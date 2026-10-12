<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { Project } from '~/utils/types'

useHead({ title: 'Meus projetos - Taskboard' })

const api = useApi()
const { projects, refresh } = useMyProjects()
await refresh()

const mine = ref<Project[]>([])
const others = computed(() => projects.value.filter((p) => !p.my.isMember))
watchEffect(() => {
  mine.value = projects.value.filter((p) => p.my.isMember)
})

const showArchived = ref(false)
const { data: archived, refresh: refreshArchived } = await useAsyncData('archived-projects', () =>
  api<Project[]>('/projects', { query: { archived: true } }),
)

async function saveOrder() {
  await api('/projects/order', { method: 'POST', body: { ids: mine.value.map((p) => p.id) } })
  await refresh()
}

async function unarchive(project: Project) {
  await api(`/projects/${project.id}/unarchive`, { method: 'POST' })
  await Promise.all([refresh(), refreshArchived()])
}
</script>

<template>
  <div class="centered">
    <div class="title-bar" style="padding: 12px 16px">
      <h1 class="page-title" style="margin: 0; font-weight: 400">Meus Projetos</h1>
      <NuxtLink class="btn" to="/project/new" style="min-width: 170px">Novo projeto</NuxtLink>
    </div>

    <div style="display: flex; gap: 32px; align-items: flex-start">
      <div class="grow">
        <p v-if="!mine.length" class="muted" style="padding: 24px 0">Você ainda não participa de nenhum projeto.</p>
        <ClientOnly>
          <VueDraggable v-model="mine" handle=".handle" :animation="150" @end="saveOrder">
            <div v-for="project in mine" :key="project.id" class="project-line">
              <ProjectLogo :project="project" :size="48" />
              <div class="grow">
                <NuxtLink class="name" :to="`/project/${project.slug}`">{{ project.name }}</NuxtLink>
                <q-icon v-if="project.isPrivate" :name="icons.private" size="18px" style="margin-left: 8px" title="Projeto privado" />
                <div>{{ project.description }}</div>
              </div>
              <q-icon class="handle muted" :name="icons.drag" size="20px" style="cursor: grab" title="Arraste para reordenar" />
            </div>
          </VueDraggable>
          <template #fallback>
            <div v-for="project in mine" :key="project.id" class="project-line">
              <ProjectLogo :project="project" :size="48" />
              <div class="grow">
                <NuxtLink class="name" :to="`/project/${project.slug}`">{{ project.name }}</NuxtLink>
                <div>{{ project.description }}</div>
              </div>
            </div>
          </template>
        </ClientOnly>

        <template v-if="others.length">
          <h2 class="label" style="margin-top: 32px">Outros projetos que você pode ver</h2>
          <div v-for="project in others" :key="project.id" class="project-line">
            <ProjectLogo :project="project" :size="48" />
            <div class="grow">
              <NuxtLink class="name" :to="`/project/${project.slug}`">{{ project.name }}</NuxtLink>
              <div>{{ project.description }}</div>
            </div>
          </div>
        </template>

        <p v-if="archived?.length" style="margin-top: 24px">
          <a href="#" class="pointer" @click.prevent="showArchived = !showArchived">
            {{ showArchived ? 'Ocultar projetos arquivados' : `Mostrar projetos arquivados (${archived.length})` }}
          </a>
        </p>
        <template v-if="showArchived">
          <div v-for="project in archived" :key="project.id" class="project-line">
            <ProjectLogo :project="project" :size="48" />
            <div class="grow">
              <NuxtLink class="name" :to="`/project/${project.slug}`">{{ project.name }}</NuxtLink>
              <span class="chip" style="margin-left: 8px">arquivado</span>
              <div>{{ project.description }}</div>
            </div>
            <button v-if="project.my.isOwner" class="btn gray" type="button" @click="unarchive(project)">Restaurar</button>
          </div>
        </template>
      </div>

      <aside class="muted" style="flex: 0 0 200px; padding-top: 16px; font-size: 12px; line-height: 18px">
        Reorganize seus projetos para colocar os mais usados no topo. Os 4 primeiros aparecem na lista de projetos da barra
        de navegação superior.
      </aside>
    </div>
  </div>
</template>
