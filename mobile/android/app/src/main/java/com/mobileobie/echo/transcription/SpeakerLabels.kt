package com.mobileobie.echo.transcription

object SpeakerLabels {
    fun displayName(speakerId: String): String {
        val number = speakerId.removePrefix("speaker-")
        return if (number != speakerId && number.all(Char::isDigit)) "Speaker $number" else speakerId
    }

    fun normalize(names: Map<String, String>): Map<String, String> {
        val normalized = names.mapValues { (_, name) -> name.trim() }
        require(normalized.values.all { name ->
            name.isNotEmpty() && name.length <= MAX_NAME_LENGTH && ':' !in name && '\n' !in name && '\r' !in name
        }) { "Speaker names must be 1–$MAX_NAME_LENGTH characters without colons or line breaks." }
        require(normalized.values.distinctBy(String::lowercase).size == normalized.size) {
            "Each speaker needs a distinct name."
        }
        return normalized
    }

    fun renameInText(text: String, names: Map<String, String>): String {
        var renamed = text
        val normalized = normalize(names)
        val placeholders = normalized.keys.mapIndexed { index, speakerId ->
            speakerId to "__HUH_SPEAKER_${index}__"
        }.toMap()
        normalized.entries
            .sortedByDescending { (speakerId, _) -> displayName(speakerId).length }
            .forEach { (speakerId, _) ->
                val label = Regex.escape(displayName(speakerId))
                renamed = Regex("(^|\\s)$label:", setOf(RegexOption.MULTILINE)).replace(renamed) { match ->
                    "${match.groupValues[1]}${placeholders.getValue(speakerId)}:"
                }
            }
        placeholders.forEach { (speakerId, placeholder) ->
            renamed = renamed.replace("$placeholder:", "${normalized.getValue(speakerId)}:")
        }
        return renamed
    }

    const val MAX_NAME_LENGTH = 40
}
