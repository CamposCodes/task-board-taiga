<script setup lang="ts">
const api = useApi()
const { project, refresh } = useProject()

type Flag = 'isBacklogActivated' | 'isKanbanActivated' | 'isIssuesActivated' | 'isWikiActivated'
const MODULES: { flag: Flag; name: string; icon: string; text: string }[] = [
  { flag: 'isBacklogActivated', name: 'Scrum', icon: icons.scrum, text: 'Backlog priorizado, sprints e quadro de tarefas de cada sprint.' },
  { flag: 'isKanbanActivated', name: 'Kanban', icon: icons.kanban, text: 'Quadro com uma coluna por status das histórias de usuário.' },
  { flag: 'isIssuesActivated', name: 'Problemas', icon: icons.issues, text: 'Registro de bugs, perguntas e melhorias, com tipo, gravidade e prioridade.' },
  { flag: 'isWikiActivated', name: 'Wiki', icon: icons.wiki, text: 'Páginas de documentação do projeto.' },
]

async function toggle(flag: Flag) {
  await api(`/projects/${project.value.id}`, { method: 'PATCH', body: { [flag]: !project.value[flag] } })
  await refresh()
}
</script>

<template>
  <div style="max-width: 640px">
    <h1 class="page-title">Módulos</h1>
    <p class="form-row muted">Desligar um módulo só o tira do menu: nada do que foi criado nele é apagado.</p>
    <div v-for="entry in MODULES" :key="entry.flag" class="row-center gap-16" style="padding: 16px 0; border-bottom: 1px solid var(--line)">
      <q-icon :name="entry.icon" size="32px" :style="{ color: project[entry.flag] ? 'var(--brand)' : 'var(--field-border)' }" />
      <div class="grow">
        <div class="medium" style="font-size: 16px">{{ entry.name }}</div>
        <div class="muted">{{ entry.text }}</div>
      </div>
      <button type="button"
        class="switch"
        :class="{ on: project[entry.flag] }"
        role="switch"
        :aria-checked="project[entry.flag]"
        :aria-label="`Módulo ${entry.name}`"
        @click="!project.isArchived && toggle(entry.flag)"
      >
        <i />
        {{ project[entry.flag] ? 'Ligado' : 'Desligado' }}
      </button>
    </div>
  </div>
</template>
