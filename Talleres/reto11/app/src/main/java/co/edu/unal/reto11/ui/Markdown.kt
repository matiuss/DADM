package co.edu.unal.reto11.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Renderizador de Markdown muy pequeño para las respuestas de los modelos, que suelen venir con
 * **negritas**, *cursivas*, `código`, títulos (#), listas (- / *), bloques ``` de código y
 * fórmulas LaTeX: `$…$` o `\(…\)` dentro del texto y `$$…$$` o `\[…\]` en bloque.
 * No pretende cubrir todo Markdown, solo lo suficiente para que el chat se lea bien.
 */
object Markdown {

    /** Colores y medidas necesarias para dibujar el texto. */
    class Style(
        val context: Context,
        val codeBackground: Int,
        val textColor: Int,
        val textSize: Float,
        val maxWidth: Int
    )

    // Grupos: 1 $$display$$ · 2 \[display\] · 3 \(inline\) · 4 $inline$ · 5 **negrita** · 6 `código` · 7 *cursiva*
    // `$x$` solo cuenta como fórmula si no hay espacio junto a los $ y el cierre no va seguido de
    // un dígito, para no confundir precios como "cuesta $5 y $10".
    private val INLINE = Regex(
        """\$\$(.+?)\$\$""" +
            """|\\\[(.+?)\\\]""" +
            """|\\\((.+?)\\\)""" +
            """|(?<![\\$\w])\$(?![\s$])((?:\\.|[^$\\])+?)(?<![\s\\])\$(?![\d$])""" +
            """|\*\*(.+?)\*\*""" +
            """|`([^`]+)`""" +
            """|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])"""
    )

    private val BLOCK_ENV = Regex("""^\\begin\{(\w+\*?)\}""")
    private val MATH_ENVS = setOf(
        "equation", "equation*", "align", "align*", "aligned", "gather", "gather*", "gathered",
        "multline", "multline*", "cases", "matrix", "pmatrix", "bmatrix", "vmatrix", "array"
    )

    fun render(source: String, style: Style): CharSequence {
        val out = SpannableStringBuilder()
        var inCodeBlock = false
        // Fórmula en bloque que ocupa varias líneas: texto acumulado y cómo se cierra.
        var mathBuffer: StringBuilder? = null
        var mathClose = ""
        val lines = source.split('\n')

        fun newLine() {
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
        }

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            mathBuffer?.let { buffer ->
                if (trimmed.endsWith(mathClose)) {
                    buffer.append('\n').append(
                        if (mathClose.startsWith("\\end")) trimmed else trimmed.removeSuffix(mathClose)
                    )
                    newLine()
                    appendBlockMath(out, buffer.toString(), mathRaw(buffer, mathClose), style)
                    mathBuffer = null
                } else {
                    buffer.append('\n').append(trimmed)
                }
                if (index < lines.lastIndex && mathBuffer == null) out.append('\n')
                return@forEachIndexed
            }

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEachIndexed
            }
            val start = out.length
            when {
                inCodeBlock -> {
                    out.append(rawLine)
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(style.codeBackground), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Fórmula en bloque en una sola línea: $$ … $$ o \[ … \]
                isSingleLineBlock(trimmed) -> appendBlockMath(out, trimmed.drop(2).dropLast(2), trimmed, style)
                // Inicio de una fórmula en bloque de varias líneas.
                (trimmed.startsWith("$$") && !trimmed.drop(2).contains("$$")) ||
                    (trimmed.startsWith("\\[") && !trimmed.contains("\\]")) -> {
                    mathClose = if (trimmed.startsWith("$$")) "$$" else "\\]"
                    mathBuffer = StringBuilder(trimmed.drop(2))
                    return@forEachIndexed
                }
                BLOCK_ENV.find(trimmed)?.groupValues?.get(1) in MATH_ENVS -> {
                    val env = BLOCK_ENV.find(trimmed)!!.groupValues[1]
                    val close = "\\end{$env}"
                    if (trimmed.endsWith(close)) {
                        appendBlockMath(out, trimmed, trimmed, style)
                    } else {
                        mathClose = close
                        mathBuffer = StringBuilder(trimmed)
                        return@forEachIndexed
                    }
                }
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length
                    appendInline(out, line.drop(level).trim(), style)
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(
                        RelativeSizeSpan(if (level <= 2) 1.15f else 1.05f),
                        start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                Regex("""^\s*[-*+]\s+.*""").matches(line) -> {
                    val indent = line.takeWhile { it == ' ' }.length
                    appendInline(out, line.trimStart().drop(1).trim(), style)
                    out.setSpan(
                        BulletSpan(16 + indent * 8),
                        start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                else -> appendInline(out, line, style)
            }
            if (index < lines.lastIndex) out.append('\n')
        }
        // Fórmula en bloque sin cerrar (el modelo aún la está escribiendo): se muestra tal cual.
        mathBuffer?.let { buffer ->
            newLine()
            val start = out.length
            out.append(if (mathClose.startsWith("\\end")) buffer else "${if (mathClose == "$$") "$$" else "\\["}$buffer")
            out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Quita saltos de línea sobrantes al final.
        while (out.isNotEmpty() && out.last() == '\n') out.delete(out.length - 1, out.length)
        return out
    }

    private fun isSingleLineBlock(line: String) = line.length > 4 &&
        ((line.startsWith("$$") && line.endsWith("$$") && !line.substring(2, line.length - 2).contains("$$")) ||
            (line.startsWith("\\[") && line.endsWith("\\]")))

    private fun mathRaw(buffer: CharSequence, close: String) = when (close) {
        "$$" -> "$$$buffer$$"
        "\\]" -> "\\[$buffer\\]"
        else -> buffer.toString()
    }

    /** Fórmula centrada en su propia línea; si no se puede dibujar, se deja el texto original. */
    private fun appendBlockMath(out: SpannableStringBuilder, latex: String, raw: String, style: Style) {
        val start = out.length
        val span = MathRenderer.span(style.context, latex, true, style.textSize, style.textColor, style.maxWidth)
        if (span == null) {
            out.append(raw)
            out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }
        out.append('￼') // carácter de reemplazo de objeto: lo dibuja el span
        out.setSpan(span, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun appendInline(out: SpannableStringBuilder, text: String, style: Style) {
        var cursor = 0
        for (match in INLINE.findAll(text)) {
            out.append(text, cursor, match.range.first)
            val start = out.length
            val g = match.groupValues
            val displayMath = g[1].ifEmpty { g[2] }
            val inlineMath = g[3].ifEmpty { g[4] }
            when {
                displayMath.isNotEmpty() || inlineMath.isNotEmpty() -> {
                    val display = displayMath.isNotEmpty()
                    val span = MathRenderer.span(
                        style.context, if (display) displayMath else inlineMath, display,
                        style.textSize, style.textColor, style.maxWidth
                    )
                    if (span != null) {
                        out.append('￼')
                        out.setSpan(span, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        out.append(match.value)
                    }
                }
                g[5].isNotEmpty() -> {
                    appendInline(out, g[5], style)
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                g[6].isNotEmpty() -> {
                    out.append(g[6])
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(style.codeBackground), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    appendInline(out, g[7], style)
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            cursor = match.range.last + 1
        }
        out.append(text, cursor, text.length)
    }
}
