package com.planroute.app.voice

import com.planroute.app.repository.RouteStep

/**
 * Turns OSRM's raw maneuver fields into a short spoken/displayed phrase in
 * one of the three supported nav languages (see [TargetVoiceLanguages]).
 * Deliberately terse — this covers the common OSRM maneuver types closely
 * enough to be useful for real turn-by-turn guidance, not an exhaustive
 * localization of every maneuver/modifier combination OSRM can return.
 */
fun RouteStep.localizedInstruction(languageCode: String?): String {
    val base = baseInstructionFor(maneuverType, maneuverModifier, languageCode)
    if (streetName.isBlank()) return base
    return when (languageCode) {
        "fi", "fin" -> "$base ($streetName)"
        "sv", "swe" -> "$base ($streetName)"
        else -> "$base onto $streetName"
    }
}

/** Wraps [instruction] with a lead-distance prefix ("In 200 m, ...") in the given language. */
fun aheadAnnouncement(instruction: String, distanceMeters: Int, languageCode: String?): String =
    when (languageCode) {
        "fi", "fin" -> "$distanceMeters metrin päästä: $instruction"
        "sv", "swe" -> "Om $distanceMeters meter: $instruction"
        else -> "In $distanceMeters m: $instruction"
    }

private fun baseInstructionFor(type: String, modifier: String?, languageCode: String?): String =
    when (languageCode) {
        "fi", "fin" -> baseInstructionFi(type, modifier)
        "sv", "swe" -> baseInstructionSv(type, modifier)
        else -> baseInstructionEn(type, modifier)
    }

private fun baseInstructionEn(type: String, modifier: String?): String = when (type) {
    "depart" -> "Head out"
    "arrive" -> "Arrive at destination"
    "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
        "At the roundabout, go ${modifierPhraseEn(modifier)}"
    "merge" -> "Merge ${modifierPhraseEn(modifier)}"
    "on ramp" -> "Take the ramp"
    "off ramp" -> "Take the exit"
    "fork" -> "At the fork, keep ${modifierPhraseEn(modifier)}"
    "end of road" -> "Turn ${modifierPhraseEn(modifier)}"
    "continue", "new name" -> if (modifier == null) "Continue straight" else "Continue ${modifierPhraseEn(modifier)}"
    "turn" -> "Turn ${modifierPhraseEn(modifier)}"
    else -> "Continue"
}

private fun modifierPhraseEn(modifier: String?): String = when (modifier) {
    "left" -> "left"
    "right" -> "right"
    "sharp left" -> "sharp left"
    "sharp right" -> "sharp right"
    "slight left" -> "slightly left"
    "slight right" -> "slightly right"
    "straight" -> "straight ahead"
    "uturn" -> "around"
    else -> "ahead"
}

private fun baseInstructionFi(type: String, modifier: String?): String = when (type) {
    "depart" -> "Lähdetään matkaan"
    "arrive" -> "Saavuit määränpäähän"
    "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
        "Liikenneympyrässä käänny ${modifierPhraseFi(modifier)}"
    "merge" -> "Liity kaistalle ${modifierPhraseFi(modifier)}"
    "on ramp" -> "Aja rampille"
    "off ramp" -> "Aja rampilta ulos"
    "fork" -> "Pysy ${modifierPhraseFi(modifier)}"
    "end of road" -> "Käänny ${modifierPhraseFi(modifier)}"
    "continue", "new name" -> if (modifier == null) "Jatka suoraan" else "Jatka ${modifierPhraseFi(modifier)}"
    "turn" -> "Käänny ${modifierPhraseFi(modifier)}"
    else -> "Jatka matkaa"
}

private fun modifierPhraseFi(modifier: String?): String = when (modifier) {
    "left" -> "vasemmalle"
    "right" -> "oikealle"
    "sharp left" -> "jyrkästi vasemmalle"
    "sharp right" -> "jyrkästi oikealle"
    "slight left" -> "loivasti vasemmalle"
    "slight right" -> "loivasti oikealle"
    "straight" -> "suoraan"
    "uturn" -> "ympäri"
    else -> "eteenpäin"
}

private fun baseInstructionSv(type: String, modifier: String?): String = when (type) {
    "depart" -> "Kör iväg"
    "arrive" -> "Framme vid resmålet"
    "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
        "Vid rondellen, sväng ${modifierPhraseSv(modifier)}"
    "merge" -> "Kör in ${modifierPhraseSv(modifier)}"
    "on ramp" -> "Kör upp på påfarten"
    "off ramp" -> "Kör av vid avfarten"
    "fork" -> "Håll ${modifierPhraseSv(modifier)}"
    "end of road" -> "Sväng ${modifierPhraseSv(modifier)}"
    "continue", "new name" -> if (modifier == null) "Fortsätt rakt fram" else "Fortsätt ${modifierPhraseSv(modifier)}"
    "turn" -> "Sväng ${modifierPhraseSv(modifier)}"
    else -> "Fortsätt"
}

private fun modifierPhraseSv(modifier: String?): String = when (modifier) {
    "left" -> "vänster"
    "right" -> "höger"
    "sharp left" -> "skarpt vänster"
    "sharp right" -> "skarpt höger"
    "slight left" -> "svagt vänster"
    "slight right" -> "svagt höger"
    "straight" -> "rakt fram"
    "uturn" -> "och vänd"
    else -> "framåt"
}
