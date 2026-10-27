package com.malagel.viewy.ui

import org.junit.Assert.*
import org.junit.Test

class TutorialMathNotationTest {
    @Test fun distanceEquationSeparatesFractionAndPreservesEndpointSubscripts() {
        val equation = parseTutorialMath("""d = \frac{H}{\tan(\theta_{top}) − \tan(\theta_{bottom})}""")!!
        val fraction = equation.children.filterIsInstance<TutorialMath.Fraction>().single()
        assertEquals("H", fraction.numerator.plain())
        assertEquals("tan(θ_top) − tan(θ_bottom)", fraction.denominator.plain())
        val scripts = fraction.denominator.children.filterIsInstance<TutorialMath.Script>()
        assertEquals(listOf("top", "bottom"), scripts.map { it.text })
        assertTrue(scripts.all { it.base == TutorialMath.Symbols("θ") && !it.superscript })
        assertEquals("d = ", TutorialMath.Group(equation.children.take(4)).plain())
    }

    @Test fun derivationSupportsMultiplicationAndMatchesDistanceFormulaLabels() {
        val equation = parseTutorialMath("""H = d \cdot (\tan(\theta_{top}) − \tan(\theta_{bottom}))""")!!
        assertEquals("H = d · (tan(θ_top) − tan(θ_bottom))", equation.plain())
        assertTrue(equation.children.none { it is TutorialMath.Fraction })
    }

    @Test fun scriptArgumentsCanBeSingleCharactersOrBracedLabels() {
        val equation = parseTutorialMath("x^2 + y^{2} + z_{bottom}")!!
        val scripts = equation.children.filterIsInstance<TutorialMath.Script>()
        assertEquals(listOf("2", "2", "bottom"), scripts.map { it.text })
        assertEquals(listOf(true, true, false), scripts.map { it.superscript })
    }

    @Test fun nestedFractionsKeepNumeratorAndDenominatorGrouping() {
        val equation = parseTutorialMath("""\frac{a}{\frac{b}{c}}""")!!
        val fraction = equation.children.single() as TutorialMath.Fraction
        val nested = fraction.denominator.children.single() as TutorialMath.Fraction
        assertEquals("a", fraction.numerator.plain())
        assertEquals("b", nested.numerator.plain())
        assertEquals("c", nested.denominator.plain())
    }

    @Test fun incompleteOrUnsupportedNotationCanFallBackWithoutCrashing() {
        listOf("", "}", "{x", "{}", "_top", "x_", "x^{}", """\frac{H}""", """\frac{H}{d""", """\unknown{x}""")
            .forEach { assertNull(it, parseTutorialMath(it)) }
    }

    private fun TutorialMath.plain(): String = when (this) {
        is TutorialMath.Symbols -> text
        is TutorialMath.Group -> children.joinToString("") { it.plain() }
        is TutorialMath.Script -> base.plain() + (if (superscript) "^" else "_") + text
        is TutorialMath.Fraction -> "(${numerator.plain()})/(${denominator.plain()})"
    }
}
