<script setup lang="ts">
import type { Member } from '~/utils/types'

interface MemberStats {
  userId: number
  closedBugs: number
  iocaineTasks: number
  wikiChanges: number
  createdBugs: number
  closedTasks: number
}

useHead({ title: 'Time' })

const api = useApi()
const me = useMe()
const { project } = useProject()
const { refresh: refreshList } = useMyProjects()
const slug = project.value.slug

const { data: stats } = await useAsyncData(`team:${slug}`, () => api<MemberStats[]>(`/projects/${slug}/member-stats`))
const statsOf = (userId: number) => stats.value?.find((s) => s.userId === userId)

const COLUMNS: { key: keyof Omit<MemberStats, 'userId'>; label: string; icon: string; hint: string }[] = [
  { key: 'closedBugs', label: 'Problemas fechados', icon: icons.clientRequirement, hint: 'Problemas fechados que estavam com a pessoa' },
  { key: 'iocaineTasks', label: 'Doses de iocaína', icon: icons.iocaine, hint: 'Tarefas penosas que a pessoa tomou para si' },
  { key: 'wikiChanges', label: 'Edições da wiki', icon: icons.edit, hint: 'Vezes em que a pessoa escreveu na wiki' },
  { key: 'createdBugs', label: 'Problemas criados', icon: icons.issues, hint: 'Problemas que a pessoa reportou' },
  { key: 'closedTasks', label: 'Tarefas fechadas', icon: icons.check, hint: 'Tarefas fechadas que estavam com a pessoa' },
]
const total = (userId: number) => {
  const s = statsOf(userId)
  return s ? s.closedBugs + s.iocaineTasks + s.wikiChanges + s.createdBugs + s.closedTasks : 0
}

const search = ref('')
const roleId = ref<number | null>(null)
const myself = computed(() => project.value.members.find((m) => m.userId === me.value?.id))
const others = computed(() => {
  const text = search.value.trim().toLowerCase()
  return project.value.members.filter(
    (m) => m.userId !== me.value?.id && (roleId.value === null || m.roleId === roleId.value) && (!text || m.fullName.toLowerCase().includes(text)),
  )
})
const roleName = computed(() => project.value.roles.find((r) => r.id === roleId.value)?.name ?? 'Tudo')

async function leave(member: Member) {
  if (await confirmDelete('a sua participação neste projeto')) {
    await api(`/projects/${slug}/members/${member.userId}`, { method: 'DELETE' })
    await refreshList()
    await navigateTo('/projects/')
  }
}
</script>

<template>
  <div style="display: flex">
    <aside class="side-nav">
      <div class="group">Filtros</div>
      <div class="slot">
        <div class="search-box" style="width: 100%">
          <input v-model="search" class="field" type="search" placeholder="Procurar por nome" aria-label="Procurar pelo nome completo">
          <q-icon :name="icons.search" size="16px" />
        </div>
      </div>
      <button class="item" type="button" :aria-pressed="roleId === null" @click="roleId = null">
        Tudo
        <span class="count">{{ project.members.length }}</span>
      </button>
      <button v-for="role in project.roles" :key="role.id" class="item" type="button" :aria-pressed="roleId === role.id" @click="roleId = role.id">
        {{ role.name }}
        <span class="count">{{ project.members.filter((m) => m.roleId === role.id).length }}</span>
      </button>
    </aside>

    <section class="section grow">
      <h1 class="page-title">Time</h1>
      <table class="table">
        <thead>
          <tr>
            <th />
            <th v-for="column in COLUMNS" :key="column.key" style="width: 13%; text-align: center; font-weight: 400" :title="column.hint">{{ column.label }}</th>
            <th style="width: 10%; text-align: center; font-weight: 400">Total</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="myself">
            <td style="padding: 24px 8px">
              <div class="row-center gap-16">
                <UserAvatar :user="myself" :size="66" />
                <div>
                  <div class="medium" style="font-size: 16px">{{ myself.fullName }}</div>
                  <div class="muted" style="font-size: 16px">{{ myself.roleName }}</div>
                  <a href="#" v-if="!myself.isOwner" class="pointer row-center gap-4" style="color: var(--danger)" @click.prevent="leave(myself)">
                    <q-icon :name="icons.close" size="13px" />Deixar este projeto
                  </a>
                </div>
              </div>
            </td>
            <td v-for="column in COLUMNS" :key="column.key" style="text-align: center">
              <q-icon :name="column.icon" size="28px" :style="{ opacity: statsOf(myself.userId)?.[column.key] ? 1 : 0.15 }" :title="`${statsOf(myself.userId)?.[column.key] ?? 0}`" />
            </td>
            <td style="text-align: center; font-size: 22px">{{ total(myself.userId) }}</td>
          </tr>
        </tbody>
      </table>

      <h2 style="margin: 16px 0; font-size: 22.4px">Equipe &gt; <span class="teal">{{ roleName }}</span></h2>
      <table class="table">
        <tbody>
          <tr v-for="member in others" :key="member.userId">
            <td>
              <div class="row-center gap-16">
                <UserAvatar :user="member" :size="66" square />
                <div>
                  <NuxtLink class="medium" style="font-size: 16px" :to="`/profile/${member.username}`">{{ member.fullName }}</NuxtLink>
                  <span v-if="member.isAdmin" class="chip" style="margin-left: 8px" :title="member.isOwner ? 'Dono do projeto' : 'Administrador do projeto'">
                    {{ member.isOwner ? 'dono' : 'admin' }}
                  </span>
                  <div class="muted" style="font-size: 16px">{{ member.roleName }}</div>
                </div>
              </div>
            </td>
            <td v-for="column in COLUMNS" :key="column.key" style="width: 13%; text-align: center">
              <q-icon :name="column.icon" size="28px" :style="{ opacity: statsOf(member.userId)?.[column.key] ? 1 : 0.15 }" :title="`${column.label}: ${statsOf(member.userId)?.[column.key] ?? 0}`" />
            </td>
            <td style="width: 10%; text-align: center; font-size: 22px">{{ total(member.userId) }}</td>
          </tr>
          <tr v-if="!others.length">
            <td class="muted" style="padding: 24px; text-align: center">Ninguém mais por aqui com esses filtros.</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
