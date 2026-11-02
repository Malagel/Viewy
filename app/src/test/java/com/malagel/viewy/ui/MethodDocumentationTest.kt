package com.malagel.viewy.ui

import com.malagel.viewy.domain.MeasurementMethod
import org.junit.Assert.*
import org.junit.Test

class MethodDocumentationTest {
    @Test fun coverageIncludesFirstEntranceFrameExitAndReversedEntrance() {
        assertFalse(documentationCoversTutorial(false, false, false))
        assertTrue(documentationCoversTutorial(false, true, false))
        assertTrue(documentationCoversTutorial(false, true, true))
        assertTrue(documentationCoversTutorial(true, true, false))
        assertTrue(documentationCoversTutorial(true, false, false))
        assertTrue(documentationCoversTutorial(true, false, true))
        // Back may reverse before the opening transition reaches its first settled state.
        assertTrue(documentationCoversTutorial(false, false, true))
    }

    @Test fun everyTutorialHasDocumentationSelectedByMethodIdentity() {
        assertEquals(methodTutorials.keys, MeasurementMethod.entries.filter { methodDocumentation(it) != null }.toSet())
        methodTutorials.forEach { (method, tutorial) ->
            val content = methodDocumentation(method)!!
            assertEquals(tutorial.title, content.title)
            assertTrue(content.blocks.first() is DocumentationBlock.Heading)
            assertTrue(content.blocks.filterIsInstance<DocumentationBlock.Paragraph>().size >= 2)
            // The initial entry deliberately contains no invented formula or demonstration asset.
            assertTrue(content.blocks.none { it is DocumentationBlock.Image })
        }
    }
}
