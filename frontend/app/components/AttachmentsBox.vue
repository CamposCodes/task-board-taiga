<script setup lang="ts">
import type { Attachment } from '~/utils/types'

const props = defineProps<{ itemId?: number; editable?: boolean }>()
const pending = defineModel<File[]>('pending', { default: () => [] })
const emit = defineEmits<{ changed: [count: number] }>()

const api = useApi()
const actions = useItemActions()
const files = ref<Attachment[]>([])
const input = ref<HTMLInputElement>()
const over = ref(false)
const busy = ref(false)

async function load() {
  if (props.itemId) {
    files.value = await api<Attachment[]>(`/items/${props.itemId}/attachments`)
  }
}
onMounted(load)
watch(() => props.itemId, load)

const count = computed(() => (props.itemId ? files.value.length : pending.value.length))

async function add(list: FileList | null | undefined) {
  const chosen = [...(list ?? [])]
  over.value = false
  if (!chosen.length) {
    return
  }
  if (!props.itemId) {
    pending.value = [...pending.value, ...chosen]
    return
  }
  busy.value = true
  try {
    files.value.push(...(await actions.upload(props.itemId, chosen)))
    emit('changed', files.value.length)
  } finally {
    busy.value = false
  }
}

async function remove(file: Attachment) {
  if (await confirmDelete(`o anexo "${file.name}"`)) {
    await api(`/attachments/${file.id}`, { method: 'DELETE' })
    files.value = files.value.filter((f) => f.id !== file.id)
    emit('changed', files.value.length)
  }
}
</script>

<template>
  <section style="margin-bottom: 32px">
    <div class="box-head">
      <span>{{ count }} Anexos</span>
      <button v-if="editable" class="btn icon" type="button" title="Adicionar anexo" :disabled="busy" @click="input?.click()">
        <q-icon :name="icons.add" size="16px" />
      </button>
      <input ref="input" type="file" multiple hidden @change="add(input?.files); input && (input.value = '')">
    </div>

    <div v-for="file in files" :key="file.id" class="attachment">
      <a class="grow row-center gap-4" :href="`/api/v1/attachments/${file.id}/download`" target="_blank" rel="noopener">
        <q-icon :name="icons.attachment" size="16px" />
        {{ file.name }}
      </a>
      <span class="size">{{ fileSize(file.size) }}</span>
      <span class="tools">
        <button v-if="editable" class="icon-btn" type="button" title="Excluir anexo" @click="remove(file)">
          <q-icon :name="icons.trash" size="16px" />
        </button>
      </span>
    </div>
    <div v-for="(file, index) in itemId ? [] : pending" :key="index" class="attachment">
      <span class="grow row-center gap-4"><q-icon :name="icons.attachment" size="16px" />{{ file.name }}</span>
      <span class="size">{{ fileSize(file.size) }}</span>
      <button class="icon-btn" type="button" title="Tirar da lista" @click="pending = pending.filter((_, i) => i !== index)">
        <q-icon :name="icons.close" size="16px" />
      </button>
    </div>

    <div
      v-if="editable && !count"
      class="dropzone"
      :class="{ over }"
      @dragover.prevent="over = true"
      @dragleave="over = false"
      @drop.prevent="add($event.dataTransfer?.files)"
    >
      Jogue os anexos aqui!
    </div>
  </section>
</template>
