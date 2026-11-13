<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { DEMO } from './api/client'
import { setLocale, type Locale } from './i18n'
import { api, session } from './session'

const { t, locale } = useI18n()
const router = useRouter()

function changeLocale(event: Event) {
  setLocale((event.target as HTMLSelectElement).value as Locale)
}

async function logout() {
  await api.logout().catch(() => undefined)
  session.user = null
  await router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">◆</span>
        <div>
          <div class="brand-name">{{ t('app.title') }}</div>
          <div class="brand-sub">{{ t('app.subtitle') }}</div>
        </div>
      </div>
      <nav v-if="session.user" class="nav">
        <RouterLink to="/">{{ t('app.dashboard') }}</RouterLink>
        <RouterLink to="/reports">{{ t('app.reports') }}</RouterLink>
      </nav>
      <div class="topbar-actions">
        <label class="sr-only" for="locale">{{ t('app.language') }}</label>
        <select id="locale" :value="locale" @change="changeLocale">
          <option value="pt-BR">PT</option>
          <option value="en">EN</option>
        </select>
        <span v-if="session.user && !DEMO" class="user">{{ session.user.username }}</span>
        <button v-if="session.user && !DEMO" class="link" type="button" @click="logout">
          {{ t('app.logout') }}
        </button>
      </div>
    </header>
    <div v-if="DEMO" class="demo-banner" role="note">{{ t('app.demoBanner') }}</div>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>
