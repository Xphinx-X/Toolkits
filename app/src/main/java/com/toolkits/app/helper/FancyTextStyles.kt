package com.toolkits.app.helper

/**
 * All Fancy Text style definitions and symbol categories.
 *
 * How the Unicode math block styles work:
 * Unicode has a "Mathematical Alphanumeric Symbols" block (U+1D400–U+1D7FF) that
 * contains entire alternative alphabets — bold, italic, fraktur, script, etc. —
 * laid out sequentially: A=base, B=base+1, …, Z=base+25, then a=lowerBase, …,
 * z=lowerBase+25. Some specific slots in those blocks are holes (unassigned) because
 * an equivalent character already exists elsewhere in Unicode (e.g. ℂ = U+2102 for
 * Double-Struck C). Those are listed in the `upperEx` / `lowerEx` exception maps as
 * index → actual code point. Supplementary-plane code points (> U+FFFF) require
 * `String(Character.toChars(cp))` to encode as a proper two-char surrogate pair.
 */
object FancyTextStyles {

    data class FancyStyle(
        val name: String,
        val isDiscordFriendly: Boolean,
        val convert: (String) -> String
    )

    data class SymbolCategory(val name: String, val symbols: List<String>)

    // ── Math Block Helpers ────────────────────────────────────────────────────

    private fun cp(codePoint: Int): String = String(Character.toChars(codePoint))

    private fun mathBlock(
        text: String,
        upperBase: Int,
        lowerBase: Int,
        upperEx: Map<Int, Int> = emptyMap(),
        lowerEx: Map<Int, Int> = emptyMap()
    ): String = buildString {
        for (c in text) when {
            c in 'A'..'Z' -> { val i = c - 'A'; append(cp(upperEx[i] ?: (upperBase + i))) }
            c in 'a'..'z' -> { val i = c - 'a'; append(cp(lowerEx[i] ?: (lowerBase + i))) }
            else           -> append(c)
        }
    }

    private fun combining(text: String, vararg diacritics: String): String = buildString {
        for (c in text) { append(c); if (c.isLetter() || c.isDigit()) diacritics.forEach { append(it) } }
    }

    private fun perChar(text: String, prefix: String = "", suffix: String = "", sep: String = ""): String =
        buildString {
            text.forEachIndexed { i, c ->
                if (i > 0 && sep.isNotEmpty() && !text[i - 1].isWhitespace() && !c.isWhitespace()) append(sep)
                if (c.isWhitespace()) append(c) else append("$prefix$c$suffix")
            }
        }

    private fun applyMap(text: String, map: Map<Char, String>): String =
        buildString { for (c in text) append(map[c] ?: c.toString()) }

    // ── Unicode Block Exception Maps ──────────────────────────────────────────

    // Holes in each block where standard Unicode already has the glyph elsewhere
    private val EX_FRAKTUR_UP   = mapOf(2 to 0x212D, 7 to 0x210C, 8 to 0x2111, 17 to 0x211C, 25 to 0x2128)
    private val EX_SCRIPT_UP    = mapOf(1 to 0x212C, 4 to 0x2130, 5 to 0x2131, 7 to 0x210B, 8 to 0x2110, 11 to 0x2112, 12 to 0x2133, 17 to 0x211B)
    private val EX_SCRIPT_LO    = mapOf(4 to 0x212F, 6 to 0x210A, 14 to 0x2134)
    private val EX_DSTRUCK_UP   = mapOf(2 to 0x2102, 7 to 0x210D, 13 to 0x2115, 15 to 0x2119, 16 to 0x211A, 17 to 0x211D, 25 to 0x2124)
    private val EX_ITALIC_LO    = mapOf(7 to 0x210E) // italic h = ℎ (Planck constant)

    // ── Character Substitution Maps ───────────────────────────────────────────

