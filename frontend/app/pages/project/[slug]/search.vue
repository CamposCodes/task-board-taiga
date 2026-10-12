<script setup lang="ts">
import type { Item } from '~/utils/types'

interface Found {
  userstories: Item[]
  tasks: Item[]
  issues: Item[]
  wikiPages: string[]
}

useHead({ title: 'Buscar' })

const route = useRoute()
const api = useApi()
const { project } = useProject()
const slug = project.value.slug

const text = ref(typeof route.query.text === 'string' ? route.query.text : '')
const { data, refresh } = await useAsyncData(`search:${slug}`, () =>
  api<Found>(`/projects/${slug}/search`, { query: { q: text.value } }),
)

let pending: ReturnType<typeof setTimeout> | undefined
watch(text, () => {
  clearTimeout(pending)
  pending = setTimeout(() => {
    navigateTo({ query: text.value ? { text: text.value } : {} }, { replace: true })
    refresh()
  }, 300)
})

type Tab = 'userstories' | 'issues' | 'tasks' | 'wikiPages'
const tab = ref<Tab>('userstories')
const TABS: { key: Tab; label: string; icon: string }[] = [
  { key: 'userstories', label: 'Histórias de Usuários', icon: icons.bulk },
  { key: 'issues', label: 'Problemas', icon: icons.issues },
  { key: 'tasks', label: 'Tarefas', icon: icons.list },
  { key: 'wikiPages', label: 'Páginas Wiki', icon: icons.wiki },
]
const count = (key: Tab) => data.value?.[key].length ?? 0
const items = computed<Item[]>(() => (tab.value === 'wikiPages' ? [] : (data.value?.[tab.value] ?? [])))
</script>

<template>
  <div style="display: flex">
    <aside class="side-nav">
      <div class="slot">
        <div class="search-box" style="width: 100%">
          <input v-model="text" class="field" type="search" placeholder="Procurar em..." aria-label="Procurar no projeto" v-focus>
          <q-icon :name="icons.search" size="16px" />
        </div>
      </div>
    </aside>

    <section class="section grow">
      <h1 class="page-title">Busca</h1>
      <nav class="row-center" style="border-top: 1px solid var(--line); border-bottom: 1px solid var(--line)">
        <a href="#"
          v-for="entry in TABS"
          :key="entry.key"
          class="pointer row-center gap-8"
          style="padding: 16px 20px; font-size: 16px"
          :style="{
            color: tab === entry.key ? 'var(--text)' : count(entry.key) ? 'var(--text-2)' : 'var(--field-border)',
            borderLeft: tab === entry.key ? '1px solid var(--text)' : '1px solid transparent',
            borderRight: tab === entry.key ? '1px solid var(--text)' : '1px solid transparent',
          }"
          @click.prevent="tab = entry.key"
        >
          <q-icon :name="entry.icon" size="16px" />
          {{ count(entry.key) }} {{ entry.label }}
        </a>
      </nav>

      <template v-if="tab === 'wikiPages'">
        <p v-for="page in data?.wikiPages" :key="page" style="padding: 12px 8px; border-bottom: 1px solid var(--line)">
          <NuxtLink :to="`/project/${slug}/wiki/${page}`">{{ page }}</NuxtLink>
        </p>
      </template>
      <table v-else class="table">
        <thead>
          <tr>
            <th>{{ TABS.find((t) => t.key === tab)!.label }}</th>
            <th v-if="tab === 'userstories'" style="width: 180px; text-align: center">Sprint</th>
            <th style="width: 200px; text-align: center">Status</th>
            <th v-if="tab === 'userstories'" style="width: 100px; text-align: center">Pontos</th>
            <th v-else style="width: 220px">Atribuído a</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td>
              <NuxtLink :to="itemPath(slug, item)" style="color: var(--text)"><span class="teal" style="margin-right: 8px">#{{ item.ref }}</span>{{ item.subject }}</NuxtLink>
            </td>
            <td v-if="tab === 'userstories'" style="text-align: center">
              <NuxtLink v-if="item.milestone" :to="`/project/${slug}/taskboard/${item.milestone.slug}`">{{ item.milestone.name }}</NuxtLink>
            </td>
            <td style="text-align: center">{{ item.status.name }}</td>
            <td v-if="tab === 'userstories'" style="text-align: center">{{ item.points === null ? '' : pointsLabel(project.points, item.points) }}</td>
            <td v-else>
              <span v-if="item.assignedTo" class="row-center gap-8"><UserAvatar :user="item.assignedTo" :size="24" />{{ item.assignedTo.fullName }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!count(tab)" class="muted" style="padding: 32px; text-align: center">Nada encontrado.</p>
    </section>
  </div>
</template>
