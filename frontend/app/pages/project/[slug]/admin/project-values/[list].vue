<script setup lang="ts">
const route = useRoute()
const api = useApi()
const { project, refresh } = useProject()
const list = computed(() => String(route.params.list))

const TITLES: Record<string, string> = {
  status: 'Status',
  points: 'Pontos',
  priorities: 'Prioridades',
  severities: 'Gravidades',
  types: 'Tipos',
  tags: 'Tags',
}
if (!TITLES[list.value]) {
  throw createError({ statusCode: 404, statusMessage: 'Página não encontrada', fatal: true })
}

const statusesOf = (kind: string) => project.value.statuses.filter((s) => s.kind === kind)

const tags = computed(() => Object.entries(project.value.tagsColors).sort(([a], [b]) => a.localeCompare(b)))
const newTag = reactive({ name: '', color: '#006557' })

async function saveTags(colors: Record<string, string>) {
  await api(`/projects/${project.value.id}`, { method: 'PATCH', body: { tagsColors: colors } })
  await refresh()
}

async function addTag() {
  await saveTags({ ...project.value.tagsColors, [newTag.name.trim().toLowerCase()]: newTag.color })
  newTag.name = ''
}

async function removeTag(name: string) {
  if (await confirmDelete(`a cor da tag "${name}"? Os itens continuam com a tag`)) {
    const { [name]: _removed, ...rest } = project.value.tagsColors
    await saveTags(rest)
  }
}
</script>

<template>
  <div style="max-width: 760px">
    <h1 class="page-title">{{ TITLES[list] }}</h1>

    <template v-if="list === 'status'">
      <p class="form-row muted">
        Os status das histórias de usuário são as colunas do kanban. Uma coluna de arquivo começa recolhida e guarda o que
        já saiu do quadro.
      </p>
      <ValueList title="Histórias de usuário" mode="status" kind="us" :rows="statusesOf('us')" @changed="refresh" />
      <ValueList title="Tarefas" mode="status" kind="task" :rows="statusesOf('task')" @changed="refresh" />
      <ValueList title="Problemas" mode="status" kind="issue" :rows="statusesOf('issue')" @changed="refresh" />
    </template>

    <template v-else-if="list === 'points'">
      <p class="form-row muted">Os valores que uma história de usuário pode valer. O valor vazio é o "não estimado".</p>
      <ValueList title="Pontos" mode="choice" kind="points" :rows="project.points" @changed="refresh" />
    </template>

    <ValueList v-else-if="list === 'priorities'" title="Prioridades dos problemas" mode="choice" kind="priority" :rows="project.priorities" @changed="refresh" />
    <ValueList v-else-if="list === 'severities'" title="Gravidades dos problemas" mode="choice" kind="severity" :rows="project.severities" @changed="refresh" />
    <ValueList v-else-if="list === 'types'" title="Tipos de problema" mode="choice" kind="issue_type" :rows="project.issueTypes" @changed="refresh" />

    <template v-else>
      <p class="form-row muted">Tags com cor aparecem coloridas nos cartões e nas listas. As outras tags continuam valendo, em cinza.</p>
      <div v-for="[name, color] in tags" :key="name" class="row-center gap-8" style="padding: 6px 0; border-bottom: 1px solid var(--fill)">
        <input
          type="color"
          :value="color"
          :aria-label="`Cor da tag ${name}`"
          :disabled="project.isArchived"
          @change="saveTags({ ...project.tagsColors, [name]: ($event.target as HTMLInputElement).value })"
        >
        <span class="tag" :style="{ background: color }" :title="name"><span>{{ name }}</span></span>
        <span class="grow" />
        <button class="icon-btn" type="button" title="Excluir" :disabled="project.isArchived" @click="removeTag(name)">
          <q-icon :name="icons.trash" size="16px" />
        </button>
      </div>
      <form v-if="!project.isArchived" class="row-center gap-8" style="margin-top: 16px" @submit.prevent="addTag">
        <input
          v-model="newTag.color"
          type="color"
          aria-label="Cor da nova tag"
        >
        <input v-model="newTag.name" class="field grow" maxlength="50" placeholder="Nova tag" aria-label="Nome da nova tag" required>
        <button class="btn" type="submit">Adicionar</button>
      </form>
    </template>
  </div>
</template>