    private val MAP_SMALLCAPS = mapOf(
        'a' to "ᴀ", 'b' to "ʙ", 'c' to "ᴄ", 'd' to "ᴅ", 'e' to "ᴇ", 'f' to "ꜰ",
        'g' to "ɢ", 'h' to "ʜ", 'i' to "ɪ", 'j' to "ᴊ", 'k' to "ᴋ", 'l' to "ʟ",
        'm' to "ᴍ", 'n' to "ɴ", 'o' to "ᴏ", 'p' to "ᴘ", 'q' to "ǫ", 'r' to "ʀ",
        's' to "ꜱ", 't' to "ᴛ", 'u' to "ᴜ", 'v' to "ᴠ", 'w' to "ᴡ",
        'x' to "x",  'y' to "ʏ", 'z' to "ᴢ"
    )

    private val MAP_SUPERSCRIPT = mapOf(
        'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ", 'd' to "ᵈ", 'e' to "ᵉ", 'f' to "ᶠ",
        'g' to "ᵍ", 'h' to "ʰ", 'i' to "ⁱ", 'j' to "ʲ", 'k' to "ᵏ", 'l' to "ˡ",
        'm' to "ᵐ", 'n' to "ⁿ", 'o' to "ᵒ", 'p' to "ᵖ", 'r' to "ʳ", 's' to "ˢ",
        't' to "ᵗ", 'u' to "ᵘ", 'v' to "ᵛ", 'w' to "ʷ", 'x' to "ˣ", 'y' to "ʸ",
        'z' to "ᶻ",
        'A' to "ᴬ", 'B' to "ᴮ", 'D' to "ᴰ", 'E' to "ᴱ", 'G' to "ᴳ", 'H' to "ᴴ",
        'I' to "ᴵ", 'J' to "ᴶ", 'K' to "ᴷ", 'L' to "ᴸ", 'M' to "ᴹ", 'N' to "ᴺ",
        'O' to "ᴼ", 'P' to "ᴾ", 'R' to "ᴿ", 'T' to "ᵀ", 'U' to "ᵁ", 'V' to "ⱽ",
        'W' to "ᵂ",
        '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
        '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹"
    )

    private val MAP_SUBSCRIPT = mapOf(
        'a' to "ₐ", 'e' to "ₑ", 'h' to "ₕ", 'i' to "ᵢ", 'j' to "ⱼ", 'k' to "ₖ",
        'l' to "ₗ", 'm' to "ₘ", 'n' to "ₙ", 'o' to "ₒ", 'p' to "ₚ", 'r' to "ᵣ",
        's' to "ₛ", 't' to "ₜ", 'u' to "ᵤ", 'v' to "ᵥ", 'x' to "ₓ",
        '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄",
        '5' to "₅", '6' to "₆", '7' to "₇", '8' to "₈", '9' to "₉"
    )

    private val MAP_UPSIDEDOWN = mapOf(
        'a' to "ɐ", 'b' to "q", 'c' to "ɔ", 'd' to "p", 'e' to "ǝ", 'f' to "ɟ",
        'g' to "ƃ", 'h' to "ɥ", 'i' to "ᴉ", 'j' to "ɾ", 'k' to "ʞ", 'l' to "l",
        'm' to "ɯ", 'n' to "u", 'o' to "o", 'p' to "d", 'q' to "b", 'r' to "ɹ",
        's' to "s", 't' to "ʇ", 'u' to "n", 'v' to "ʌ", 'w' to "ʍ", 'x' to "x",
        'y' to "ʎ", 'z' to "z",
        'A' to "∀", 'B' to "𐐒", 'C' to "Ɔ", 'D' to "ᗡ", 'E' to "Ǝ", 'F' to "Ⅎ",
        'G' to "⅁", 'H' to "H", 'I' to "I", 'J' to "ɾ", 'K' to "ʞ", 'L' to "˥",
        'M' to "W", 'N' to "N", 'O' to "O", 'P' to "Ԁ", 'Q' to "b", 'R' to "ᴚ",
        'S' to "S", 'T' to "⊥", 'U' to "∩", 'V' to "Λ", 'W' to "M", 'X' to "X",
        'Y' to "⅄", 'Z' to "Z",
        '0' to "0", '1' to "Ɩ", '2' to "ᄅ", '3' to "Ɛ", '4' to "ᔭ", '5' to "ϛ",
        '6' to "9", '7' to "L", '8' to "8", '9' to "6",
        '.' to "˙", ',' to "'", '?' to "¿", '!' to "¡", '(' to ")", ')' to "("
    )

