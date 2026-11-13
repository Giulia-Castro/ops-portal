<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DailyReport } from '../api/types'
import { day, percent } from '../format'

const props = defineProps<{ reports: DailyReport[] }>()
const { t, locale } = useI18n()

const maxRate = computed(() => Math.max(0.01, ...props.reports.map((r) => r.errorRate)))
</script>

<template>
  <figure class="timeline">
    <ol class="timeline-days">
      <li
        v-for="report in reports"
        :key="report.id"
        class="timeline-day"
        :title="`${report.reportDate} · ${t(`health.${report.health}`)} · ${percent(report.errorRate, locale, 2)}`"
      >
        <div class="timeline-bar-area">
          <div
            class="timeline-bar"
            :class="`health-${report.health.toLowerCase()}`"
            :style="{ height: `${Math.max(6, (report.errorRate / maxRate) * 100)}%` }"
          />
        </div>
        <span class="health-dot" :class="`health-${report.health.toLowerCase()}`" />
        <span class="timeline-label">{{ day(report.reportDate, locale) }}</span>
      </li>
    </ol>
    <figcaption class="timeline-legend">
      <span><span class="health-dot health-green" /> {{ t('health.GREEN') }}</span>
      <span><span class="health-dot health-yellow" /> {{ t('health.YELLOW') }}</span>
      <span><span class="health-dot health-red" /> {{ t('health.RED') }}</span>
      <span class="muted">· {{ t('reports.errorRate') }}</span>
    </figcaption>
  </figure>
</template>
