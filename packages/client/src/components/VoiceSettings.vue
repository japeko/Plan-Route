<script setup lang="ts">
import { availableVoices, selectedVoiceKey, setSelectedVoice, voiceKey } from "@/services/speech.service";

function handleChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value;
  setSelectedVoice(value || null);
}
</script>

<template>
  <div class="voice-settings">
    <label for="voice-select">Navigation voice</label>
    <select
      id="voice-select"
      :value="selectedVoiceKey ?? ''"
      @change="handleChange"
    >
      <option value="">
        Default
      </option>
      <option
        v-for="voice in availableVoices"
        :key="voiceKey(voice)"
        :value="voiceKey(voice)"
      >
        {{ voice.name }} ({{ voice.lang }})
      </option>
    </select>
  </div>
</template>

<style scoped>
.voice-settings {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.voice-settings label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #495057;
}

.voice-settings select {
  font-size: 0.85rem;
  padding: 0.4rem;
  border-radius: 6px;
  border: 1px solid #ced4da;
}
</style>
