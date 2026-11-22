package com.livesub.translate.caption

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Pulls the caption text out of the Live Caption window
 * (package `com.google.android.as`) via the accessibility node tree.
 */
object CaptionReader {

    const val LIVE_CAPTION_PKG = "com.google.android.as"

    /** Lines that are clearly UI chrome, not transcript. */
    private val SKIP_CLASSES = listOf("Button", "Switch", "CheckBox", "ImageView", "EditText")

    /** Max lines kept from the scrollback (the transcript is trimmed further by [Transcript]). */
    private const val MAX_LINES = 80

    /** @return the joined transcript, or "" when no caption window is on screen. */
    fun read(windows: List<AccessibilityWindowInfo>): String {
        var best: List<String> = emptyList()
        var bestLen = 0
        for (w in windows) {
            val root = w.root ?: continue
            try {
                if (root.packageName?.toString() != LIVE_CAPTION_PKG) continue
                val lines = ArrayList<String>()
                collect(root, lines)
                val len = lines.sumOf { it.length }
                // Several windows can belong to the package; the transcript is the wordy one.
                if (len > bestLen) {
                    best = lines
                    bestLen = len
                }
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }
        return Transcript.join(best.takeLast(MAX_LINES))
    }

    /** Debug aid: every window of the Live Caption package and its text nodes. */
    fun dump(windows: List<AccessibilityWindowInfo>): String = buildString {
        for (w in windows) {
            val root = w.root ?: continue
            try {
                if (root.packageName?.toString() != LIVE_CAPTION_PKG) continue
                append("window type=").append(w.type).append(" title=").append(w.title)
                    .append(" layer=").append(w.layer).append('\n')
                dumpNode(root, 1, this)
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, out: StringBuilder) {
        out.append("  ".repeat(depth)).append(node.className?.toString()?.substringAfterLast('.'))
        node.viewIdResourceName?.let { out.append(" #").append(it.substringAfter(":id/")) }
        if (node.isClickable) out.append(" [click]")
        if (!node.isVisibleToUser) out.append(" [hidden]")
        node.text?.let { out.append(" text=\"").append(it.toString().replace("\n", "⏎")).append('"') }
        node.contentDescription?.let { out.append(" desc=\"").append(it).append('"') }
        out.append('\n')
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                dumpNode(child, depth + 1, out)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val cls = node.className?.toString().orEmpty()
        if (SKIP_CLASSES.none { cls.endsWith(it) }) {
            node.text?.takeIf { it.isNotBlank() }?.let { text ->
                text.split('\n').mapNotNullTo(out) { it.trim().takeIf(String::isNotEmpty) }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collect(child, out)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }
}
