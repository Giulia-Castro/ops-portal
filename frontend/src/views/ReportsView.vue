<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError, DEMO } from '../api/client'
import type { DailyReport, ReportRun } from '../api/types'
import { day, dateTime, number, percent, seconds } from '../format'
import { api, isOperator } from '../session'

const { t, locale } = useI18n()

function yesterday(): string {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const reportDate = ref(yesterday())
const runs = ref<ReportRun[]>([])
const reports = ref<DailyReport[]>([])
const busy = ref(false)
const message = ref<{ kind: 'ok' | 'error'; text: string } | null>(null)

async function load() {
  const [runList, dashboard] = await Promise.all([api.reportRuns(), api.dashboard()])
  runs.value = runList
  reports.value = [...dashboard.recentReports].reverse()
}

async function run() {
  busy.value = true
  message.value = null
  try {
    const result = await api.runReport(reportDate.value)
    message.value =
      result.outcome === 'DELIVERED'
        ? { kind: 'ok', text: t('runs.success', { date: result.reportDate }) }
        : { kind: 'error', text: t(`outcome.${result.outcome}`) }
    await load()
  } catch (e) {
    message.value = { kind: 'error', text: e instanceof ApiError ? e.message : t('common.error') }
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="reports-page">
    <section class="card">
      <h2>{{ t('runs.title') }}</h2>
      <p class="muted">{{ t('runs.description') }}</p>
      <form class="run-form" @submit.prevent="run">
        <label>
          {{ t('runs.date') }}
          <input v-model="reportDate" type="date" required :max="yesterday()" />
        </label>
        <button class="primary" type="submit" :disabled="busy || !isOperator()">
          {{ busy ? t('runs.running') : t('runs.submit') }}
        </button>
      </form>
      <p v-if="DEMO" class="muted small">{{ t('runs.demoDisabled') }}</p>
      <p v-else-if="!isOperator()" class="muted small">{{ t('runs.operatorOnly') }}</p>
      <p v-if="message" :class="message.kind === 'ok' ? 'success' : 'error'" role="status">{{ message.text }}</p>

      <h3>{{ t('runs.history') }}</h3>
      <p v-if="runs.length === 0" class="muted">{{ t('runs.empty') }}</p>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t('runs.date') }}</th>
              <th>{{ t('runs.requestedBy') }}</th>
              <th>{{ t('runs.requestedAt') }}</th>
              <th>{{ t('runs.outcome') }}</th>
              <th class="num">{{ t('runs.duration') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in runs" :key="item.id">
              <td>{{ day(item.reportDate, locale) }}</td>
              <td>{{ item.requestedBy }}</td>
              <td>{{ dateTime(item.requestedAt, locale) }}</td>
              <td>
                <span v-if="item.health" class="health-dot" :class="`health-${item.health.toLowerCase()}`" />
                {{ t(`outcome.${item.outcome}`) }}
              </td>
              <td class="num">{{ seconds(item.durationMs, locale) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="card">
      <h2>{{ t('reports.title') }}</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t('reports.date') }}</th>
              <th>{{ t('reports.health') }}</th>
              <th class="num">{{ t('reports.requests') }}</th>
              <th class="num">{{ t('reports.errorRate') }}</th>
              <th class="num">{{ t('reports.p95') }}</th>
              <th class="num">{{ t('reports.alerts') }}</th>
              <th>{{ t('reports.source') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="report in reports" :key="report.id">
              <td>{{ day(report.reportDate, locale) }}</td>
              <td>
                <span class="health-dot" :class="`health-${report.health.toLowerCase()}`" />
                {{ t(`health.${report.health}`) }}
              </td>
              <td class="num">{{ number(report.totalRequests, locale) }}</td>
              <td class="num">{{ percent(report.errorRate, locale, 2) }}</td>
              <td class="num">{{ number(report.p95LatencyMs, locale) }} ms</td>
              <td class="num">{{ report.anomalyCount }}</td>
              <td>{{ report.summarySource ? t(`source.${report.summarySource}`) : '–' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
