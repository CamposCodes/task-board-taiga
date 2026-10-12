<script setup lang="ts">
import { copyToClipboard, Notify } from 'quasar'
import type { ApiToken } from '~/utils/types'

const timeAgo = useTimeAgo()
const now = useState('now', () => Date.now())
const writes = (token: ApiToken) => token.scopes.includes('write')
const expired = (token: ApiToken) => !!token.expiresAt && new Date(token.expiresAt).getTime() < now.value

useHead({ title: 'Tokens de API - Taskboard' })

const api = useApi()
const { data: tokens, refresh } = await useAsyncData('tokens', () => api<ApiToken[]>('/tokens'))

const form = reactive({ name: '', write: false, expiresInDays: '' })
const created = ref<string | null>(null)
const saving = ref(false)

async function create() {
  saving.value = true
  try {
    const result = await api<{ token: string }>('/tokens', {
      method: 'POST',
      body: { name: form.name, write: form.write, expiresInDays: form.expiresInDays ? Number(form.expiresInDays) : null },
    })
    created.value = result.token
    form.name = ''
    await refresh()
  } finally {
    saving.value = false
  }
}

async function copy() {
  await copyToClipboard(created.value!)
  Notify.create({ type: 'positive', message: 'Token copiado.' })
}

async function revoke(token: ApiToken) {
  if (await confirmDelete(`o token "${token.name}"? O que usa esse token para de funcionar`)) {
    await api(`/tokens/${token.id}`, { method: 'DELETE' })
    await refresh()
  }
}
</script>

<template>
  <div style="max-width: 960px">
    <h1 class="page-title">Tokens de API</h1>
    <p class="form-row muted" style="max-width: 68ch">
      Um token deixa uma integração, como o servidor MCP usado por assistentes de IA, agir em seu nome sem a sua senha. Tudo
      o que for feito com ele fica registrado no histórico como "via token".
    </p>

    <div v-if="created" class="callout form-row">
      <p class="medium" style="margin-bottom: 8px">Copie o token agora. Ele não será mostrado de novo.</p>
      <div class="row-center gap-8">
        <input class="field grow" style="font-family: ui-monospace, Consolas, monospace" :value="created" readonly aria-label="Token criado" @focus="($event.target as HTMLInputElement).select()">
        <button class="btn" type="button" @click="copy">Copiar</button>
        <button class="btn gray" type="button" @click="created = null">Já copiei</button>
      </div>
    </div>

    <form class="row-center gap-8 form-row" style="flex-wrap: wrap" @submit.prevent="create">
      <input v-model="form.name" class="field" style="flex: 1 1 220px; width: auto; min-width: 0" placeholder="Para que serve este token" aria-label="Nome do token" maxlength="100" required>
      <select v-model="form.expiresInDays" class="field" style="width: 170px" aria-label="Validade">
        <option value="">Sem validade</option>
        <option value="30">Vale 30 dias</option>
        <option value="90">Vale 90 dias</option>
        <option value="365">Vale 1 ano</option>
      </select>
      <label class="check"><input v-model="form.write" type="checkbox">Permitir escrita</label>
      <button class="btn" type="submit" :disabled="saving">Criar token</button>
    </form>

    <div style="margin-top: 24px; overflow-x: auto">
      <table class="table open">
        <thead>
          <tr>
            <th style="min-width: 200px">Nome</th>
            <th>Começa com</th>
            <th>Permissão</th>
            <th>Validade</th>
            <th>Último uso</th>
            <th style="width: 1%">Ações</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="token in tokens" :key="token.id">
            <td class="medium">{{ token.name }}</td>
            <td><code class="chip">{{ token.prefix }}…</code></td>
            <td><span class="pill" :class="{ strong: writes(token) }">{{ writes(token) ? 'Leitura e escrita' : 'Só leitura' }}</span></td>
            <td style="white-space: nowrap">
              <span v-if="expired(token)" style="color: var(--danger)">expirou em {{ formatDateTime(token.expiresAt) }}</span>
              <template v-else-if="token.expiresAt">{{ formatDateTime(token.expiresAt) }}</template>
              <span v-else class="muted">sem validade</span>
            </td>
            <td style="white-space: nowrap">
              <template v-if="token.lastUsedAt">{{ timeAgo(token.lastUsedAt) }}</template>
              <span v-else class="muted">nunca</span>
            </td>
            <td>
              <button class="icon-btn danger" type="button" title="Revogar token" :aria-label="`Revogar o token ${token.name}`" @click="revoke(token)">
                <q-icon :name="icons.trash" size="18px" />
              </button>
            </td>
          </tr>
          <tr v-if="!tokens?.length" class="blank">
            <td colspan="6" style="padding: 32px 16px; text-align: center">
              <span class="muted">Você ainda não criou nenhum token. Diga acima para que ele serve e crie o primeiro.</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
