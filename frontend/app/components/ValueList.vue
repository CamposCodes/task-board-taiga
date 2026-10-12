<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'

interface Row {
  id: number
  name: string
  color: string
  isClosed?: boolean
  isArchived?: boolean
  wipLimit?: number | null
  value?: number | null
}

const props = defineProps<{
  title: string
  mode: 'status' | 'choice'
  kind: string
  rows: Row[]
}>()
const emit = defineEmits<{ changed: [] }>()

const api = useApi()
const { project } = useProject()
const resource = computed(() => (props.mode === 'status' ? 'statuses' : 'choices'))
const isPoints = computed(() => props.kind === 'points')
const locked = computed(() => project.value.isArchived)

const list = ref<Row[]>([])
watchEffect(() => {
  list.value = props.rows.map((row) => ({ ...row }))
})
const draft = reactive({ name: '', color: '#555555', value: '' })

async function patch(row: Row, changes: Record<string, unknown>) {
  try {
    await api(`/${resource.value}/${row.id}`, { method: 'PATCH', body: changes })
  } finally {
    emit('changed')
  }
}

function rename(row: Row) {
  const original = props.rows.find((r) => r.id === row.id)
  if (row.name.trim() && row.name.trim() !== original?.name) {
    patch(row, { name: row.name.trim() })
  } else {
    row.name = original?.name ?? row.name
  }
}

const setValue = (row: Row, text: string) => patch(row, text === '' ? { clearValue: true } : { value: Number(text) })
const setLimit = (row: Row, text: string) => patch(row, { wipLimit: Number(text) || 0 })

async function add() {
  const body: Record<string, unknown> = { kind: props.kind, name: draft.name.trim(), color: draft.color }
  if (isPoints.value && draft.value !== '') {
    body.value = Number(draft.value)
  }
  await api(`/projects/${project.value.id}/${resource.value}`, { method: 'POST', body })
  draft.name = ''
  draft.value = ''
  emit('changed')
}

async function remove(row: Row) {
  if (await confirmDelete(`"${row.name}"`)) {
    await api(`/${resource.value}/${row.id}`, { method: 'DELETE' })
    emit('changed')
  }
}

async function reorder() {
  try {
    await api(`/projects/${project.value.id}/${resource.value}/reorder`, {
      method: 'POST',
      body: { kind: props.kind, ids: list.value.map((row) => row.id) },
    })
  } finally {
    emit('changed')
  }
}
</script>

<template>
  <section style="margin-bottom: 40px">
    <h2 class="title-bar" style="margin-bottom: 8px">{{ title }}</h2>
    <div class="value-row muted" style="font-size: 11.2px; text-transform: uppercase">
      <span style="width: 20px" />
      <span v-if="!isPoints" style="width: 32px">Cor</span>
      <span class="grow">Nome</span>
      <span v-if="isPoints" style="width: 90px">Valor</span>
      <template v-if="mode === 'status'">
        <span style="width: 70px; text-align: center">Fechado</span>
        <template v-if="kind === 'us'">
          <span style="width: 80px; text-align: center">Arquivado</span>
          <span style="width: 80px">Limite</span>
        </template>
      </template>
      <span style="width: 24px" />
    </div>

    <ClientOnly>
      <VueDraggable v-model="list" handle=".handle" :animation="150" :disabled="locked" @end="reorder">
        <div v-for="row in list" :key="row.id" class="value-row">
          <q-icon class="handle muted" :name="icons.drag" size="18px" style="width: 20px; cursor: grab" />
          <input v-if="!isPoints" v-model="row.color" type="color" :aria-label="`Cor de ${row.name}`" :disabled="locked" @change="patch(row, { color: row.color })">
          <input v-model="row.name" class="field grow" maxlength="60" :aria-label="`Nome de ${row.name}`" :disabled="locked" @change="rename(row)">
          <input
            v-if="isPoints"
            class="field"
            style="width: 90px"
            type="number"
            min="0"
            step="0.5"
            placeholder="?"
            :value="row.value ?? ''"
            :aria-label="`Valor de ${row.name}`"
            :disabled="locked"
            @change="setValue(row, ($event.target as HTMLInputElement).value)"
          >
          <template v-if="mode === 'status'">
            <span style="width: 70px; text-align: center">
              <input type="checkbox" :checked="row.isClosed" :aria-label="`${row.name} fecha o item`" :disabled="locked" @change="patch(row, { isClosed: !row.isClosed })">
            </span>
            <template v-if="kind === 'us'">
              <span style="width: 80px; text-align: center">
                <input type="checkbox" :checked="row.isArchived" :aria-label="`${row.name} é coluna de arquivo`" :disabled="locked" @change="patch(row, { isArchived: !row.isArchived })">
              </span>
              <input
                class="field"
                style="width: 80px"
                type="number"
                min="0"
                placeholder="sem"
                :value="row.wipLimit ?? ''"
                :aria-label="`Limite de histórias em ${row.name}`"
                :disabled="locked"
                @change="setLimit(row, ($event.target as HTMLInputElement).value)"
              >
            </template>
          </template>
          <button class="icon-btn" type="button" title="Excluir" :disabled="locked" @click="remove(row)"><q-icon :name="icons.trash" size="16px" /></button>
        </div>
      </VueDraggable>
    </ClientOnly>

    <form v-if="!locked" class="value-row" style="margin-top: 8px" @submit.prevent="add">
      <span style="width: 20px" />
      <input v-if="!isPoints" v-model="draft.color" type="color" aria-label="Cor do novo valor">
      <input v-model="draft.name" class="field grow" maxlength="60" placeholder="Novo valor" aria-label="Nome do novo valor" required>
      <input v-if="isPoints" v-model="draft.value" class="field" style="width: 90px" type="number" min="0" step="0.5" placeholder="?" aria-label="Valor em pontos">
      <button class="btn" type="submit">Adicionar</button>
    </form>
  </section>
</template>

<style scoped>
.value-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
</style>
