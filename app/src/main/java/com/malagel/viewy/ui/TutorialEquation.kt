package com.malagel.viewy.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Native, offline equations. Spoken resources replace fragmented symbol semantics. */
@Composable
internal fun TutorialEquation(@StringRes notation: Int, @StringRes spoken: Int) {
    val source = stringResource(notation)
    val description = stringResource(spoken)
    val expression = remember(source) { parseTutorialMath(source) }
    // Preserve font scaling; unusually wide equations scroll rather than becoming tiny or clipped.
    Box(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = MaterialTheme.typography.bodyLarge
        if (expression == null) Text(source, style = style)
        else MathExpression(expression, style)
    }
}

@Composable
private fun MathExpression(expression: TutorialMath, style: TextStyle) {
    when (expression) {
        is TutorialMath.Fraction -> Column(
            Modifier.width(IntrinsicSize.Max).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MathExpression(expression.numerator, style)
            HorizontalDivider(Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.onSurface)
            MathExpression(expression.denominator, style)
        }
        is TutorialMath.Group -> {
            if (expression.containsFraction()) Row(verticalAlignment = Alignment.CenterVertically) {
                expression.children.forEach { MathExpression(it, style) }
            } else Text(expression.annotated(style), style = style, softWrap = false)
        }
        else -> Text(expression.annotated(style), style = style, softWrap = false)
    }
}

private fun TutorialMath.containsFraction(): Boolean = when (this) {
    is TutorialMath.Fraction -> true
    is TutorialMath.Group -> children.any { it.containsFraction() }
    else -> false
}

private fun TutorialMath.annotated(style: TextStyle): AnnotatedString = buildAnnotatedString {
    fun appendExpression(expression: TutorialMath) {
        when (expression) {
            is TutorialMath.Symbols -> append(expression.text)
            is TutorialMath.Group -> expression.children.forEach(::appendExpression)
            is TutorialMath.Script -> {
                appendExpression(expression.base)
                withStyle(SpanStyle(
                    baselineShift = if (expression.superscript) BaselineShift.Superscript else BaselineShift.Subscript,
                    fontSize = style.fontSize * 0.75f,
                )) { append(expression.text) }
            }
            is TutorialMath.Fraction -> error("Fractions use the equation layout")
        }
    }
    appendExpression(this@annotated)
}
