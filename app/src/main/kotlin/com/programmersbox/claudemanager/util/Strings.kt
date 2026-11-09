package com.programmersbox.claudemanager.util

/** First non-blank line, trimmed and capped; for one-line labels of multi-line text. */
fun String.firstLine(max: Int = 120): String = lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(max)
