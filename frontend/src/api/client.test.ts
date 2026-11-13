import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createClient } from './client'

function json(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-123'
  })

  it('sends the CSRF cookie back in the header the API names', async () => {
    const http = vi
      .fn()
      .mockResolvedValueOnce(json(200, { headerName: 'X-XSRF-TOKEN' }))
      .mockResolvedValueOnce(json(201, { id: 'r1', outcome: 'DELIVERED' }))
    const client = createClient(http, () => 'pt-BR')

    await client.runReport('2026-11-08')

    const [path, init] = http.mock.calls[1]
    expect(path).toBe('/api/v1/report-runs')
    const headers = init.headers as Headers
    expect(headers.get('X-XSRF-TOKEN')).toBe('token-123')
    expect(headers.get('Accept-Language')).toBe('pt-BR')
    expect(init.body).toBe(JSON.stringify({ reportDate: '2026-11-08' }))
  })

  it('turns RFC 7807 problems into ApiError with the localized detail', async () => {
    const problem = { status: 409, code: 'REPORT_ALREADY_DELIVERED', title: 'Já entregue', detail: 'Relatório já enviado.' }
    const client = createClient(vi.fn().mockResolvedValue(json(409, problem)))

    const error = await client.dashboard().catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem.code).toBe('REPORT_ALREADY_DELIVERED')
    expect((error as ApiError).message).toBe('Relatório já enviado.')
  })

  it('treats 401 on /me as logged out', async () => {
    const client = createClient(vi.fn().mockResolvedValue(json(401, null)))
    await expect(client.me()).resolves.toBeNull()
  })

  it('reports non-problem errors with the HTTP status', async () => {
    const client = createClient(vi.fn().mockResolvedValue(new Response('oops', { status: 502 })))
    const error = (await client.reportRuns().catch((e: unknown) => e)) as ApiError
    expect(error.problem.code).toBe('HTTP_502')
  })
})
