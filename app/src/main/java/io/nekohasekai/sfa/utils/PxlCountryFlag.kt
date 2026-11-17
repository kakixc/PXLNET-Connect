package io.nekohasekai.sfa.utils

/** Country hints are presentation only; never change a server's routing tag because of them. */
internal fun pxlCountryFlag(label: String): String? {
    val codePoints = label.codePoints().toArray()
    val existing = codePoints.asList().windowed(2).firstOrNull { pair ->
        pair.all { it in 0x1F1E6..0x1F1FF }
    }
    if (existing != null) return String(existing.toIntArray(), 0, 2)

    val words = label.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
    val country = listOf(
        "DE" to listOf("germany", "deutschland", "германия", "германии", "de"),
        "FI" to listOf("finland", "финляндия", "финляндии", "fi"),
        "PL" to listOf("poland", "polska", "польша", "польши", "pl"),
        "NL" to listOf("netherlands", "holland", "нидерланды", "голландия", "nl"),
        "FR" to listOf("france", "франция", "франции", "fr"),
        "SE" to listOf("sweden", "швеция", "швеции", "se"),
        "GB" to listOf("uk", "gb", "britain", "англия", "великобритания"),
        "US" to listOf("usa", "unitedstates", "сша", "us"),
        "RU" to listOf("russia", "россия", "россии", "ru"),
        "KZ" to listOf("kazakhstan", "казахстан", "kz"),
        "TR" to listOf("turkey", "türkiye", "турция", "турции", "tr"),
        "SG" to listOf("singapore", "сингапур", "sg"),
        "JP" to listOf("japan", "япония", "jp"),
    ).firstOrNull { (_, names) -> names.any { it in words } }?.first ?: return null
    return buildString {
        country.forEach { appendCodePoint(0x1F1E6 + it.code - 'A'.code) }
    }
}
