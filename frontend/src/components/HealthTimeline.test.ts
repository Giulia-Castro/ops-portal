import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import en from '../locales/en.json'
import ptBR from '../locales/pt-BR.json'
import type { DailyReport } from '../api/types'
import { day, percent, usd } from '../format'
import HealthTimeline from './HealthTimeline.vue'

const report = (reportDate: string, health: DailyReport['health'], errorRate: number): DailyReport => ({
  id: reportDate,
  reportDate,
  status: 'DELIVERED',
  health,
  summarySource: 'FALLBACK',
  totalRequests: 1500,
  errorRate,
  p95LatencyMs: 400,
  anomalyCount: health === 'GREEN' ? 0 : 2,
  pipelineSeconds: 7,
})

describe('HealthTimeline', () => {
  it('draws one bar per day, colored by health and scaled by error rate', () => {
    const i18n = createI18n({ legacy: false, locale: 'pt-BR', messages: { en, 'pt-BR': ptBR } })
    const wrapper = mount(HealthTimeline, {
      props: { reports: [report('2026-11-03', 'GREEN', 0.002), report('2026-11-04', 'RED', 0.0101)] },
      global: { plugins: [i18n] },
    })

    const bars = wrapper.findAll('.timeline-bar')
    expect(bars).toHaveLength(2)
    expect(bars[1].classes()).toContain('health-red')
    expect(bars[1].attributes('style')).toContain('height: 100%')
    expect(wrapper.text()).toContain('04/11')
    expect(wrapper.text()).toContain('Crítico')
  })
})

describe('format', () => {
  it('formats per locale without shifting calendar days', () => {
    expect(day('2026-11-04', 'pt-BR')).toBe('04/11')
    expect(day('2026-11-04', 'en')).toBe('11/04')
    expect(percent(0.0633, 'pt-BR')).toBe('6,3%')
    expect(percent(null, 'en')).toBe('–')
    expect(usd(0.0185, 'en')).toBe('$0.0185')
  })
})