    private val MAP_CYRILLIC = mapOf(
        'A' to "А", 'B' to "В", 'C' to "С", 'E' to "Е", 'H' to "Н",
        'I' to "І", 'J' to "Ј", 'K' to "К", 'M' to "М", 'N' to "Й",
        'O' to "О", 'P' to "Р", 'S' to "Ѕ", 'T' to "Т", 'X' to "Х", 'Y' to "У",
        'a' to "а", 'c' to "с", 'e' to "е", 'i' to "і", 'j' to "ј", 'k' to "к",
        'n' to "и", 'o' to "о", 'p' to "р", 's' to "ѕ", 't' to "т",
        'u' to "υ", 'x' to "х", 'y' to "у"
    )

    private val MAP_GREEK = mapOf(
        'A' to "Α", 'B' to "Β", 'E' to "Ε", 'H' to "Η", 'I' to "Ι", 'K' to "Κ",
        'M' to "Μ", 'N' to "Ν", 'O' to "Ο", 'P' to "Ρ", 'T' to "Τ", 'X' to "Χ",
        'Y' to "Υ", 'Z' to "Ζ",
        'a' to "α", 'b' to "β", 'd' to "δ", 'e' to "ε", 'f' to "φ", 'g' to "γ",
        'h' to "η", 'i' to "ι", 'k' to "κ", 'l' to "λ", 'n' to "η", 'o' to "θ",
        'p' to "ρ", 'r' to "я", 's' to "ς", 't' to "τ", 'u' to "υ", 'v' to "ν",
        'w' to "ω", 'x' to "χ", 'y' to "ψ", 'z' to "ζ"
    )

    // ── Style List ────────────────────────────────────────────────────────────

