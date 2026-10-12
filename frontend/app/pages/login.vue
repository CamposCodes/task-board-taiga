<script setup lang="ts">
import type { Me } from '~/utils/types'

interface AuthConfig {
  oidcEnabled: boolean
  oidcLabel: string
  oidcLoginUrl: string
}

definePageMeta({ layout: false })
useHead({ title: 'Entrar - Taskboard' })

const route = useRoute()
const me = useMe()
const api = useApi()

const { data: config } = await useAsyncData('auth-config', () => api<AuthConfig>('/auth/config').catch(() => null))

const next = computed(() => {
  const value = route.query.next
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/'
})
const providerError = computed(() => (typeof route.query.erro === 'string' ? route.query.erro : ''))

const showPasswordForm = ref(!config.value?.oidcEnabled)
const form = reactive({ username: '', password: '' })
const loading = ref(false)

function loginWithProvider() {
  document.cookie = `login_next=${encodeURIComponent(next.value)}; path=/; max-age=600; samesite=lax`
  window.location.assign(config.value!.oidcLoginUrl)
}

async function submit() {
  loading.value = true
  try {
    me.value = await api<Me>('/auth/login', { method: 'POST', body: form })
    await navigateTo(next.value)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <img class="brand" src="/r2da-logo-white.svg" width="217" height="91" alt="R2DA Tecnologia">
    <div class="panel">
      <h1 class="page-title" style="margin: 0 0 24px; text-align: center">Taskboard</h1>

      <p v-if="providerError" class="blocked-banner" style="margin: 0 0 16px">{{ providerError }}</p>

      <template v-if="config?.oidcEnabled">
        <button class="btn big" type="button" @click="loginWithProvider">Entrar com {{ config.oidcLabel }}</button>
        <p v-if="!showPasswordForm" style="margin-top: 16px; text-align: center">
          <a href="#" class="pointer" @click.prevent="showPasswordForm = true">Entrar com usuário local</a>
        </p>
      </template>

      <form v-if="showPasswordForm" :style="config?.oidcEnabled ? 'margin-top: 24px' : ''" @submit.prevent="submit">
        <div class="form-row">
          <input
            v-model="form.username"
            class="field"
            placeholder="Usuário ou e-mail"
            aria-label="Usuário ou e-mail"
            autocomplete="username"
            v-focus
            required
          >
        </div>
        <div class="form-row">
          <input
            v-model="form.password"
            class="field"
            type="password"
            placeholder="Senha"
            aria-label="Senha"
            autocomplete="current-password"
            required
          >
        </div>
        <button class="btn big" :class="{ gray: config?.oidcEnabled }" type="submit" :disabled="loading">Entrar</button>
      </form>
    </div>
  </div>
</template>
