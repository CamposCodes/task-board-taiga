<script setup lang="ts">
import type { Milestone } from '~/utils/types'

const props = defineProps<{ sprint?: Milestone }>()
const emit = defineEmits<{ saved: []; close: [] }>()

const { project, can } = useProject()
const api = useApi()

const addDays = (isoDate: string, days: number) =>
  new Date(new Date(isoDate + 'T00:00:00Z').getTime() + days * 86_400_000).toISOString().slice(0, 10)

const last = computed(() => [...project.value.milestones].sort((a, b) => b.estimatedFinish.localeCompare(a.estimatedFinish))[0])
const start = props.sprint?.estimatedStart ?? last.value?.estimatedFinish ?? todayIso()
const form = reactive({
  name: props.sprint?.name ?? `Sprint ${project.value.milestones.length + 1}`,
  estimatedStart: start,
  estimatedFinish: props.sprint?.estimatedFinish ?? addDays(start, 7),
})
const saving = ref(false)

async function run(action: () => Promise<unknown>) {
  saving.value = true
  try {
    await action()
    emit('saved')
  } finally {
    saving.value = false
  }
}

const submit = () =>
  run(() =>
    props.sprint
      ? api(`/milestones/${props.sprint.id}`, { method: 'PATCH', body: form })
      : api(`/projects/${project.value.slug}/milestones`, { method: 'POST', body: form }),
  )

const setClosed = (isClosed: boolean) => run(() => api(`/milestones/${props.sprint!.id}`, { method: 'PATCH', body: { isClosed } }))

async function remove() {
  if (await confirmDelete(`a sprint "${props.sprint!.name}"? As histórias dela voltam para o backlog`)) {
    await run(() => api(`/milestones/${props.sprint!.id}`, { method: 'DELETE' }))
  }
}
</script>

<template>
  <AppLightbox :title="sprint ? 'Editar sprint' : 'Nova sprint'" narrow @close="emit('close')">
    <form @submit.prevent="submit">
      <div class="form-row">
        <input v-model="form.name" class="field" placeholder="nome da sprint" aria-label="Nome da sprint" maxlength="120" required v-focus>
        <p v-if="!sprint && last" class="muted" style="margin-top: 4px; font-size: 12px">A última sprint é {{ last.name }}.</p>
      </div>
      <div class="form-row row-center gap-16">
        <label class="grow">
          <span class="label">Início estimado</span>
          <input v-model="form.estimatedStart" class="field" type="date" required>
        </label>
        <label class="grow">
          <span class="label">Fim estimado</span>
          <input v-model="form.estimatedFinish" class="field" type="date" :min="form.estimatedStart" required>
        </label>
      </div>
      <button class="btn big" type="submit" :disabled="saving">{{ sprint ? 'Salvar' : 'Criar' }}</button>
      <div v-if="sprint" class="row-center gap-16" style="justify-content: center; margin-top: 16px">
        <a href="#" class="pointer" @click.prevent="setClosed(!sprint.isClosed)">{{ sprint.isClosed ? 'Reabrir a sprint' : 'Fechar a sprint' }}</a>
        <a href="#" v-if="can('delete_milestone')" class="pointer" style="color: var(--danger)" @click.prevent="remove">Excluir a sprint</a>
      </div>
    </form>
  </AppLightbox>
</template>
