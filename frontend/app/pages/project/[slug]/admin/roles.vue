<script setup lang="ts">
import type { Role } from '~/utils/types'

const api = useApi()
const { project, refresh } = useProject()
const locked = computed(() => project.value.isArchived)

const selectedId = ref(project.value.roles[0]?.id ?? 0)
const role = computed(() => project.value.roles.find((r) => r.id === selectedId.value) ?? project.value.roles[0])

const ACTIONS: Record<string, string> = { view: 'Ver', add: 'Criar', modify: 'Alterar', comment: 'Comentar', delete: 'Excluir' }
const SECTIONS = [
  { title: 'Sprints', permissions: ['view_milestones', 'add_milestone', 'modify_milestone', 'delete_milestone'] },
  { title: 'Histórias de usuário', permissions: ['view_us', 'add_us', 'modify_us', 'comment_us', 'delete_us'] },
  { title: 'Tarefas', permissions: ['view_tasks', 'add_task', 'modify_task', 'comment_task', 'delete_task'] },
  { title: 'Problemas', permissions: ['view_issues', 'add_issue', 'modify_issue', 'comment_issue', 'delete_issue'] },
  { title: 'Wiki', permissions: ['view_wiki_pages', 'add_wiki_page', 'modify_wiki_page', 'delete_wiki_page'] },
]
const label = (permission: string) => ACTIONS[permission.split('_')[0]!]

async function update(target: Role, body: Record<string, unknown>) {
  try {
    await api(`/roles/${target.id}`, { method: 'PATCH', body })
  } finally {
    await refresh()
  }
}

function toggle(target: Role, permission: string) {
  const has = target.permissions.includes(permission)
  return update(target, { permissions: has ? target.permissions.filter((p) => p !== permission) : [...target.permissions, permission] })
}

async function rename(target: Role, name: string) {
  if (name.trim() && name.trim() !== target.name) {
    await update(target, { name: name.trim() })
  }
}

async function add() {
  const name = await askText('Novo papel', 'Qual é o nome do papel? Ele começa só com as permissões de ver.')
  if (name?.trim()) {
    const created = await api<Role>(`/projects/${project.value.id}/roles`, { method: 'POST', body: { name: name.trim() } })
    await refresh()
    selectedId.value = created.id
  }
}

async function remove(target: Role) {
  if (await confirmDelete(`o papel "${target.name}"`)) {
    await api(`/roles/${target.id}`, { method: 'DELETE' })
    await refresh()
    selectedId.value = project.value.roles[0]?.id ?? 0
  }
}
</script>

<template>
  <div>
    <h1 class="page-title">Papéis</h1>
    <div style="display: flex; gap: 24px; align-items: flex-start">
      <div style="flex: 0 0 240px">
        <a href="#"
          v-for="entry in project.roles"
          :key="entry.id"
          class="pointer row-center"
          style="justify-content: space-between; padding: 12px; border-bottom: 1px solid var(--fill)"
          :style="entry.id === role?.id ? { background: 'var(--brand-soft)', color: 'var(--brand-deep)' } : { color: 'var(--text)' }"
          :aria-current="entry.id === role?.id || undefined"
          @click.prevent="selectedId = entry.id"
        >
          <span class="medium grow">{{ entry.name }}</span>
          <q-icon v-if="entry.followsAll" :name="icons.mail" size="14px" style="margin-right: 8px" title="Recebe e-mail de toda movimentação do projeto" />
          <span class="chip" :title="`${entry.membersCount} membros com este papel`">{{ entry.membersCount }}</span>
        </a>
        <button v-if="!locked" class="btn soft" type="button" style="margin-top: 12px" @click="add"><q-icon :name="icons.add" size="16px" />Novo papel</button>
      </div>

      <div v-if="role" class="grow">
        <div class="row-center gap-8 form-row">
          <input
            :key="role.id"
            class="field grow"
            maxlength="60"
            :value="role.name"
            aria-label="Nome do papel"
            :disabled="locked"
            @change="rename(role, ($event.target as HTMLInputElement).value)"
          >
          <button v-if="!locked" class="btn gray" type="button" :disabled="role.membersCount > 0" :title="role.membersCount ? 'Troque o papel dos membros antes de excluir' : ''" @click="remove(role)">
            <q-icon :name="icons.trash" size="16px" />Excluir
          </button>
        </div>
        <p class="form-row muted">
          Quem pode criar, alterar ou comentar também pode ver. Administradores do projeto têm todas as permissões, seja qual
          for o papel.
        </p>
        <section v-for="section in SECTIONS" :key="section.title" style="margin-bottom: 16px">
          <h2 class="title-bar">{{ section.title }}</h2>
          <div class="row-center" style="flex-wrap: wrap; gap: 8px 24px; padding: 12px 16px">
            <label v-for="permission in section.permissions" :key="permission" class="row-center gap-4 pointer">
              <input type="checkbox" :checked="role.permissions.includes(permission)" :disabled="locked" @change="toggle(role, permission)">
              {{ label(permission) }}
            </label>
          </div>
        </section>
        <section style="margin-bottom: 16px">
          <h2 class="title-bar">Avisos por e-mail</h2>
          <div style="padding: 12px 16px">
            <label class="row-center gap-4 pointer">
              <input type="checkbox" :checked="role.followsAll" :disabled="locked" @change="update(role, { followsAll: !role.followsAll })">
              Receber e-mail de toda movimentação do projeto
            </label>
            <p class="muted" style="margin-top: 4px">
              Sem esta opção, quem tem o papel recebe e-mail só do que está atribuído a ele, do que observa e do que criou.
            </p>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
