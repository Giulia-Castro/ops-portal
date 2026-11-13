import { reactive } from 'vue'
import { createClient, DEMO } from './api/client'
import type { Me } from './api/types'
import { i18n } from './i18n'

export const api = createClient(fetch.bind(globalThis), () => i18n.global.locale.value)

export const session = reactive<{ user: Me | null; loaded: boolean }>({ user: null, loaded: false })

export async function loadSession(): Promise<Me | null> {
  if (!session.loaded) {
    session.user = await api.me()
    session.loaded = true
  }
  return session.user
}

export function isOperator(): boolean {
  return !DEMO && (session.user?.roles.includes('OPERATOR') ?? false)
}
