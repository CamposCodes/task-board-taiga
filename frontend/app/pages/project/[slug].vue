<script setup lang="ts">
import type { ProjectDetail } from '~/utils/types'

const route = useRoute()
const api = useApi()
const slug = computed(() => String(route.params.slug))

const { data, refresh } = await useAsyncData(
  () => `project:${slug.value}`,
  () => api<ProjectDetail>(`/projects/${slug.value}`, QUIET).catch(() => null),
  { watch: [slug] },
)
if (!data.value) {
  throw createError({ statusCode: 404, statusMessage: 'Projeto não encontrado', fatal: true })
}
const project = data as Ref<ProjectDetail>

provide(PROJECT_KEY, {
  project,
  refresh: async () => {
    await refresh()
  },
  can: (permission) => project.value.my.permissions.includes(permission),
})

useHead({ titleTemplate: (title) => (title ? `${title} - ${project.value.name}` : project.value.name) })
</script>

<template>
  <div class="project-shell">
    <ProjectMenu />
    <div class="project-main">
      <NuxtPage />
    </div>
  </div>
</template>
