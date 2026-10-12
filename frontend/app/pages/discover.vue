<script setup lang="ts">
useHead({ title: 'Descobrir projetos - Taskboard' })

const { projects, refresh } = useMyProjects()
await refresh()

const search = ref('')
const shown = computed(() => {
  const text = search.value.trim().toLowerCase()
  return projects.value
    .filter((p) => !p.isPrivate && (!text || p.name.toLowerCase().includes(text) || p.description.toLowerCase().includes(text)))
    .sort((a, b) => b.likes - a.likes || a.name.localeCompare(b.name))
})
</script>

<template>
  <div class="centered">
    <div style="padding: 32px 0; text-align: center">
      <h1 class="page-title">Descobrir projetos</h1>
      <div class="search-box" style="width: min(480px, 100%); margin: 0 auto">
        <input v-model="search" class="field" type="search" placeholder="Procurar projetos públicos" aria-label="Procurar projetos públicos">
        <q-icon :name="icons.search" size="16px" />
      </div>
    </div>
    <p v-if="!shown.length" class="muted" style="text-align: center">Nenhum projeto público {{ search ? 'com esse nome' : 'por enquanto' }}.</p>
    <div v-for="project in shown" :key="project.id" class="project-line">
      <ProjectLogo :project="project" :size="48" />
      <div class="grow">
        <NuxtLink class="name" :to="`/project/${project.slug}`">{{ project.name }}</NuxtLink>
        <div>{{ project.description }}</div>
      </div>
      <div class="row-center gap-16 muted">
        <span class="row-center gap-4" title="Curtidas"><q-icon :name="icons.like" size="16px" />{{ project.likes }}</span>
        <span class="row-center gap-4" title="Observadores"><q-icon :name="icons.watch" size="16px" />{{ project.watchers }}</span>
        <span class="row-center gap-4" title="Membros"><q-icon :name="icons.team" size="16px" />{{ project.membersCount }}</span>
      </div>
    </div>
  </div>
</template>
