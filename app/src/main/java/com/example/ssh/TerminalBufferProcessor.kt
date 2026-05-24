package com.example.ssh

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object TerminalBufferProcessor {

    /**
     * Cleans an incoming SSH raw text chunk, handles backspaces/carriage returns/clear screen triggers,
     * strips unnecessary VT100 control sequences, and keeps styling escape sequences in place
     * for visual processing.
     */
    fun processChunk(currentBuffer: String, chunk: String): String {
        val sb = StringBuilder()
        sb.append(currentBuffer)

        var i = 0
        val len = chunk.length

        while (i < len) {
            val c = chunk[i]
            when (c) {
                '\u0008', '\u007f' -> { // Backspace or Delete
                    if (sb.isNotEmpty() && sb.last() != '\n') {
                        sb.deleteAt(sb.length - 1)
                    }
                    i++
                }
                '\r' -> { // Carriage Return
                    if (i + 1 < len && chunk[i + 1] == '\n') {
                        // Let the '\n' at next index handle the line break
                        i++
                    } else {
                        // Standalone carriage return wipes the active line
                        val lastNewLine = sb.lastIndexOf('\n')
                        if (lastNewLine != -1) {
                            sb.setLength(lastNewLine + 1)
                        } else {
                            sb.setLength(0)
                        }
                        i++
                    }
                }
                '\u001b' -> { // Escape Sequence
                    if (i + 1 < len) {
                        val next = chunk[i + 1]
                        if (next == '[') {
                            // CSI (Control Sequence Introducer)
                            var j = i + 2
                            var foundEnd = false
                            while (j < len) {
                                val tc = chunk[j]
                                if (tc in 'a'..'z' || tc in 'A'..'Z' || tc == '@' || tc == '`') {
                                    val code = chunk.substring(i, j + 1)
                                    foundEnd = true
                                    
                                    // Handle specific terminal control triggers
                                    when (tc) {
                                        'J' -> { // Clear screen sequence (e.g., [2J or [J)
                                            sb.setLength(0)
                                        }
                                        'K' -> { // Erase line sequence (e.g., [K or [2K)
                                            val lastNewLine = sb.lastIndexOf('\n')
                                            if (lastNewLine != -1) {
                                                sb.setLength(lastNewLine + 1)
                                            } else {
                                                sb.setLength(0)
                                            }
                                        }
                                        'm' -> { // SGR (Select Graphic Rendition) - KEEP for colors!
                                            sb.append(code)
                                        }
                                        // Other sequences (A, B, C, D, H, f, h, l, etc.) are stripped
                                    }
                                    i = j + 1
                                    break
                                }
                                j++
                            }
                            if (!foundEnd) {
                                i = len // Drop split raw escape prefix
                            }
                        } else if (next == ']') {
                            // OSC (Operating System Command)
                            var j = i + 2
                            var foundEnd = false
                            while (j < len) {
                                val tc = chunk[j]
                                if (tc == '\u0007') {
                                    foundEnd = true
                                    i = j + 1
                                    break
                                }
                                j++
                            }
                            if (!foundEnd) {
                                i = len
                            }
                        } else {
                            // Simple Esc+char sequence (e.g. Esc=, Esc>) -> strip
                            i += 2
                        }
                    } else {
                        i++
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }

        return sb.toString()
    }

    /**
     * Parses a text line containing ANSI SGR graphic rendition sequences (ESC[...m)
     * and compiles it into a beautiful Jetpack Compose AnnotatedString.
     */
    fun parseAnsiToAnnotatedString(text: String, defaultColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = text.length
            
            // Maintain styling state
            var currentColor = defaultColor
            var currentWeight = FontWeight.Normal
            var textDecoration = TextDecoration.None

            while (i < len) {
                val escIndex = text.indexOf("\u001b[", i)
                if (escIndex == -1) {
                    // No more styling, append the rest of the text
                    val remainingText = text.substring(i)
                    append(remainingText)
                    
                    // Apply style to the appended segment
                    val spanStart = length - remainingText.length
                    addStyle(
                        SpanStyle(
                            color = currentColor,
                            fontWeight = currentWeight,
                            textDecoration = textDecoration
                        ),
                        spanStart,
                        length
                    )
                    break
                }

                // Append text before the Escape sequence
                if (escIndex > i) {
                    val plainText = text.substring(i, escIndex)
                    append(plainText)
                    
                    val spanStart = length - plainText.length
                    addStyle(
                        SpanStyle(
                            color = currentColor,
                            fontWeight = currentWeight,
                            textDecoration = textDecoration
                        ),
                        spanStart,
                        length
                    )
                }

                // Find the ending 'm' character of this SGR sequence
                val mIndex = text.indexOf('m', escIndex + 2)
                if (mIndex != -1) {
                    val paramsStr = text.substring(escIndex + 2, mIndex)
                    val params = paramsStr.split(';').mapNotNull { it.toIntOrNull() }
                    
                    if (params.isEmpty() || params.contains(0)) {
                        // Reset defaults
                        currentColor = defaultColor
                        currentWeight = FontWeight.Normal
                        textDecoration = TextDecoration.None
                    } else {
                        for (param in params) {
                            when (param) {
                                1 -> currentWeight = FontWeight.Bold
                                4 -> textDecoration = TextDecoration.Underline
                                22 -> currentWeight = FontWeight.Normal // Normal color/intensity
                                24 -> textDecoration = TextDecoration.None // Underline off
                                
                                // Standard Foreground color codes
                                30 -> currentColor = Color(0xFF2E3440) // Black (Nord styles)
                                31 -> currentColor = Color(0xFFBF616A) // Red
                                32 -> currentColor = Color(0xFFA3BE8C) // Green
                                33 -> currentColor = Color(0xFFEBCB8B) // Yellow
                                34 -> currentColor = Color(0xFF81A1C1) // Blue
                                35 -> currentColor = Color(0xFFB48EAD) // Magenta
                                36 -> currentColor = Color(0xFF88C0D0) // Cyan
                                37 -> currentColor = Color(0xFFECEFF4) // White
                                
                                // Bright colors
                                90 -> currentColor = Color(0xFF4C566A) // Bright Black (Gray)
                                91 -> currentColor = Color(0xFFD08770) // Bright Red
                                92 -> currentColor = Color(0xFFA3BE8C) // Bright Green (same or lighter)
                                93 -> currentColor = Color(0xFFEBCB8B) // Bright Yellow
                                94 -> currentColor = Color(0xFF5E81AC) // Bright Blue
                                95 -> currentColor = Color(0xFFB48EAD) // Bright Magenta
                                96 -> currentColor = Color(0xFF8FBCBB) // Bright Cyan
                                97 -> currentColor = Color(0xFFE5E9F0) // Bright White
                                
                                // Background colors or secondary configurations are skipped for high readability
                            }
                        }
                    }
                    i = mIndex + 1
                } else {
                    // Unclosed escape, skip ESC prefix
                    i = escIndex + 2
                }
            }
        }
    }
}
