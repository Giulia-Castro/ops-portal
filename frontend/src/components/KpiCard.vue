<script setup lang="ts">
defineProps<{ label: string; value: string; hint?: string; emphasis?: boolean }>()
</script>

<template>
  <div class="kpi" :class="{ 'kpi--emphasis': emphasis }">
    <div class="kpi-label">{{ label }}</div>
    <div class="kpi-value">{{ value }}</div>
    <div v-if="hint" class="kpi-hint">{{ hint }}</div>
  </div>
</template>
