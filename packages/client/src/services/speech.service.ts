import { computed, ref } from "vue";
import type { NavigationLanguage } from "@/types/route.types";

// Voices are restricted to these languages, since translated instructions
// only exist for them (see routing.service.ts) — no point offering a
// voice we'd only ever feed English text to.
const SUPPORTED_LANGUAGES: NavigationLanguage[] = ["en", "fi", "sv"];

// The primary language subtag — the part before the region, e.g. "en" from
// "en-US" or "en_US" (some Android/browser combinations use an underscore
// instead of the standard BCP-47 dash). Not a startsWith() check against
// the tag as a whole: Filipino is "fil-PH"/"fil_PH", and
// "fil-ph".startsWith("fi") is true in JS, which let Filipino voices slip
// through a naive Finnish-prefix filter.
function primaryLanguageSubtag(lang: string): string {
  return lang.toLowerCase().split(/[-_]/)[0] ?? "";
}

// The Web Speech API exposes no gender/accent fields on SpeechSynthesisVoice
// — only a name and a BCP-47 language tag (e.g. "en-IN", "en-AU"). Which
// voices exist at all (an Indian-accented English voice, an Australian
// one, etc.) is entirely up to what the browser/OS ships, so the best we
// can do is list whatever's actually installed and let the user pick.
const VOICE_STORAGE_KEY = "poi-selected-voice-key";

// Identity key for a voice. Not voice.voiceURI: many Android/Chrome TTS
// voices report an empty string for it, which made every such voice
// collapse to the same (falsy) key as "no selection" and silently fall
// back to the default. name+lang is always populated.
export function voiceKey(voice: SpeechSynthesisVoice): string {
  return `${voice.name}|${voice.lang}`;
}

// Fallback heuristic (used until the user picks a voice explicitly): match
// against known female voice names — these are the common ones across
// Chrome/Edge/Safari/Android.
const FEMALE_VOICE_NAME_HINTS = [
  "female",
  "samantha",
  "zira",
  "hazel",
  "susan",
  "karen",
  "moira",
  "tessa",
  "fiona",
  "victoria",
  "allison",
  "ava",
  "serena",
  "kate",
  "salli",
  "joanna",
  "kimberly",
  "kendra",
  "ivy",
];

export const availableVoices = ref<SpeechSynthesisVoice[]>([]);
export const selectedVoiceKey = ref<string | null>(
  typeof localStorage !== "undefined" ? localStorage.getItem(VOICE_STORAGE_KEY) : null,
);

const fallbackVoice = ref<SpeechSynthesisVoice | null>(null);

function loadVoices(): void {
  const voices = window.speechSynthesis
    .getVoices()
    .filter((voice) => (SUPPORTED_LANGUAGES as string[]).includes(primaryLanguageSubtag(voice.lang)));
  if (voices.length === 0) {
    return;
  }
  availableVoices.value = voices;

  const englishVoices = voices.filter((voice) => primaryLanguageSubtag(voice.lang) === "en");
  const candidates = englishVoices.length > 0 ? englishVoices : voices;
  fallbackVoice.value =
    candidates.find((voice) => FEMALE_VOICE_NAME_HINTS.some((hint) => voice.name.toLowerCase().includes(hint))) ??
    null;
}

if (typeof window !== "undefined" && "speechSynthesis" in window) {
  // Voice lists load asynchronously in most browsers, so try immediately
  // and again once the browser reports them.
  loadVoices();
  window.speechSynthesis.onvoiceschanged = loadVoices;
}

export function setSelectedVoice(key: string | null): void {
  selectedVoiceKey.value = key;
  if (key) {
    localStorage.setItem(VOICE_STORAGE_KEY, key);
  } else {
    localStorage.removeItem(VOICE_STORAGE_KEY);
  }
}

function resolveVoice(): SpeechSynthesisVoice | null {
  if (selectedVoiceKey.value) {
    const chosen = availableVoices.value.find((voice) => voiceKey(voice) === selectedVoiceKey.value);
    if (chosen) {
      return chosen;
    }
  }
  return fallbackVoice.value;
}

// Which translated instruction text to speak, derived from whichever
// voice will actually be used — falls back to English if the resolved
// voice's language isn't one of the supported navigation languages (e.g.
// no voices loaded yet).
export const currentLanguage = computed<NavigationLanguage>(() => {
  const subtag = primaryLanguageSubtag(resolveVoice()?.lang ?? "en");
  return SUPPORTED_LANGUAGES.find((lang) => lang === subtag) ?? "en";
});

export function speak(text: string): void {
  if (!("speechSynthesis" in window)) {
    return;
  }
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  const voice = resolveVoice();
  if (voice) {
    utterance.voice = voice;
  }
  window.speechSynthesis.speak(utterance);
}

export function stopSpeaking(): void {
  if ("speechSynthesis" in window) {
    window.speechSynthesis.cancel();
  }
}
