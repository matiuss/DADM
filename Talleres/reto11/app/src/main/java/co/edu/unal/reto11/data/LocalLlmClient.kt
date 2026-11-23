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
class LocalLlmClient(context: Context, private val modelFile: File) : AutoCloseable {

    private val cacheDir = context.applicationContext.cacheDir.path
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "litertlm") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null

    /** true cuando el modelo ya está cargado en memoria (la primera carga tarda unos segundos). */
    @Volatile
    var isLoaded = false
        private set

    /** Genera la respuesta al último mensaje de [history], emitiendo el texto por fragmentos. */
    fun streamReply(
        systemPrompt: String,
        history: List<ChatMessage>,
        temperature: Float
    ): Flow<String> = channelFlow {
        val engine = loadEngine()
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
            isLoaded = false
        }
    }

    /** Igual que [close] pero sin apagar el hilo, para poder volver a cargar el modelo después. */
    suspend fun unload() = withContext(dispatcher) {
        runCatching { engine?.close() }
        engine = null
        isLoaded = false
    }

    private fun loadEngine(): Engine {
        engine?.let { return it }
        check(modelFile.exists()) { "El modelo no está descargado. Descárgalo en Ajustes." }
        val newEngine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                // CPU: funciona en cualquier celular (y en el emulador). La GPU es más rápida pero
                // depende de OpenCL, que no todos los equipos exponen a las apps.
                backend = Backend.CPU(),
                maxNumTokens = LocalModel.CONTEXT_TOKENS,
                cacheDir = cacheDir
            )
        )
        newEngine.initialize() // puede tardar varios segundos; si falla, no se guarda el motor
        engine = newEngine
        isLoaded = true
        return newEngine
    }

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
