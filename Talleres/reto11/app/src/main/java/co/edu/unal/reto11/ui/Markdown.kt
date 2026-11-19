package co.edu.unal.reto11.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Renderizador de Markdown muy pequeño para las respuestas de Gemini, que suelen venir con
 * **negritas**, *cursivas*, `código`, títulos (#), listas (- / *) y bloques ``` de código.
 * No pretende cubrir todo Markdown, solo lo suficiente para que el chat se lea bien.
 */
object Markdown {

    private val INLINE = Regex("""\*\*(.+?)\*\*|`([^`]+)`|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""")

    fun render(source: String, codeBackground: Int): CharSequence {
        val out = SpannableStringBuilder()
        var inCodeBlock = false
        val lines = source.split('\n')

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEachIndexed
            }
            val start = out.length
            when {
                inCodeBlock -> {
                    out.append(rawLine)
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(codeBackground), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length
                    appendInline(out, line.drop(level).trim(), codeBackground)
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(
                        RelativeSizeSpan(if (level <= 2) 1.15f else 1.05f),
                        start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                Regex("""^\s*[-*+]\s+.*""").matches(line) -> {
                    val indent = line.takeWhile { it == ' ' }.length
                    appendInline(out, line.trimStart().drop(1).trim(), codeBackground)
                    out.setSpan(
                        BulletSpan(16 + indent * 8),
                        start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                else -> appendInline(out, line, codeBackground)
            }
            if (index < lines.lastIndex) out.append('\n')
        }
        // Quita saltos de línea sobrantes al final.
        while (out.isNotEmpty() && out.last() == '\n') out.delete(out.length - 1, out.length)
        return out
    }

    private fun appendInline(out: SpannableStringBuilder, text: String, codeBackground: Int) {
        var cursor = 0
        for (match in INLINE.findAll(text)) {
            out.append(text, cursor, match.range.first)
            val start = out.length
            val (bold, code, italic) = match.destructured
            when {
                bold.isNotEmpty() -> {
                    out.append(bold)
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                code.isNotEmpty() -> {
                    out.append(code)
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(codeBackground), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    out.append(italic)
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            cursor = match.range.last + 1
        }
        out.append(text, cursor, text.length)
    }
}
