package com.example.ssh

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object TerminalBufferProcessor {

    private val states = java.util.concurrent.ConcurrentHashMap<String, TerminalState>()

    class TerminalState {
        var charSetG0: Char = 'B'       // 'B' = US ASCII, '0' = Special Graphics
        var charSetG1: Char = 'B'       // 'B' = US ASCII, '0' = Special Graphics
        var activeCharSet: Int = 0      // 0 for G0, 1 for G1
        var pendingCarriageReturn: Boolean = false
    }

    /**
     * Clears cached state data for a tab upon disconnection or closure.
     */
    fun clearState(tabId: String) {
        states.remove(tabId)
    }

    /**
     * Cleans an incoming SSH raw text chunk, handles backspaces/carriage returns/clear screen triggers,
     * strips unnecessary VT100 control sequences, and keeps styling escape sequences in place
     * for visual processing.
     * Keeps track of active VT100 G0/G1 character sets & SO/SI switches to translate box borders properly.
     */
    fun processChunk(tabId: String, currentBuffer: String, chunk: String): String {
        val state = states.computeIfAbsent(tabId) { TerminalState() }
        val sb = StringBuilder()
        sb.append(currentBuffer)

        var i = 0
        val len = chunk.length

        while (i < len) {
            val c = chunk[i]
            when (c) {
                '\u000e' -> { // Shift Out (SO) -> select G1 character set
                    state.activeCharSet = 1
                    i++
                }
                '\u000f' -> { // Shift In (SI) -> select G0 character set
                    state.activeCharSet = 0
                    i++
                }
                '\u0008', '\u007f' -> { // Backspace or Delete
                    if (sb.isNotEmpty() && sb.last() != '\n') {
                        sb.deleteAt(sb.length - 1)
                    }
                    i++
                }
                '\r' -> { // Carriage Return
                    state.pendingCarriageReturn = true
                    i++
                }
                '\n' -> { // Newline
                    state.pendingCarriageReturn = false
                    sb.append('\n')
                    i++
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
                                            state.pendingCarriageReturn = false
                                        }
                                        'K' -> { // Erase line sequence (e.g., [K or [2K)
                                            val lastNewLine = sb.lastIndexOf('\n')
                                            if (lastNewLine != -1) {
                                                sb.setLength(lastNewLine + 1)
                                            } else {
                                                sb.setLength(0)
                                            }
                                            state.pendingCarriageReturn = false
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
                        } else if (next == '(' || next == ')' || next == '*' || next == '+') {
                            // Character set designation sequences of form ESC ( C (3 characters total)
                            if (i + 2 < len) {
                                val designator = chunk[i + 2]
                                if (next == '(') {
                                    state.charSetG0 = designator
                                } else if (next == ')') {
                                    state.charSetG1 = designator
                                }
                                i += 3 // Consume all 3 characters cleanly!
                            } else {
                                i += 2 // Incomplete sequence across chunk, consume prefix
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
                    // Check if we need to apply pending carriage return
                    if (state.pendingCarriageReturn) {
                        val lastNewLine = sb.lastIndexOf('\n')
                        if (lastNewLine != -1) {
                            sb.setLength(lastNewLine + 1)
                        } else {
                            sb.setLength(0)
                        }
                        state.pendingCarriageReturn = false
                    }

                    // Translate character if current character set is line drawing ('0')
                    val translated = translateChar(c, state)
                    sb.append(translated)
                    i++
                }
            }
        }

        return sb.toString()
    }

    private fun translateChar(c: Char, state: TerminalState): Char {
        val currentSet = if (state.activeCharSet == 0) state.charSetG0 else state.charSetG1
        if (currentSet == '0') {
            return when (c) {
                '`' -> '◆' // diamond
                'a' -> '▒' // checkerboard
                'f' -> '°' // degree
                'g' -> '±' // plus/minus
                'j' -> '┘' // lower right corner
                'k' -> '┐' // upper right corner
                'l' -> '┌' // upper left corner
                'm' -> '└' // lower left corner
                'n' -> '┼' // crossing lines
                'o' -> '⎺' // horizontal line 1
                'p' -> '⎻' // horizontal line 2
                'q' -> '─' // horizontal line 3
                'r' -> '⎼' // horizontal line 4
                's' -> '⎽' // horizontal line 5
                't' -> '├' // left tee
                'u' -> '┤' // right tee
                'v' -> '┴' // bottom tee
                'w' -> '┬' // top tee
                'x' -> '│' // vertical line
                'y' -> '≤'
                'z' -> '≥'
                '{' -> 'π'
                '|' -> '≠'
                '}' -> '£'
                '~' -> '·'
                else -> c
            }
        }
        return c
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
                                22 -> currentWeight = FontWeight.Normal
                                24 -> textDecoration = TextDecoration.None
                                
                                // Standard Foreground color codes
                                30 -> currentColor = Color(0xFF2E3440)
                                31 -> currentColor = Color(0xFFBF616A)
                                32 -> currentColor = Color(0xFFA3BE8C)
                                33 -> currentColor = Color(0xFFEBCB8B)
                                34 -> currentColor = Color(0xFF81A1C1)
                                35 -> currentColor = Color(0xFFB48EAD)
                                36 -> currentColor = Color(0xFF88C0D0)
                                37 -> currentColor = Color(0xFFECEFF4)
                                
                                // Bright colors
                                90 -> currentColor = Color(0xFF4C566A)
                                91 -> currentColor = Color(0xFFD08770)
                                92 -> currentColor = Color(0xFFA3BE8C)
                                93 -> currentColor = Color(0xFFEBCB8B)
                                94 -> currentColor = Color(0xFF5E81AC)
                                95 -> currentColor = Color(0xFFB48EAD)
                                96 -> currentColor = Color(0xFF8FBCBB)
                                97 -> currentColor = Color(0xFFE5E9F0)
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