    val all: List<FancyStyle> get() = _styles
    private val _styles: List<FancyStyle> by lazy {
        val list = mutableListOf<FancyStyle>()

        // ── Unicode Math Block Alphabets ──────────────────────────────────────
        list += FancyStyle("Old English / Fraktur", true)
            { mathBlock(it, 0x1D504, 0x1D51E, EX_FRAKTUR_UP) }

        list += FancyStyle("Bold Old English", true)
            { mathBlock(it, 0x1D56C, 0x1D586) }

        list += FancyStyle("Calligraphy / Script", true)
            { mathBlock(it, 0x1D49C, 0x1D4B6, EX_SCRIPT_UP, EX_SCRIPT_LO) }

        list += FancyStyle("Bold Calligraphy", true)
            { mathBlock(it, 0x1D4D0, 0x1D4EA) }

        list += FancyStyle("Double Struck", true)
            { mathBlock(it, 0x1D538, 0x1D552, EX_DSTRUCK_UP) }

        list += FancyStyle("Bold Serif", true)
            { mathBlock(it, 0x1D400, 0x1D41A) }

        list += FancyStyle("Italic Serif", true)
            { mathBlock(it, 0x1D434, 0x1D44E, lowerEx = EX_ITALIC_LO) }

        list += FancyStyle("Bold Italic Serif", true)
            { mathBlock(it, 0x1D468, 0x1D482) }

        list += FancyStyle("Sans Bold", true)
            { mathBlock(it, 0x1D5D4, 0x1D5EE) }

        list += FancyStyle("Sans Italic", true)
            { mathBlock(it, 0x1D608, 0x1D622) }

        list += FancyStyle("Sans Bold Italic", true)
            { mathBlock(it, 0x1D63C, 0x1D656) }

        list += FancyStyle("Monospace / Code", true)
            { mathBlock(it, 0x1D670, 0x1D68A) }

        list += FancyStyle("Fullwidth / Aesthetic", true) { text ->
            buildString {
                for (c in text) append(when {
                    c in 'A'..'Z' -> cp(0xFF21 + (c - 'A'))
                    c in 'a'..'z' -> cp(0xFF41 + (c - 'a'))
                    c in '0'..'9' -> cp(0xFF10 + (c - '0'))
                    c == ' '      -> "\u3000"
                    else          -> c.toString()
                })
            }
        }

        // ── Character-Map Styles ──────────────────────────────────────────────
        list += FancyStyle("Small Caps", true) { text ->
            buildString { for (c in text) append(if (c.isUpperCase()) c else MAP_SMALLCAPS[c] ?: c) }
        }

        list += FancyStyle("Superscript", true) { applyMap(it, MAP_SUPERSCRIPT) }
        list += FancyStyle("Subscript",   true) { applyMap(it, MAP_SUBSCRIPT)   }

        list += FancyStyle("Circled Letters", true) { text ->
            buildString {
                for (c in text) append(when {
                    c in 'A'..'Z' -> cp(0x24B6 + (c - 'A'))
                    c in 'a'..'z' -> cp(0x24D0 + (c - 'a'))
                    c in '1'..'9' -> cp(0x2460 + (c - '1'))
                    c == '0'      -> "⓪"
                    else          -> c.toString()
                })
            }
        }

        list += FancyStyle("Negative Circled", true) { text ->
            buildString {
                for (c in text) append(when {
                    c in 'A'..'Z' -> cp(0x1F150 + (c - 'A'))
                    c in 'a'..'z' -> cp(0x1F150 + (c.uppercaseChar() - 'A'))
                    else          -> c.toString()
                })
            }
        }

        list += FancyStyle("Squared Letters", true) { text ->
            buildString {
                for (c in text) append(when {
                    c in 'A'..'Z' -> cp(0x1F130 + (c - 'A'))
                    c in 'a'..'z' -> cp(0x1F130 + (c.uppercaseChar() - 'A'))
                    else          -> c.toString()
                })
            }
        }

        list += FancyStyle("Upside Down & Flipped", false) { text ->
            applyMap(text, MAP_UPSIDEDOWN).reversed()
        }

        list += FancyStyle("Cyrillic Lookalike", true) { applyMap(it, MAP_CYRILLIC) }
        list += FancyStyle("Greek Lookalike",    true) { applyMap(it, MAP_GREEK)    }

        // ── Combining Diacritic Styles ────────────────────────────────────────
        list += FancyStyle("Strikethrough",         true)  { combining(it, "\u0336") }
        list += FancyStyle("Double Strikethrough",  true)  { combining(it, "\u0335") }
        list += FancyStyle("Underline",             true)  { combining(it, "\u0332") }
        list += FancyStyle("Double Underline",      true)  { combining(it, "\u0333") }
        list += FancyStyle("Overline",              true)  { combining(it, "\u0305") }
        list += FancyStyle("Tilde Strikethrough",   true)  { combining(it, "\u0334") }
        list += FancyStyle("Wavy Underline",        true)  { combining(it, "\u0330") }
        list += FancyStyle("Slash Through",         true)  { combining(it, "\u0338") }

        list += FancyStyle("Glitch / Zalgo", false) { text ->
            // Fixed pattern so result is reproducible — not random
            val above = arrayOf("\u030d", "\u0303", "\u030b", "\u0360", "\u0307")
            val mid   = arrayOf("\u0336", "\u0334", "\u0488")
            val below = arrayOf("\u0317", "\u0323", "\u0325", "\u0332", "\u0330")
            buildString {
                text.forEachIndexed { i, c ->
                    append(c)
                    if (c.isLetter()) {
                        append(above[i % above.size])
                        append(mid  [i % mid  .size])
                        append(below[i % below.size])
                    }
                }
            }
        }

        // ── Keycap / Box Combining ────────────────────────────────────────────
        list += FancyStyle("Keycap", true) { text ->
            buildString { for (c in text) if (c.isWhitespace()) append("   ") else append("$c\u20E3 ") }
        }

        list += FancyStyle("Enclosing Square", true) { text ->
            buildString { for (c in text) if (c.isWhitespace()) append("   ") else append("$c\u20DE ") }
        }

        // ── Bracket Wrappers ──────────────────────────────────────────────────
        list += FancyStyle("【Square Brackets】", true)   { perChar(it, "【", "】") }
        list += FancyStyle("『Fancy Brackets』",  true)   { perChar(it, "『", "』") }
        list += FancyStyle("(Parentheses)",        true)   { perChar(it, "(", ")") }
        list += FancyStyle("[̲̅Underbox]",           true) { text ->
            buildString {
                for (c in text) {
                    if (c.isWhitespace()) append(c)
                    else append("[$c\u0332\u0305]")
                }
            }
        }

        // ── Separator Styles ──────────────────────────────────────────────────
        list += FancyStyle("♥ Heart Separator",    true) { perChar(it, sep = "♥") }
        list += FancyStyle("★ Star Separator",     true) { perChar(it, sep = "★") }
        list += FancyStyle("✦ Diamond Star",       true) { perChar(it, sep = "✦") }
        list += FancyStyle("≋ Wave Separator",     true) { perChar(it, sep = "≋") }
        list += FancyStyle("░ Shade Separator",    true) { perChar(it, sep = "░") }
        list += FancyStyle("• Bullet Separator",   true) { perChar(it, sep = "•") }
        list += FancyStyle("| Pipe Separator",     true) { perChar(it, sep = "|") }

        // ── Full-Text Borders ─────────────────────────────────────────────────
        list += FancyStyle("«» Guillemet",           true) { "« $it »" }
        list += FancyStyle("❝ Quotation ❞",         true) { "❝ $it ❞" }
        list += FancyStyle("─── Line Border ───",   true) { "──── $it ────" }
        list += FancyStyle("~*° Decorative °*~",    true) { "˜\"*°•.˜\"*°• $it •°*\"˜.•°*\"˜" }
        list += FancyStyle("╔═ Box Border ═╗",    true) { text -> "╔═ $text ═╗" }
        list += FancyStyle("⋆｡ Soft Border ｡⋆",   true) { "⋆｡°✩ $it ✩°｡⋆" }
        list += FancyStyle("⌈⌉ Angle Bracket",      true) { "⌈ $it ⌋" }

        list
    }

