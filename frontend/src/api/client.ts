import type { Dashboard, Me, Problem, ReportRun } from './types'

export const DEMO = import.meta.env.VITE_DEMO === 'true'

export class ApiError extends Error {
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.detail || problem.title)
    this.problem = problem
  }
}

type Fetch = typeof fetch

let csrf: { headerName: string } | null = null

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((part) => part.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

/**
 * Thin client for the BFF. Session cookie + CSRF double-submit: the API sets XSRF-TOKEN and
 * names the header to echo it in. In demo mode, reads a static snapshot instead.
 */
export function createClient(http: Fetch = fetch.bind(globalThis), language = () => 'en') {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Accept-Language', language())
    if (init.method && init.method !== 'GET') {
      if (!csrf) {
        csrf = await request<{ headerName: string }>('/api/auth/csrf')
      }
      const token = readCookie('XSRF-TOKEN')
      if (token) headers.set(csrf.headerName, token)
      headers.set('Content-Type', 'application/json')
    }
    const response = await http(path, { ...init, headers, credentials: 'same-origin' })
    if (response.status === 204) return undefined as T
    const body = await response.json().catch(() => null)
    if (!response.ok) {
      throw new ApiError(
        body?.code
          ? (body as Problem)
          : { status: response.status, code: `HTTP_${response.status}`, title: '', detail: '' },
      )
    }
    return body as T
  }

  async function demo<T>(file: string): Promise<T> {
    const response = await http(`${import.meta.env.BASE_URL}demo/${file}`)
    return (await response.json()) as T
  }

  return {
    async me(): Promise<Me | null> {
      if (DEMO) return { username: 'demo', roles: ['VIEWER'] }
      try {
        return await request<Me>('/api/auth/me')
      } catch (error) {
        if (error instanceof ApiError && error.problem.status === 401) return null
        throw error
      }
    },
    login(username: string, password: string): Promise<Me> {
      return request<Me>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      })
    },
    async logout(): Promise<void> {
      await request<void>('/api/auth/logout', { method: 'POST' })
      csrf = null
    },
    dashboard(): Promise<Dashboard> {
      return DEMO ? demo<Dashboard>('dashboard.json') : request<Dashboard>('/api/v1/dashboard')
    },
    reportRuns(): Promise<ReportRun[]> {
      return DEMO ? demo<ReportRun[]>('report-runs.json') : request<ReportRun[]>('/api/v1/report-runs')
    },
    runReport(reportDate: string): Promise<ReportRun> {
      return request<ReportRun>('/api/v1/report-runs', {
        method: 'POST',
        body: JSON.stringify({ reportDate }),
      })
    },
  }
}

export type ApiClient = ReturnType<typeof createClient>
