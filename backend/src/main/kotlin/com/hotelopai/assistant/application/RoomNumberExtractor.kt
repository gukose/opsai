package com.hotelopai.assistant.application

import java.util.Locale

internal object RoomNumberExtractor {
    private val digitPatterns = listOf(
        Regex("""\b(?:room|oda)\s*(\d{1,5})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{1,5})\s*(?:numaralı\s*)?oda(?:da|de)?\b""", RegexOption.IGNORE_CASE)
    )
    private val numberWords = "sıfır|bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on|yirmi|otuz|kırk|elli|altmış|yetmiş|seksen|doksan|yüz|bin"
    private val wordsBeforeRoom = Regex("""\b((?:(?:$numberWords)(?:\s+|$)){1,5})numaralı\s+oda(?:da|de)?\b""", RegexOption.IGNORE_CASE)
    private val wordsAfterRoom = Regex("""\boda(?:da|de)?\s+((?:(?:$numberWords)(?:\s+|$)){1,5})""", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? {
        digitPatterns.firstNotNullOfOrNull { pattern -> pattern.find(text)?.groupValues?.getOrNull(1) }?.let { return it }
        val normalized = text.lowercase(Locale.forLanguageTag("tr-TR"))
        val words = wordsBeforeRoom.find(normalized)?.groupValues?.getOrNull(1)
            ?: wordsAfterRoom.find(normalized)?.groupValues?.getOrNull(1)
            ?: return null
        return parseTurkishNumber(words.trim())?.takeIf { it in 1..99_999 }?.toString()
    }

    private fun parseTurkishNumber(value: String): Int? {
        var total = 0
        var current = 0
        for (token in value.split(Regex("\\s+")).filter(String::isNotBlank)) {
            when (token) {
                "sıfır" -> Unit
                "bir" -> current += 1
                "iki" -> current += 2
                "üç" -> current += 3
                "dört" -> current += 4
                "beş" -> current += 5
                "altı" -> current += 6
                "yedi" -> current += 7
                "sekiz" -> current += 8
                "dokuz" -> current += 9
                "on" -> current += 10
                "yirmi" -> current += 20
                "otuz" -> current += 30
                "kırk" -> current += 40
                "elli" -> current += 50
                "altmış" -> current += 60
                "yetmiş" -> current += 70
                "seksen" -> current += 80
                "doksan" -> current += 90
                "yüz" -> current = maxOf(1, current) * 100
                "bin" -> { total += maxOf(1, current) * 1000; current = 0 }
                else -> return null
            }
        }
        return total + current
    }
}
