// Mirrors the JSON returned by the portal API (Spring Boot BFF).

export type Health = 'GREEN' | 'YELLOW' | 'RED'
export type RunOutcome = 'DELIVERED' | 'FAILED' | 'UNAVAILABLE'

export interface SourceResult<T> {
  data: T | null
  available: boolean
}

export interface TriageKpis {
  total: number
  routed: number
  needsReview: number
  categoryAccuracy: number | null
  severityAccuracy: number | null
  feedbackCount: number
  p95LatencyMs: number
  totalCostUsd: number
  byCategory: Record<string, number>
}

export interface ReportKpis {
  generated: number
  delivered: number
  aiSummaries: number
  fallbackSummaries: number
  failures: number
  successRate: number | null
  avgPipelineSeconds: number | null
  totalLlmCostUsd: number
  estimatedHoursSaved: number
}

export interface DailyReport {
  id: string
  reportDate: string
  status: string
  health: Health
  summarySource: string | null
  totalRequests: number
  errorRate: number
  p95LatencyMs: number
  anomalyCount: number
  pipelineSeconds: number | null
}

export interface Dashboard {
  generatedAt: string
  triage: SourceResult<TriageKpis>
  reports: SourceResult<ReportKpis>
  recentReports: DailyReport[]
  health: { green: number; yellow: number; red: number }
  impact: {
    reportHoursSaved: number
    triageHoursSaved: number
    totalHoursSaved: number
    llmCostUsd: number
    complete: boolean
  }
}

export interface ReportRun {
  id: string
  reportDate: string
  requestedBy: string
  requestedAt: string
  outcome: RunOutcome
  health: Health | null
  durationMs: number
}

export interface Me {
  username: string
  roles: string[]
}

/** RFC 7807 problem returned by the API. */
export interface Problem {
  status: number
  code: string
  title: string
  detail: string
  correlationId?: string
}
