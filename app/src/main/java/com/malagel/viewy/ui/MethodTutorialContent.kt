package com.malagel.viewy.ui

import androidx.annotation.StringRes
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.tutorial.TutorialPage
import com.malagel.viewy.ui.TutorialBlock.Paragraph

internal sealed interface TutorialBlock {
    data object KnownHeightIllustration : TutorialBlock
    data class BulletList(val items: List<Int>) : TutorialBlock
    data class Paragraph(@param:StringRes val text: Int) : TutorialBlock
}

internal data class TutorialSection(@param:StringRes val title: Int?, val blocks: List<TutorialBlock>)
internal data class MethodTutorialContent(
    @param:StringRes val title: Int,
    val pages: Map<TutorialPage, List<TutorialSection>>,
) {
    init { require(pages.keys == TutorialPage.entries.toSet()) }
}

@StringRes
internal fun TutorialPage.titleResource(): Int = when (this) {
    TutorialPage.INSTRUCTIONS -> R.string.tutorial_instructions
    TutorialPage.IMPORTANT -> R.string.tutorial_important
}

/** Content exists only for implemented methods; presentation is shared. */
internal val methodTutorials = mapOf(
    MeasurementMethod.KNOWN_OBJECT_HEIGHT to MethodTutorialContent(
        title = R.string.known_height_method,
        pages = mapOf(
            TutorialPage.INSTRUCTIONS to listOf(
                TutorialSection(null, listOf(Paragraph(R.string.tutorial_known_height_intro))),
                TutorialSection(null, listOf(TutorialBlock.KnownHeightIllustration)),
                TutorialSection(R.string.tutorial_steps, listOf(
                    R.string.tutorial_known_height_step_1, R.string.tutorial_known_height_step_2,
                    R.string.tutorial_known_height_step_3,
                ).map(::Paragraph)),
            ),
            TutorialPage.IMPORTANT to listOf(
                TutorialSection(R.string.tutorial_known_height_tips, listOf(
                    TutorialBlock.BulletList(listOf(
                        R.string.tutorial_known_height_tip_1,
                        R.string.tutorial_known_height_tip_2,
                        R.string.tutorial_known_height_tip_3,
                        R.string.tutorial_known_height_tip_4,
                    )),
                )),
            ),
        ),
    ),
)
