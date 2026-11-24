package co.edu.unal.reto11.data

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors

/**
 * Ejecuta el modelo de lenguaje en el propio celular con LiteRT-LM (Google AI Edge).
 *
 * - El motor ([Engine]) se carga una vez (tarda unos segundos) y se reutiliza.
 * - Cada respuesta usa una [com.google.ai.edge.litertlm.Conversation] nueva con el historial
 *   como `initialMessages`, así funcionan igual "Reintentar", "Borrar" y el cambio de personalidad.
 * - Todo el trabajo con el motor pasa por un único hilo, como recomienda la documentación.
 */
class LocalLlmClient(context: Context) : AutoCloseable {

    private val cacheDir = context.applicationContext.cacheDir.path
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "litertlm") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null

    /** Modelo cargado en memoria (la carga tarda unos segundos), o null si no hay ninguno. */
    @Volatile
    var loadedModel: LocalModel? = null
        private set

    /**
     * Genera con [model] (guardado en [modelFile]) la respuesta al último mensaje de [history],
     * emitiendo el texto por fragmentos. Si había otro modelo cargado, se libera primero.
     */
    fun streamReply(
        model: LocalModel,
        modelFile: File,
        systemPrompt: String,
        history: List<ChatMessage>,
        temperature: Float
    ): Flow<String> = channelFlow {
        val engine = loadEngine(model, modelFile)
        val prompt = history.last()
        val previous = fitInContext(history.dropLast(1), budget = HISTORY_CHAR_BUDGET - prompt.text.length)

        val conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = previous.map {
                    if (it.role == Role.USER) Message.user(it.text) else Message.model(it.text)
                },
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = temperature.toDouble().coerceAtLeast(0.05),
                    seed = 0
                ),
                // Qwen3 "piensa" en voz alta antes de responder; en un chat solo se quiere la respuesta.
                thinkingConfig = ThinkingConfig(enableThinking = false)
            )
        )
        val done = CompletableDeferred<Unit>()
        var producedText = false
        try {
            conversation.sendMessageAsync(prompt.text, object : MessageCallback {
                override fun onMessage(message: Message) {
                    val chunk = textOf(message)
                    if (chunk.isNotEmpty()) {
                        producedText = true
                        trySend(chunk)
                    }
                }

                override fun onDone() {
                    done.complete(Unit)
                }

                override fun onError(throwable: Throwable) {
                    done.completeExceptionally(throwable)
                }
            })
            done.await()
            if (!producedText) throw IllegalStateException("El modelo no devolvió texto. Intenta de nuevo.")
        } finally {
            // "Detener": se pide al motor que pare y se espera a que lo confirme antes de cerrar,
            // porque cerrar una conversación que sigue generando puede tumbar la app.
            if (!done.isCompleted) {
                conversation.cancelProcess()
                withContext(NonCancellable) {
                    withTimeoutOrNull(CANCEL_TIMEOUT_MS) { runCatching { done.await() } }
                }
            }
            conversation.close()
        }
    }.buffer(Channel.UNLIMITED).flowOn(dispatcher)

    /** Libera el modelo de la memoria (p. ej. antes de borrar el archivo o al cerrar la app). */
    override fun close() {
        executor.execute {
            runCatching { engine?.close() }
            engine = null
            loadedModel = null
            backendName = null
        }
    }

    /** Igual que [close] pero sin apagar el hilo, para poder volver a cargar el modelo después. */
    suspend fun unload() = withContext(dispatcher) {
        runCatching { engine?.close() }
        engine = null
        loadedModel = null
        backendName = null
    }

    /** Dónde se está ejecutando el modelo ("GPU" o "CPU"), o null si aún no se ha cargado. */
    @Volatile
    var backendName: String? = null
        private set

    private fun loadEngine(model: LocalModel, modelFile: File): Engine {
        engine?.let { if (loadedModel == model) return it }
        // Otro modelo en memoria: se libera antes de cargar el nuevo (no caben dos a la vez).
        runCatching { engine?.close() }
        engine = null
        loadedModel = null
        backendName = null
        check(modelFile.exists()) { "El modelo no está descargado. Descárgalo en Ajustes." }
        // La GPU es varias veces más rápida, pero depende de OpenCL, que no todos los equipos (ni el
        // emulador) exponen a las apps. Si no se puede, se usa la CPU.
        val gpu = runCatching { createEngine(modelFile, model.contextTokens, Backend.GPU()) }.getOrNull()
        val newEngine = gpu ?: createEngine(modelFile, model.contextTokens, Backend.CPU(threadCount = cpuThreads()))
        backendName = if (gpu != null) "GPU" else "CPU"
        engine = newEngine
        loadedModel = model
        return newEngine
    }

    /** Crea e inicializa el motor (puede tardar varios segundos); si falla, lo libera y relanza. */
    private fun createEngine(modelFile: File, contextTokens: Int, backend: Backend): Engine {
        val newEngine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                maxNumTokens = contextTokens,
                cacheDir = cacheDir
            )
        )
        try {
            newEngine.initialize()
        } catch (e: Throwable) {
            runCatching { newEngine.close() }
            throw e
        }
        return newEngine
    }

    /**
     * Hilos para la CPU: los celulares mezclan núcleos rápidos y lentos; usar todos hace que los
     * rápidos esperen a los lentos. 4 suele coincidir con los núcleos de alto rendimiento.
     */
    private fun cpuThreads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    /**
     * El modelo pequeño tiene un contexto limitado: se conservan solo los mensajes más recientes
     * que caben en el presupuesto (aprox. 3 caracteres por token, dejando espacio a la respuesta).
     */
    private fun fitInContext(messages: List<ChatMessage>, budget: Int): List<ChatMessage> {
        var remaining = budget
        val kept = ArrayDeque<ChatMessage>()
        for (message in messages.asReversed()) {
            remaining -= message.text.length
            if (remaining < 0) break
            kept.addFirst(message)
        }
        // El historial debe empezar con un mensaje del usuario.
        while (kept.firstOrNull()?.role == Role.MODEL) kept.removeFirst()
        return kept.toList()
    }

    private fun textOf(message: Message): String =
        message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private companion object {
        /** ~2000 tokens de historial; el resto del contexto queda para la instrucción y la respuesta. */
        const val HISTORY_CHAR_BUDGET = 6_000
        const val CANCEL_TIMEOUT_MS = 5_000L
    }
}
