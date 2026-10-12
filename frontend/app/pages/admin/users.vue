<script setup lang="ts">
import type { AdminUser, Page } from '~/utils/types'

useHead({ title: 'Usuários do sistema - Taskboard' })

const api = useApi()
const me = useMe()
const search = ref('')
const { data, refresh } = await useAsyncData(
  'admin-users',
  () => (me.value?.isSuperuser ? api<Page<AdminUser>>('/users', { query: { q: search.value, size: 200 } }) : Promise.resolve(null)),
  { watch: [search] },
)

const adding = ref(false)
const form = reactive({ username: '', email: '', fullName: '', password: '', isSuperuser: false })
const saving = ref(false)

async function create() {
  saving.value = true
  try {
    await api('/users', { method: 'POST', body: form })
    Object.assign(form, { username: '', email: '', fullName: '', password: '', isSuperuser: false })
    adding.value = false
    await refresh()
  } finally {
    saving.value = false
  }
}

async function change(user: AdminUser, body: Record<string, unknown>) {
  try {
    await api(`/users/${user.id}`, { method: 'PATCH', body })
  } finally {
    await refresh()
  }
}

async function resetPassword(user: AdminUser) {
  const password = await askText('Nova senha', `Defina uma nova senha para ${user.fullName} (mínimo de 8 caracteres).`)
  if (password) {
    await change(user, { password })
  }
}
</script>

<template>
  <div class="centered">
    <template v-if="me?.isSuperuser">
      <div class="title-bar" style="margin-bottom: 16px; padding: 12px 16px">
        <h1 class="page-title" style="margin: 0; font-weight: 400">Usuários do sistema</h1>
        <div class="row-center gap-8">
          <div class="search-box">
            <input v-model="search" class="field" type="search" placeholder="Procurar" aria-label="Procurar usuários">
            <q-icon :name="icons.search" size="16px" />
          </div>
          <button class="btn" type="button" @click="adding = true"><q-icon :name="icons.add" size="16px" />Novo usuário</button>
        </div>
      </div>

      <table class="table">
        <thead>
          <tr>
            <th>Pessoa</th>
            <th>E-mail</th>
            <th style="width: 130px; text-align: center">Admin do sistema</th>
            <th style="width: 90px; text-align: center">Ativo</th>
            <th style="width: 40px" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in data?.items" :key="user.id">
            <td>
              <div class="row-center gap-16">
                <UserAvatar :user="user" :size="36" />
                <div>
                  <NuxtLink class="medium" :to="`/profile/${user.username}`">{{ user.fullName }}</NuxtLink>
                  <div class="muted">@{{ user.username }}</div>
                </div>
              </div>
            </td>
            <td>{{ user.email }}</td>
            <td style="text-align: center">
              <button type="button" class="switch" :class="{ on: user.isSuperuser }" role="switch" :aria-checked="user.isSuperuser" :aria-label="`${user.fullName} administra o sistema`" @click="user.id !== me?.id && change(user, { isSuperuser: !user.isSuperuser })"><i /></button>
            </td>
            <td style="text-align: center">
              <button type="button" class="switch" :class="{ on: user.isActive }" role="switch" :aria-checked="user.isActive" :aria-label="`${user.fullName} pode entrar`" @click="user.id !== me?.id && change(user, { isActive: !user.isActive })"><i /></button>
            </td>
            <td>
              <button class="icon-btn" type="button" title="Definir nova senha" @click="resetPassword(user)"><q-icon :name="icons.lock" size="16px" /></button>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
    <div v-else class="empty"><p class="medium">Só administradores do sistema podem ver esta página.</p></div>

    <AppLightbox v-if="adding" title="Novo usuário" narrow @close="adding = false">
      <form @submit.prevent="create">
        <div class="form-row"><input v-model="form.fullName" class="field" placeholder="Nome completo" aria-label="Nome completo" maxlength="200" required v-focus></div>
        <div class="form-row"><input v-model="form.username" class="field" placeholder="Nome de usuário" aria-label="Nome de usuário" maxlength="60" pattern="[A-Za-z0-9._\-]+" required></div>
        <div class="form-row"><input v-model="form.email" class="field" type="email" placeholder="E-mail" aria-label="E-mail" maxlength="200" required></div>
        <div class="form-row"><input v-model="form.password" class="field" type="password" placeholder="Senha inicial (mínimo de 8 caracteres)" aria-label="Senha inicial" minlength="8" autocomplete="new-password" required></div>
        <div class="form-row">
          <button type="button" class="switch" :class="{ on: form.isSuperuser }" role="switch" :aria-checked="form.isSuperuser" @click="form.isSuperuser = !form.isSuperuser"><i />Administrador do sistema</button>
        </div>
        <p class="form-row muted" style="font-size: 12px">Quem entra pelo login da empresa ganha a conta sozinho no primeiro acesso.</p>
        <button class="btn big" type="submit" :disabled="saving">Criar</button>
      </form>
    </AppLightbox>
  </div>
</template>
