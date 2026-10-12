<script setup lang="ts">
const { project } = useProject()
useHead({ title: 'Configurações' })

const base = computed(() => `/project/${project.value.slug}/admin`)
const GROUPS = [
  {
    title: 'Projeto',
    links: [
      { to: 'project-profile/details', label: 'Detalhes do projeto', icon: icons.details },
      { to: 'project-profile/modules', label: 'Módulos', icon: icons.modules },
    ],
  },
  {
    title: 'Atributos',
    links: [
      { to: 'project-values/status', label: 'Status', icon: icons.statuses },
      { to: 'project-values/points', label: 'Pontos', icon: icons.points },
      { to: 'project-values/priorities', label: 'Prioridades', icon: icons.priorities },
      { to: 'project-values/severities', label: 'Gravidades', icon: icons.severities },
      { to: 'project-values/types', label: 'Tipos', icon: icons.types },
      { to: 'project-values/tags', label: 'Tags', icon: icons.tags },
    ],
  },
  { title: 'Membros', links: [{ to: 'memberships', label: 'Membros', icon: icons.team }] },
  { title: 'Permissões', links: [{ to: 'roles', label: 'Papéis', icon: icons.roles }] },
]
</script>

<template>
  <div v-if="project.my.isAdmin" style="display: flex">
    <aside class="side-nav">
      <template v-for="group in GROUPS" :key="group.title">
        <div class="group">{{ group.title }}</div>
        <NuxtLink v-for="link in group.links" :key="link.to" class="item" :to="`${base}/${link.to}`"><q-icon :name="link.icon" size="18px" />{{ link.label }}</NuxtLink>
      </template>
    </aside>
    <section class="section grow" style="max-width: 1000px">
      <p v-if="project.isArchived" class="blocked-banner" style="margin: 0 0 16px">
        Projeto arquivado: somente leitura. O dono pode restaurá-lo em "Detalhes do projeto".
      </p>
      <NuxtPage />
    </section>
  </div>
  <div v-else class="empty">
    <p class="medium">Só administradores do projeto podem ver as configurações.</p>
  </div>
</template>
