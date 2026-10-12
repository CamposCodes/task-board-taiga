<script setup lang="ts">
import { Notify } from 'quasar'

useHead({ title: 'Alterar senha - Taskboard' })

const api = useApi()
const me = useMe()
const form = reactive({ currentPassword: '', newPassword: '', repeat: '' })
const saving = ref(false)

async function save() {
  if (form.newPassword !== form.repeat) {
    Notify.create({ type: 'negative', message: 'A confirmação não é igual à nova senha.' })
    return
  }
  saving.value = true
  try {
    await api('/me/password', { method: 'POST', body: { currentPassword: form.currentPassword, newPassword: form.newPassword } })
    form.currentPassword = form.newPassword = form.repeat = ''
    Notify.create({ type: 'positive', message: 'Senha alterada. As outras sessões foram encerradas.' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div style="max-width: 420px">
    <h1 class="page-title">Alterar senha</h1>
    <p v-if="me?.isSso" class="muted">Sua conta entra pelo login da empresa. A senha é trocada lá, não aqui.</p>
    <form v-else @submit.prevent="save">
      <div class="form-row">
        <label for="current">Senha atual</label>
        <input id="current" v-model="form.currentPassword" class="field" type="password" autocomplete="current-password" required>
      </div>
      <div class="form-row">
        <label for="new">Nova senha</label>
        <input id="new" v-model="form.newPassword" class="field" type="password" autocomplete="new-password" minlength="8" required>
      </div>
      <div class="form-row">
        <label for="repeat">Confirme a nova senha</label>
        <input id="repeat" v-model="form.repeat" class="field" type="password" autocomplete="new-password" minlength="8" required>
      </div>
      <button class="btn" type="submit" :disabled="saving">Salvar</button>
    </form>
  </div>
</template>
