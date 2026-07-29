import { ref } from "vue";

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

let fallbackVoice: SpeechSynthesisVoice | null = null;

function loadVoices(): void {
  const voices = window.speechSynthesis.getVoices();
  if (voices.length === 0) {
    return;
  }
  availableVoices.value = voices;

  const englishVoices = voices.filter((voice) => voice.lang.toLowerCase().startsWith("en"));
  const candidates = englishVoices.length > 0 ? englishVoices : voices;
  fallbackVoice =
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
  return fallbackVoice;
}

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
