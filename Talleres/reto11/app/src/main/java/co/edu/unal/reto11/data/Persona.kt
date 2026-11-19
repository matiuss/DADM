package co.edu.unal.reto11.data

/**
 * Personalidad del chatbot. Cada una define un *prompt de sistema* (systemInstruction) que se
 * envía a Gemini en cada petición, y unas sugerencias de preguntas para empezar la conversación.
 */
enum class Persona(
    val title: String,
    val emoji: String,
    val greeting: String,
    val systemPrompt: String,
    val suggestions: List<String>
) {
    ASSISTANT(
        title = "Asistente general",
        emoji = "🤖",
        greeting = "¡Hola! Soy tu asistente. Pregúntame lo que quieras.",
        systemPrompt = "Eres un asistente útil y amable. Responde siempre en español, de forma " +
            "clara y concisa. Usa listas cuando ayuden a la lectura.",
        suggestions = listOf(
            "¿Qué es la inteligencia artificial generativa?",
            "Dame 3 ideas para un fin de semana en Bogotá",
            "Explícame la fotosíntesis en 5 líneas"
        )
    ),
    TUTOR(
        title = "Tutor de programación",
        emoji = "👨‍💻",
        greeting = "Soy tu tutor de programación. ¿Qué quieres aprender hoy?",
        systemPrompt = "Eres un tutor de programación paciente, experto en Kotlin y desarrollo " +
            "Android. Responde en español. Explica paso a paso, incluye ejemplos de código cortos " +
            "y termina con una pregunta para comprobar que el estudiante entendió.",
        suggestions = listOf(
            "¿Qué es una corrutina en Kotlin?",
            "Diferencia entre val y var",
            "¿Para qué sirve un ViewModel en Android?"
        )
    ),
    TRANSLATOR(
        title = "Traductor",
        emoji = "🌎",
        greeting = "Escribe un texto y lo traduzco entre español e inglés.",
        systemPrompt = "Eres un traductor profesional. Si el usuario escribe en español, traduce " +
            "al inglés; si escribe en otro idioma, traduce al español. Devuelve solo la traducción " +
            "y, si hay modismos, añade una nota breve al final.",
        suggestions = listOf(
            "Mañana tengo examen de móviles y estoy nervioso",
            "It's raining cats and dogs",
            "¿Me puedes recomendar un buen restaurante?"
        )
    ),
    STORYTELLER(
        title = "Cuentacuentos",
        emoji = "📖",
        greeting = "Dame un tema o unos personajes y te escribo una historia.",
        systemPrompt = "Eres un escritor creativo de cuentos cortos en español. Escribe historias " +
            "originales de máximo 250 palabras, con título, inicio, nudo y desenlace. Si el " +
            "usuario pide cambios, reescribe la historia manteniendo el estilo.",
        suggestions = listOf(
            "Un robot que quiere aprender a cocinar ajiaco",
            "Una estudiante que descubre un portal en la biblioteca",
            "Un gato detective en La Candelaria"
        )
    )
}
