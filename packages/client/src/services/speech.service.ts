// The Web Speech API exposes no gender field on SpeechSynthesisVoice, so
// picking a "female" voice means matching against known voice names —
// these are the common ones across Chrome/Edge/Safari/Android.
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

let preferredVoice: SpeechSynthesisVoice | null = null;

function loadPreferredVoice(): void {
  const voices = window.speechSynthesis.getVoices();
  if (voices.length === 0) {
    return;
  }

  const englishVoices = voices.filter((voice) => voice.lang.toLowerCase().startsWith("en"));
  const candidates = englishVoices.length > 0 ? englishVoices : voices;

  preferredVoice =
    candidates.find((voice) => FEMALE_VOICE_NAME_HINTS.some((hint) => voice.name.toLowerCase().includes(hint))) ??
    null;
}

if (typeof window !== "undefined" && "speechSynthesis" in window) {
  // Voice lists load asynchronously in most browsers, so try immediately
  // and again once the browser reports them.
  loadPreferredVoice();
  window.speechSynthesis.onvoiceschanged = loadPreferredVoice;
}

export function speak(text: string): void {
  if (!("speechSynthesis" in window)) {
    return;
  }
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  if (preferredVoice) {
    utterance.voice = preferredVoice;
  }
  window.speechSynthesis.speak(utterance);
}

export function stopSpeaking(): void {
  if ("speechSynthesis" in window) {
    window.speechSynthesis.cancel();
  }
}
