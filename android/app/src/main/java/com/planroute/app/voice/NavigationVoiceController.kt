package com.planroute.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Only these languages are offered, per the product requirement — whatever
 * else the device's TTS engine has installed is filtered out. Both the
 * ISO 639-1 (2-letter, what [Locale.getLanguage] normally returns) and
 * ISO 639-2 (3-letter) forms are matched defensively, in case a voice's
 * locale was built from the longer code.
 */
private val SupportedLanguages = setOf("fi", "fin", "en", "eng", "sv", "swe")

/** Languages this feature targets, for checking which ones a device actually has voices for. */
val TargetVoiceLanguages = listOf("fi" to "Finnish", "en" to "English", "sv" to "Swedish")

private const val TAG = "NavigationVoice"
private const val UtteranceId = "planroute-nav"

data class NavigationVoiceOption(val voice: Voice, val label: String)

/**
 * Thin wrapper around Android's built-in [TextToSpeech] engine for spoken
 * turn-by-turn guidance. [TextToSpeech] initializes asynchronously, so
 * [speak] queues a single pending utterance if called before the engine
 * reports ready rather than dropping it. The selected voice is re-applied
 * right before every [speak] call (not just once, in [selectVoice]) since
 * `setVoice()` can silently fail for a given voice (e.g. a network voice
 * with no connection) — always worth retrying rather than leaving whatever
 * voice happened to be active.
 */
class NavigationVoiceController(context: Context) {
    /** Compose-observable so callers can react (e.g. auto-selecting a default voice) once the engine finishes initializing. */
    var isReady by mutableStateOf(false)
        private set
    private var pendingUtterance: String? = null
    private var selectedVoiceOption: NavigationVoiceOption? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            pendingUtterance?.let { speak(it) }
            pendingUtterance = null
        } else {
            Log.e(TAG, "TextToSpeech failed to initialize (status $status)")
        }
    }.apply {
        setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}

                @Deprecated("Deprecated in Java", ReplaceWith(""))
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Utterance '$utteranceId' failed")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "Utterance '$utteranceId' failed (error code $errorCode)")
                }
            },
        )
    }

    /**
     * Finnish/English/Swedish voices currently installed on the device —
     * empty until the engine finishes initializing. A TTS engine commonly
     * exposes several raw [Voice] entries per language (different
     * quality/gender/network tiers) that all render as the same display
     * label (e.g. several "English (Australia)" entries); those collapse
     * to a single best one here rather than listing every duplicate,
     * preferring one whose voice data is actually downloaded (see
     * [Voice.isFullyInstalled]) over one that merely exists in the
     * engine's catalog but would fail with [TextToSpeech.ERROR_NOT_INSTALLED_YET]
     * if used. If every voice for a label is still undownloaded, the
     * label gets a "(needs download)" suffix so that's visible up front
     * rather than only discovered when speech silently fails.
     * Voices whose locale doesn't resolve to a displayable name are
     * dropped rather than shown blank.
     */
    fun availableVoices(): List<NavigationVoiceOption> {
        if (!isReady) return emptyList()
        return tts.voices.orEmpty()
            .filter { it.locale.language in SupportedLanguages }
            .mapNotNull { voice -> voiceLabel(voice).takeIf { it.isNotBlank() }?.let { voice to it } }
            .groupBy { (_, label) -> label }
            .map { (_, entries) ->
                val (bestVoice, label) = entries.maxWith(
                    compareBy(
                        { (voice, _) -> voice.isFullyInstalled() },
                        { (voice, _) -> voice.quality },
                        { (voice, _) -> !voice.isNetworkConnectionRequired },
                    ),
                )
                val displayLabel = if (bestVoice.isFullyInstalled()) label else "$label (needs download)"
                NavigationVoiceOption(bestVoice, displayLabel)
            }
            .sortedWith(compareBy({ it.voice.locale.language }, { it.label }))
    }

    fun selectVoice(option: NavigationVoiceOption) {
        selectedVoiceOption = option
        applySelectedVoice()
    }

    fun speak(text: String) {
        if (!isReady) {
            pendingUtterance = text
            return
        }
        val option = selectedVoiceOption
        if (option != null && !option.voice.isFullyInstalled()) {
            // Attempting this would just fail with ERROR_NOT_INSTALLED_YET —
            // skip the doomed call and log something actionable instead.
            Log.w(TAG, "Not speaking: \"${option.label}\" voice data isn't fully downloaded yet")
            return
        }
        applySelectedVoice()
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UtteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "speak() rejected for \"$text\" with voice ${selectedVoiceOption?.label}")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun applySelectedVoice() {
        val option = selectedVoiceOption ?: return
        if (!isReady) return
        if (tts.voice == option.voice) return
        val result = tts.setVoice(option.voice)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "setVoice() failed for ${option.label} (${option.voice.name})")
        }
    }
}

/** False if selecting this voice would fail at speak time with ERROR_NOT_INSTALLED_YET (-9) — its data isn't downloaded yet. */
private fun Voice.isFullyInstalled(): Boolean =
    !features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

private fun voiceLabel(voice: Voice): String {
    val language = voice.locale.getDisplayLanguage(Locale.getDefault())
    if (language.isBlank()) return ""
    val languageCapitalized = language.replaceFirstChar(Char::uppercase)
    val country = voice.locale.getDisplayCountry(Locale.getDefault())
    return if (country.isNotBlank() && country != languageCapitalized) "$languageCapitalized ($country)" else languageCapitalized
}
