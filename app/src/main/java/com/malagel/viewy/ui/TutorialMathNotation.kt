package com.malagel.viewy.ui

/** Small TeX-style display subset for authored tutorials, not a general TeX engine. */
internal sealed interface TutorialMath {
    data class Symbols(val text: String) : TutorialMath
    data class Group(val children: List<TutorialMath>) : TutorialMath
    data class Fraction(val numerator: Group, val denominator: Group) : TutorialMath
    data class Script(val base: TutorialMath, val text: String, val superscript: Boolean) : TutorialMath
}

/** Invalid or unsupported notation falls back to readable source instead of failing the dialog. */
internal fun parseTutorialMath(source: String): TutorialMath.Group? = try {
    TutorialMathParser(source).parse()
} catch (_: IllegalArgumentException) {
    null
}

private class TutorialMathParser(private val source: String) {
    private var position = 0

    fun parse(): TutorialMath.Group = group(braced = false)

    private fun group(braced: Boolean): TutorialMath.Group {
        val children = mutableListOf<TutorialMath>()
        while (position < source.length) {
            when (val character = source[position++]) {
                '}' -> {
                    require(braced)
                    require(children.isNotEmpty())
                    return TutorialMath.Group(children)
                }
                '{' -> children += group(braced = true)
                '\\' -> children += command()
                '_', '^' -> {
                    require(children.isNotEmpty())
                    val base = children.removeAt(children.lastIndex)
                    // Scripts are inline labels, such as theta_{top} and x^2.
                    require(!base.inlineText().isNullOrBlank())
                    val label = argument().inlineText()
                    require(!label.isNullOrBlank())
                    children += TutorialMath.Script(base, label, superscript = character == '^')
                }
                else -> children += TutorialMath.Symbols(character.toString())
            }
        }
        require(!braced && children.isNotEmpty())
        return TutorialMath.Group(children)
    }

    private fun command(): TutorialMath {
        val start = position
        while (position < source.length && source[position].isLetter()) position++
        return when (source.substring(start, position)) {
            "frac" -> TutorialMath.Fraction(bracedArgument(), bracedArgument())
            "theta" -> TutorialMath.Symbols("θ")
            "tan" -> TutorialMath.Symbols("tan")
            "cdot" -> TutorialMath.Symbols("·")
            else -> throw IllegalArgumentException("Unsupported math command")
        }
    }

    private fun argument(): TutorialMath {
        require(position < source.length)
        return when (val character = source[position++]) {
            '{' -> group(braced = true)
            '\\' -> command()
            '}', '_', '^' -> throw IllegalArgumentException("Missing script argument")
            else -> TutorialMath.Symbols(character.toString())
        }
    }

    private fun bracedArgument(): TutorialMath.Group {
        require(position < source.length && source[position++] == '{')
        return group(braced = true)
    }
}

private fun TutorialMath.inlineText(): String? {
    return when (this) {
        is TutorialMath.Symbols -> text
        is TutorialMath.Group -> children.map { it.inlineText() ?: return null }.joinToString("")
        is TutorialMath.Fraction, is TutorialMath.Script -> null
    }
}
