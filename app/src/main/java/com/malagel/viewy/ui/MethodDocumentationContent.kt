package com.malagel.viewy.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod

/** Resource references only: localized text and offline, pre-rendered assets. */
internal sealed interface DocumentationBlock {
    data class Heading(@param:StringRes val text: Int, val subheading: Boolean = false) : DocumentationBlock
    data class Paragraph(@param:StringRes val text: Int) : DocumentationBlock
    data class BulletList(val items: List<Int>) : DocumentationBlock
    data class Image(
        @param:DrawableRes val drawable: Int,
        @param:StringRes val description: Int,
        @param:StringRes val caption: Int? = null,
        @param:DrawableRes val darkDrawable: Int = drawable,
        // For transparent, single-color equations: use the theme's readable foreground.
        val monochrome: Boolean = false,
    ) : DocumentationBlock
}

internal data class MethodDocumentationContent(
    @param:StringRes val title: Int,
    val blocks: List<DocumentationBlock>,
)

/** Called only while documentation is mounted; identity is independent of display language. */
internal fun methodDocumentation(method: MeasurementMethod): MethodDocumentationContent? = when (method) {
    MeasurementMethod.KNOWN_OBJECT_HEIGHT -> MethodDocumentationContent(
        R.string.known_height_method,
        listOf(
            DocumentationBlock.Heading(R.string.documentation_about),
            DocumentationBlock.Paragraph(R.string.documentation_known_height_intro),
            DocumentationBlock.Heading(R.string.documentation_details),
            DocumentationBlock.Paragraph(R.string.documentation_known_height_details),
            DocumentationBlock.Heading(R.string.documentation_limits),
            DocumentationBlock.Paragraph(R.string.documentation_known_height_limits),
        ),
    )
}
