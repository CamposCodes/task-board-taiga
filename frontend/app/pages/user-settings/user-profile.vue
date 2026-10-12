<script setup lang="ts">
import { Notify } from 'quasar'
import type { Me } from '~/utils/types'

useHead({ title: 'Configurações de usuário - Taskboard' })

const api = useApi()
const me = useMe()
const form = reactive({ fullName: me.value!.fullName, bio: me.value!.bio })
const saving = ref(false)

async function save() {
  saving.value = true
  try {
    me.value = await api<Me>('/me', { method: 'PATCH', body: form })
    Notify.create({ type: 'positive', message: 'Perfil salvo.' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="page-title">Configurações de usuário</h1>
    <form style="display: flex; gap: 24px; align-items: flex-start" @submit.prevent="save">
      <UserAvatar :user="{ username: me!.username, fullName: form.fullName }" :size="110" />
      <div style="width: min(420px, 100%)">
        <div class="form-row">
          <label for="username">Nome do usuário</label>
          <input id="username" class="field" :value="me!.username" disabled>
        </div>
        <div class="form-row">
          <label for="email">E-mail</label>
          <input id="email" class="field" :value="me!.email" disabled>
          <p class="muted" style="margin-top: 4px; font-size: 12px">
            {{ me!.isSso ? 'Nome de usuário e e-mail vêm do login da empresa.' : 'Nome de usuário e e-mail são alterados por um administrador do sistema.' }}
          </p>
        </div>
        <div class="form-row">
          <label for="full-name">Nome completo</label>
          <input id="full-name" v-model="form.fullName" class="field" maxlength="200" required>
        </div>
        <div class="form-row">
          <label for="bio">Bio (máx. 210 caracteres)</label>
          <textarea id="bio" v-model="form.bio" class="field" maxlength="210" />
        </div>
        <button class="btn" type="submit" :disabled="saving">Salvar</button>
      </div>
    </form>
  </div>
</template>
