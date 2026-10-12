<script setup lang="ts">
import type { DashItem } from '~/utils/types'

useHead({ title: 'Início - Taskboard' })

const api = useApi()
const { projects } = useMyProjects()
const { data } = await useAsyncData('dashboard', () =>
  api<{ workingOn: DashItem[]; watching: DashItem[] }>('/me/dashboard'),
)
const mine = computed(() => projects.value.filter((p) => p.my.isMember).slice(0, 4))
const columns = computed(() => [
  { title: 'Trabalhando em', items: data.value?.workingOn ?? [], empty: 'Nada atribuído a você no momento.' },
  { title: 'Observando', items: data.value?.watching ?? [], empty: 'Você não observa nenhum item.' },
])
</script>

<template>
  <div class="centered">
    <h1 class="page-title">Painel de Projetos</h1>
    <div style="display: flex; gap: 24px; align-items: flex-start; flex-wrap: wrap">
      <section v-for="column in columns" :key="column.title" class="grow" style="min-width: 280px">
        <h2 class="title-bar" style="font-weight: 400">{{ column.title }}</h2>
        <p v-if="!column.items.length" style="padding: 16px 8px; text-align: center">{{ column.empty }}</p>
        <div v-for="item in column.items" :key="item.id" class="row-center gap-16" style="padding: 12px 8px; border-bottom: 1px solid var(--line); background: var(--surface)">
          <ProjectLogo :project="item.project" :size="40" />
          <div class="grow">
            <div>
              <span class="muted">{{ item.project.name }}</span>
              <span style="margin-left: 4px; text-transform: uppercase">{{ kindLabels[item.type] }}</span>
            </div>
            <div class="status-text" :style="{ '--status': item.status.color }">{{ item.status.name }}</div>
            <NuxtLink :to="itemPath(item.project.slug, item)" style="color: var(--text)">
              <span class="teal">#{{ item.ref }}</span> {{ item.subject }}
            </NuxtLink>
          </div>
        </div>
      </section>

      <aside style="flex: 0 0 240px">
        <NuxtLink
          v-for="project in mine"
          :key="project.id"
          :to="`/project/${project.slug}`"
          style="display: block; margin-bottom: 12px; padding: 12px; border: 1px solid var(--line); background: var(--surface); color: var(--text)"
        >
          <div class="row-center gap-8">
            <ProjectLogo :project="project" :size="40" />
            <span style="font-size: 16px">{{ project.name }}</span>
          </div>
          <p class="clip" style="margin: 8px 0; font-size: 12px">{{ project.description || '&nbsp;' }}</p>
          <div class="row-center gap-8 muted" style="font-size: 12px">
            <span class="row-center gap-4"><q-icon :name="icons.like" size="14px" />{{ project.likes }}</span>
            <span class="row-center gap-4"><q-icon :name="icons.watch" size="14px" />{{ project.watchers }}</span>
            <span class="row-center gap-4"><q-icon :name="icons.team" size="14px" />{{ project.membersCount }}</span>
            <q-icon v-if="project.isPrivate" :name="icons.private" size="14px" title="Projeto privado" />
          </div>
        </NuxtLink>
        <NuxtLink class="btn big" to="/projects/">Gerenciar projetos</NuxtLink>
      </aside>
    </div>
  </div>
</template>
