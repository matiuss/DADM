package co.edu.unal.reto11.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import android.util.LruCache
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Convierte fórmulas LaTeX (integrales, fracciones, potencias, raíces, matrices…) en dibujos con
 * JLatexMath para insertarlos dentro del texto del chat.
 *
 * Mientras el modelo escribe, la burbuja se vuelve a dibujar con cada fragmento, así que las
 * fórmulas ya procesadas se guardan en caché para no analizarlas de nuevo.
 */
object MathRenderer {

    private val FAILED = Any()
    private val cache = LruCache<String, Any>(256)
    @Volatile private var initialized = false

    /**
     * Devuelve un span que dibuja [latex], o null si JLatexMath no la entiende (en ese caso se
     * muestra el texto original).
     * @param display true para fórmulas en bloque (fracciones grandes, límites debajo de ∑ e ∫).
     */
    fun span(
        context: Context,
        latex: String,
        display: Boolean,
        textSize: Float,
        color: Int,
        maxWidth: Int
    ): ReplacementSpan? {
        if (!initialized) {
            JLatexMathAndroid.init(context.applicationContext)
            initialized = true
        }
        val source = clean(latex)
        if (source.isBlank()) return null
        val size = if (display) textSize * 1.1f else textSize
        val key = "$display|$size|$color|$source"
        val cached = cache.get(key) ?: try {
            JLatexMathDrawable.builder(if (display) source else "\\textstyle $source")
                .textSize(size)
                .color(color)
                .padding(0, 2, 0, 2)
                .build()
        } catch (e: Throwable) {
            FAILED // ParseException u otros errores internos de JLatexMath
        }.also { cache.put(key, it) }
        return (cached as? JLatexMathDrawable)?.let { MathSpan(it, maxWidth) }
    }

    /** Ajusta detalles que los modelos suelen escribir y que JLatexMath no conoce. */
    private fun clean(latex: String): String = latex.trim()
        .replace(Regex("""\\tag\*?\{[^\}]*\}"""), "")
        .replace(Regex("""\\(begin|end)\{equation\*?\}"""), "")
        .replace(Regex("""\\(begin|end)\{(align|gather|multline)\*\}""")) {
            "\\${it.groupValues[1]}{${it.groupValues[2]}}"
        }
        .replace("\\boxed{", "\\fbox{")
        .replace(Regex("""\\(?:mathrm|operatorname)\{d\}"""), "d")
}

/**
 * Span que dibuja una fórmula alineando su línea base con la del texto. Si la fórmula es más
 * ancha que la burbuja, se reduce para que quepa.
 */
private class MathSpan(
    private val drawable: JLatexMathDrawable,
    maxWidth: Int
) : ReplacementSpan() {

    private val scale = drawable.intrinsicWidth.let { if (it > maxWidth) maxWidth.toFloat() / it else 1f }
    private val width = ceil(drawable.intrinsicWidth * scale).toInt()
    private val depth = drawable.icon().iconDepth * scale
    private val ascent = drawable.intrinsicHeight * scale - depth

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val font = paint.fontMetricsInt
            fm.ascent = min(font.ascent, -ceil(ascent).toInt())
            fm.descent = max(font.descent, ceil(depth).toInt())
            fm.top = min(font.top, fm.ascent)
            fm.bottom = max(font.bottom, fm.descent)
        }
        return width
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        canvas.save()
        canvas.translate(x, y - ascent)
        canvas.scale(scale, scale)
        drawable.draw(canvas)
        canvas.restore()
    }
}
