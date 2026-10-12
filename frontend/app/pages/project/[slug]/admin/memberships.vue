<script setup lang="ts">
import type { Member } from '~/utils/types'

const api = useApi()
const { project, refresh } = useProject()
const base = `/projects/${project.value.id}/members`
const locked = computed(() => project.value.isArchived)

const form = reactive({ user: '', roleId: project.value.roles[0]?.id ?? 0 })

async function add() {
  await api(base, { method: 'POST', body: { user: form.user, roleId: form.roleId } })
  form.user = ''
  await refresh()
}

async function change(member: Member, body: Record<string, unknown>) {
  try {
    await api(`${base}/${member.userId}`, { method: 'PATCH', body })
  } finally {
    await refresh()
  }
}

async function remove(member: Member) {
  if (await confirmDelete(`${member.fullName} do projeto`)) {
    await api(`${base}/${member.userId}`, { method: 'DELETE' })
    await refresh()
  }
}
</script>

<template>
  <div>
    <h1 class="page-title">Membros</h1>

    <form v-if="!locked" class="row-center gap-8 form-row" style="max-width: 720px" @submit.prevent="add">
      <input v-model="form.user" class="field grow" placeholder="Nome de usuário ou e-mail de quem já tem conta" aria-label="Nome de usuário ou e-mail" required>
      <select v-model="form.roleId" class="field" style="width: 200px" aria-label="Papel do novo membro">
        <option v-for="role in project.roles" :key="role.id" :value="role.id">{{ role.name }}</option>
      </select>
      <button class="btn" type="submit"><q-icon :name="icons.add" size="16px" />Novo membro</button>
    </form>

    <table class="table">
      <thead>
        <tr>
          <th>Membro</th>
          <th style="width: 90px; text-align: center">Admin</th>
          <th style="width: 220px">Papel</th>
          <th style="width: 40px" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="member in project.members" :key="member.userId">
          <td>
            <div class="row-center gap-16">
              <UserAvatar :user="member" :size="40" />
              <div>
                <span class="medium">{{ member.fullName }}</span>
                <span v-if="member.isOwner" class="chip" style="margin-left: 8px">dono</span>
                <div class="muted">{{ member.email }}</div>
              </div>
            </div>
          </td>
          <td style="text-align: center">
            <button type="button"
              class="switch"
              :class="{ on: member.isAdmin }"
              role="switch"
              :aria-checked="member.isAdmin"
              :aria-label="`${member.fullName} administra o projeto`"
              :title="member.isOwner ? 'O dono é sempre administrador' : 'Administradores mudam as configurações e têm todas as permissões'"
              @click="!locked && !member.isOwner && change(member, { isAdmin: !member.isAdmin })"
            >
              <i />
            </button>
          </td>
          <td>
            <select
              class="field"
              :value="member.roleId"
              :aria-label="`Papel de ${member.fullName}`"
              :disabled="locked"
              @change="change(member, { roleId: Number(($event.target as HTMLSelectElement).value) })"
            >
              <option v-for="role in project.roles" :key="role.id" :value="role.id">{{ role.name }}</option>
            </select>
          </td>
          <td>
            <button v-if="!member.isOwner && !locked" class="icon-btn" type="button" title="Remover do projeto" @click="remove(member)">
              <q-icon :name="icons.trash" size="16px" />
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
