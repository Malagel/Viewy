package com.malagel.viewy.ui

import androidx.annotation.StringRes
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.ui.TutorialBlock.Equation
import com.malagel.viewy.ui.TutorialBlock.Paragraph

internal sealed interface TutorialBlock {
    data object KnownHeightIllustration : TutorialBlock
    data class Paragraph(@param:StringRes val text: Int) : TutorialBlock
    data class Equation(@param:StringRes val notation: Int, @param:StringRes val spoken: Int) : TutorialBlock
}

internal data class TutorialSection(@param:StringRes val title: Int?, val blocks: List<TutorialBlock>)
internal data class MethodTutorialContent(
    @param:StringRes val title: Int,
    val basic: List<TutorialSection>,
    val details: List<TutorialSection>,
)

/** Content exists only for implemented methods; presentation is shared. */
internal val methodTutorials = mapOf(
    MeasurementMethod.KNOWN_OBJECT_HEIGHT to MethodTutorialContent(
        title = R.string.known_height_method,
        basic = listOf(
            TutorialSection(null, listOf(Paragraph(R.string.tutorial_known_height_intro))),
            TutorialSection(null, listOf(TutorialBlock.KnownHeightIllustration)),
            TutorialSection(R.string.tutorial_steps, listOf(
                R.string.tutorial_known_height_step_1, R.string.tutorial_known_height_step_2,
                R.string.tutorial_known_height_step_3,
            ).map(::Paragraph)),
        ),
        details = listOf(
            TutorialSection(R.string.tutorial_geometry, listOf(
                Paragraph(R.string.tutorial_known_height_geometry),
                Equation(R.string.tutorial_known_height_height_equation, R.string.tutorial_known_height_height_equation_spoken),
                Paragraph(R.string.tutorial_known_height_solve),
                Equation(R.string.tutorial_known_height_equation, R.string.tutorial_known_height_equation_spoken),
                Paragraph(R.string.tutorial_known_height_variables), Paragraph(R.string.tutorial_known_height_horizontal),
            )),
            TutorialSection(R.string.tutorial_directions, listOf(
                R.string.tutorial_known_height_directions, R.string.tutorial_known_height_alignment,
                R.string.tutorial_known_height_sessions,
            ).map(::Paragraph)),
            TutorialSection(R.string.tutorial_sensitivity, listOf(Paragraph(R.string.tutorial_known_height_sensitivity))),
            TutorialSection(R.string.tutorial_camera_timing, listOf(
                R.string.tutorial_known_height_camera, R.string.tutorial_known_height_timing,
            ).map(::Paragraph)),
            TutorialSection(R.string.tutorial_steadiness, listOf(Paragraph(R.string.tutorial_known_height_steadiness))),
        ),
    ),
)
