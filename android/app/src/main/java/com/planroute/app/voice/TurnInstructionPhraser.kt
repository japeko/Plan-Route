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
    // "Arrive at destination onto X" doesn't read as a sentence the way
    // "Turn left onto X" does — every language uses the parenthetical
    // form for the arrive step specifically.
    if (maneuverType == "arrive") return "$base ($streetName)"
    return when (languageCode) {
        "fi", "fin" -> "$base ($streetName)"
        "sv", "swe" -> "$base ($streetName)"
        else -> "$base onto $streetName"
    }
}

/**
 * A directional glyph for the navigation banner's turn icon, driven purely
 * by OSRM's modifier — language-independent, and deliberately not keyed on
 * maneuverType, so an "arrive" step with modifier "right" (destination is
 * on the right) shows the same right-pointing arrow a "turn right" would,
 * rather than freezing on whatever the previous turn's arrow happened to
 * be (the banner used to show one hardcoded glyph forever, regardless of
 * the current step).
 */
fun arrowGlyphFor(modifier: String?): String = when (modifier) {
    "left", "sharp left", "slight left" -> "↰"
    "right", "sharp right", "slight right" -> "↱"
    "uturn" -> "↩"
    else -> "↑"
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
    "arrive" -> when (modifier) {
        "left" -> "Arrive at destination, on the left"
        "right" -> "Arrive at destination, on the right"
        else -> "Arrive at destination"
    }
    "roundabout", "rotary", "roundabout turn" -> "At the roundabout, go ${modifierPhraseEn(modifier)}"
    // A separate step OSRM sometimes emits right after "roundabout" for
    // the actual exit onto the new street — phrased as a plain turn, not
    // "at the roundabout..." again, or the two steps sound identical.
    "exit roundabout", "exit rotary" -> "Turn ${modifierPhraseEn(modifier)}"
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
    "arrive" -> when (modifier) {
        "left" -> "Saavuit määränpäähän, vasemmalla"
        "right" -> "Saavuit määränpäähän, oikealla"
        else -> "Saavuit määränpäähän"
    }
    "roundabout", "rotary", "roundabout turn" -> "Liikenneympyrässä käänny ${modifierPhraseFi(modifier)}"
    // A separate step OSRM sometimes emits right after "roundabout" for
    // the actual exit onto the new street — phrased as a plain turn, not
    // "liikenneympyrässä..." again, or the two steps sound identical.
    "exit roundabout", "exit rotary" -> "Käänny ${modifierPhraseFi(modifier)}"
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
    "arrive" -> when (modifier) {
        "left" -> "Framme vid resmålet, till vänster"
        "right" -> "Framme vid resmålet, till höger"
        else -> "Framme vid resmålet"
    }
    "roundabout", "rotary", "roundabout turn" -> "Vid rondellen, sväng ${modifierPhraseSv(modifier)}"
    // A separate step OSRM sometimes emits right after "roundabout" for
    // the actual exit onto the new street — phrased as a plain turn, not
    // "vid rondellen..." again, or the two steps sound identical.
    "exit roundabout", "exit rotary" -> "Sväng ${modifierPhraseSv(modifier)}"
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
