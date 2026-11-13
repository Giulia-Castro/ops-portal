<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Dashboard } from '../api/types'
import HealthTimeline from '../components/HealthTimeline.vue'
import KpiCard from '../components/KpiCard.vue'
import { dateTime, number, percent, usd } from '../format'
import { api } from '../session'

const { t, locale } = useI18n()
const dashboard = ref<Dashboard | null>(null)
const failed = ref(false)

async function load() {
  failed.value = false
  try {
    dashboard.value = await api.dashboard()
  } catch {
    failed.value = true
  }
}

onMounted(load)

const triage = computed(() => dashboard.value?.triage.data ?? null)
const reports = computed(() => dashboard.value?.reports.data ?? null)
const categories = computed(() =>
  Object.entries(triage.value?.byCategory ?? {}).sort((a, b) => b[1] - a[1]),
)
const maxCategory = computed(() => Math.max(1, ...categories.value.map(([, count]) => count)))
const reviewRate = computed(() =>
  triage.value && triage.value.total > 0 ? triage.value.needsReview / triage.value.total : null,
)
</script>

<template>
  <div v-if="failed" class="card">
    <p class="error">{{ t('common.error') }}</p>
    <button type="button" @click="load">{{ t('common.retry') }}</button>
  </div>
  <p v-else-if="!dashboard" class="muted">{{ t('common.loading') }}</p>
  <div v-else class="dashboard">
    <section class="card impact">
      <div class="section-head">
        <h2>{{ t('impact.title') }}</h2>
        <span class="muted small">{{ t('app.updatedAt', { date: dateTime(dashboard.generatedAt, locale) }) }}</span>
      </div>
      <div class="kpi-grid">
        <KpiCard
          emphasis
          :label="t('impact.totalHours')"
          :value="`${number(dashboard.impact.totalHoursSaved, locale, 1)} h`"
          :hint="`${t('impact.reportHours')} ${number(dashboard.impact.reportHoursSaved, locale, 1)} h · ${t('impact.triageHours')} ${number(dashboard.impact.triageHoursSaved, locale, 1)} h`"
        />
        <KpiCard :label="t('impact.llmCost')" :value="usd(dashboard.impact.llmCostUsd, locale)" />
        <KpiCard :label="t('reports.delivered')" :value="reports ? number(reports.delivered, locale) : '–'" />
        <KpiCard :label="t('triage.total')" :value="triage ? number(triage.total, locale) : '–'" />
      </div>
      <p v-if="!dashboard.impact.complete" class="warning" role="status">{{ t('impact.partial') }}</p>
      <p class="muted small">{{ t('impact.estimateNote') }}</p>
    </section>

    <div class="two-columns">
      <section class="card">
        <h2>{{ t('reports.title') }}</h2>
        <p v-if="!reports" class="warning">{{ t('common.unavailable') }}</p>
        <template v-else>
          <div class="kpi-grid kpi-grid--compact">
            <KpiCard :label="t('reports.successRate')" :value="percent(reports.successRate, locale)" />
            <KpiCard
              :label="t('reports.avgPipeline')"
              :value="reports.avgPipelineSeconds === null ? '–' : `${number(reports.avgPipelineSeconds, locale, 1)} s`"
            />
            <KpiCard
              :label="t('reports.aiSummaries')"
              :value="`${reports.aiSummaries} / ${reports.aiSummaries + reports.fallbackSummaries}`"
            />
          </div>
          <h3>{{ t('reports.timeline') }}</h3>
          <HealthTimeline :reports="dashboard.recentReports" />
        </template>
      </section>

      <section class="card">
        <h2>{{ t('triage.title') }}</h2>
        <p v-if="!triage" class="warning">{{ t('common.unavailable') }}</p>
        <template v-else>
          <div class="kpi-grid kpi-grid--compact">
            <KpiCard
              :label="t('triage.accuracy')"
              :value="percent(triage.categoryAccuracy, locale)"
              :hint="triage.feedbackCount ? t('triage.accuracyHint', { count: triage.feedbackCount }) : t('triage.noFeedback')"
            />
            <KpiCard :label="t('triage.reviewRate')" :value="percent(reviewRate, locale)" />
            <KpiCard :label="t('triage.p95')" :value="`${number(triage.p95LatencyMs, locale)} ms`" />
          </div>
          <h3>{{ t('triage.byCategory') }}</h3>
          <ul class="bars">
            <li v-for="[category, count] in categories" :key="category">
              <span class="bar-label">{{ category }}</span>
              <span class="bar-track"><span class="bar-fill" :style="{ width: `${(count / maxCategory) * 100}%` }" /></span>
              <span class="bar-value">{{ count }}</span>
            </li>
          </ul>
        </template>
      </section>
    </div>
  </div>
</template>
