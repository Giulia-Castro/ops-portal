<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, DEMO } from '../api/client'
import { api, session } from '../session'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

if (DEMO) {
  void router.replace({ name: 'dashboard' })
}

async function submit() {
  error.value = ''
  busy.value = true
  try {
    session.user = await api.login(username.value, password.value)
    session.loaded = true
    const next = typeof route.query.next === 'string' ? route.query.next : '/'
    await router.push(next.startsWith('/') ? next : '/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : t('common.error')
  } finally {
    password.value = ''
    busy.value = false
  }
}
</script>

<template>
  <section class="card login">
    <h1>{{ t('login.title') }}</h1>
    <form @submit.prevent="submit">
      <label>
        {{ t('login.username') }}
        <input v-model.trim="username" autocomplete="username" required maxlength="100" />
      </label>
      <label>
        {{ t('login.password') }}
        <input v-model="password" type="password" autocomplete="current-password" required maxlength="200" />
      </label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button class="primary" type="submit" :disabled="busy">{{ t('login.submit') }}</button>
    </form>
    <p class="muted small">{{ t('login.hint') }}</p>
  </section>
</template>