    val discordOnly: List<FancyStyle> get() = all.filter { it.isDiscordFriendly }

    // ── Symbol Categories (text symbols only, no emoji) ───────────────────────

    val symbolCategories: List<SymbolCategory> = listOf(
        SymbolCategory("Box Drawing", listOf(
            "─","│","┌","┐","└","┘","├","┤","┬","┴","┼",
            "═","║","╔","╗","╚","╝","╠","╣","╦","╩","╬",
            "╒","╓","╕","╖","╙","╛","╜","╞","╡","╟","╢",
            "╭","╮","╯","╰","╱","╲","╳",
            "┄","┅","┆","┇","┈","┉","┊","┋","━","┃","╌","╍","╎","╏"
        )),
        SymbolCategory("Stars & Sparkles", listOf(
            "★","☆","✦","✧","✩","✪","✫","✬","✭","✮","✯","✰",
            "✵","✶","✷","✸","✹","✺","✻","✼","✽","✾","✿","❀","❁",
            "❂","❃","❄","❅","❆","❇","❈","❉","❊","❋","⊹","⟡","⌬",
            "⭑","⭒","✱","✲","✳","✴","⁂","※","⊛"
        )),
        SymbolCategory("Arrows", listOf(
            "→","←","↑","↓","↔","↕","↖","↗","↘","↙",
            "⇒","⇐","⇑","⇓","⇔","⇕","⇖","⇗","⇘","⇙",
            "➜","➝","➞","➡","➢","➣","➤","➥","➦","➧","➨",
            "▸","◂","▴","▾","►","◄","▲","▼","⟶","⟵","⟷",
            "↩","↪","↫","↬","⤴","⤵","⬆","⬇","⬈","⬉","⬊","⬋","⬌","⬍"
        )),
        SymbolCategory("Hearts & Suits", listOf(
            "♥","♡","❤","❥","❦","❧",
            "♠","♣","♦","♤","♧","♢","❖","♾","⚘"
        )),
        SymbolCategory("Geometric", listOf(
            "■","□","▪","▫","▬","▭","▮","▯",
            "●","○","◎","◉","◆","◇","◈","◐","◑","◒","◓",
            "△","▽","◁","▷","▴","▾","◂","▸",
            "⬛","⬜","⬡","⬢","⬟","⬠","⬣",
            "⌀","⌂","⌑","⎔","⏣","⬦","⬧","⬨"
        )),
        SymbolCategory("Zodiac", listOf(
            "♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","⛎",
            "☉","☿","♀","♁","♂","♃","♄","♅","♆"
        )),
        SymbolCategory("Weather & Nature", listOf(
            "☀","☁","☂","☃","☄","☼","☽","☾","⛅","⛈",
            "❄","❅","❆","⚡","⛲","⛰","⛵",
            "✿","❀","❁","❂","❃","❊","❋","⚘","⛱"
        )),
        SymbolCategory("Chess", listOf(
            "♔","♕","♖","♗","♘","♙",
            "♚","♛","♜","♝","♞","♟"
        )),
        SymbolCategory("Music", listOf(
            "♩","♪","♫","♬","♭","♮","♯","𝄞","𝄡","𝄢"
        )),
        SymbolCategory("Numbers & Fractions", listOf(
            "½","⅓","⅔","¼","¾","⅛","⅜","⅝","⅞",
            "Ⅰ","Ⅱ","Ⅲ","Ⅳ","Ⅴ","Ⅵ","Ⅶ","Ⅷ","Ⅸ","Ⅹ","Ⅺ","Ⅻ",
            "①","②","③","④","⑤","⑥","⑦","⑧","⑨","⑩",
            "⑪","⑫","⑬","⑭","⑮","⑯","⑰","⑱","⑲","⑳"
        )),
        SymbolCategory("Currency & Math", listOf(
            "₿","€","£","¥","¢","₹","₩","₪","₫","₭","₮","₱","₲",
            "©","®","™","°","±","×","÷","≠","≈","∞","√",
            "∑","∏","∂","∫","∇","Δ","Ω","π","μ","σ","φ","λ"
        )),
        SymbolCategory("Religious & Peace", listOf(
            "☮","☯","✝","✡","☪","☸","⚛","♾",
            "☦","☩","✞","✟","☬","☭","☰","☱","☲","☳","☴","☵","☶","☷"
        )),
        SymbolCategory("Typography & Misc", listOf(
            "•","◦","‣","§","¶","†","‡","※","⁂","⁎","⁑",
            "❝","❞","„","‟","«","»","‹","›","'","'","“","”",
            "–","—","‒","―","‖","…","‥",
            "꧁","꧂","〰","≋","░","▒","▓","█",
            "「","」","【","】","『","』","《","》","〈","〉",
            "⌈","⌉","⌊","⌋","〔","〕","⟦","⟧","⟨","⟩","⟪","⟫"
        ))
    )
}
