package com.aistudio.overread.bzvz.vision.processing

object TextNormalizer {
    fun normalize(text: String): String {
        // 1. Replace newlines with spaces (since we assume comics use artificial line breaks)
        var raw = text.replace("\n", " ").replace("\r", " ")
        // 2. Remove duplicate spaces
        raw = raw.replace(Regex("\\s+"), " ").trim()
        
        // 3. Sentence case for ALL CAPS (heuristic for manga/comics)
        val isAllCaps = raw == raw.uppercase() && raw.any { it.isLetter() }
        if (isAllCaps) {
            val words = raw.split(" ").filter { it.any(Char::isLetter) }
            val isShortAcronym = words.size == 1 && words[0].count { it.isLetter() } <= 4
            val isMultipleAcronyms = words.size <= 3 && words.all { it.count { c -> c.isLetter() } <= 4 }
            
            if (!isShortAcronym && !isMultipleAcronyms) {
                raw = toSentenceCase(raw)
            }
        }
        
        return raw
    }
    
    private fun toSentenceCase(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        var newSentence = true
        for (i in text.indices) {
            val c = text[i]
            if (c.isLetter()) {
                if (newSentence) {
                    sb.append(c.uppercaseChar())
                    newSentence = false
                } else {
                    sb.append(c.lowercaseChar())
                }
            } else {
                sb.append(c)
                // If it's a punctuation mark that ends a sentence
                if (c == '.' || c == '!' || c == '?') {
                    newSentence = true
                }
            }
        }
        // Fix standalone 'i' -> 'I'
        return sb.toString().replace(Regex("\\bi\\b"), "I")
    }
}
