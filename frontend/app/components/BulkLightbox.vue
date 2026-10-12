<script setup lang="ts">
import type { Item, Kind } from '~/utils/types'

const props = defineProps<{
  kind: Kind
  defaults?: { statusId?: number; milestoneId?: number | null; parentId?: number | null }
  position?: boolean
}>()
const emit = defineEmits<{ saved: [items: Item[]]; close: [] }>()

const { project } = useProject()
const api = useApi()

const statuses = computed(() => project.value.statuses.filter((s) => s.kind === props.kind))
const statusId = ref(props.defaults?.statusId ?? (statuses.value.find((s) => !s.isClosed) ?? statuses.value[0])!.id)
const status = computed(() => statuses.value.find((s) => s.id === statusId.value)!)
const position = ref<'bottom' | 'top'>('bottom')
const text = ref('')
const saving = ref(false)

async function submit() {
  saving.value = true
  try {
    const body: Record<string, unknown> = { text: text.value, statusId: statusId.value }
    if (props.position) {
      body.position = position.value
    }
    if (props.defaults?.milestoneId !== undefined) {
      body.milestoneId = props.defaults.milestoneId
    }
    if (props.defaults?.parentId !== undefined && props.kind === 'task') {
      body.parentId = props.defaults.parentId
    }
    emit('saved', await api<Item[]>(`/projects/${project.value.slug}/${kindPath[props.kind]}/bulk`, { method: 'POST', body }))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppLightbox title="Nova inserção em lote" narrow @close="emit('close')">
    <form @submit.prevent="submit">
      <span class="label">Status</span>
      <div class="status-select" :style="{ background: status.color, color: textColorOn(status.color) }">
        <span class="clip">{{ status.name }}</span>
        <q-icon :name="icons.down" size="18px" />
        <MenuList :options="statuses" @pick="statusId = $event" />
      </div>
      <div v-if="position" class="form-row">
        <span class="label">Localização</span>
        <div class="row-center gap-16">
          <label class="radio"><input v-model="position" type="radio" value="bottom"> embaixo</label>
          <label class="radio"><input v-model="position" type="radio" value="top"> acima</label>
        </div>
      </div>
      <div class="form-row">
        <textarea v-model="text" class="field" style="min-height: 190px" placeholder="Um item por linha..." aria-label="Um item por linha" required v-focus />
      </div>
      <div style="text-align: right">
        <button class="btn" type="submit" :disabled="saving">Salvar</button>
      </div>
    </form>
  </AppLightbox>
</template>
