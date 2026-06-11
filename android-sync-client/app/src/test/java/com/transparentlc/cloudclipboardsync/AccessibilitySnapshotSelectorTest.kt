package com.transparentlc.cloudclipboardsync

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilitySnapshotSelectorTest {
    @Test
    fun prefersStructuredUrlOverEditablePlaceholder() {
        val result = AccessibilitySnapshotSelector.buildSnapshotText(
            listOf(
                TextCandidate(
                    text = "在 Google 中搜索或输入网址",
                    priority = 1,
                    depth = 2,
                    editable = true,
                    hint = "在 Google 中搜索或输入网址",
                ),
                TextCandidate(
                    text = "example.com/?q=codex-accessibility-url-20260610-1",
                    priority = 3,
                    depth = 4,
                ),
                TextCandidate(
                    text = "Example Domain",
                    priority = 3,
                    depth = 4,
                ),
            ),
        )

        assertEquals("example.com/?q=codex-accessibility-url-20260610-1", result)
    }

    @Test
    fun keepsRealEditableTextWhenNotPlaceholder() {
        val result = AccessibilitySnapshotSelector.buildSnapshotText(
            listOf(
                TextCandidate(
                    text = "https://example.com/copied",
                    priority = 1,
                    depth = 1,
                    editable = true,
                    hint = "在 Google 中搜索或输入网址",
                ),
                TextCandidate(
                    text = "Example Domain",
                    priority = 3,
                    depth = 2,
                ),
            ),
        )

        assertEquals("https://example.com/copied", result)
    }
}
