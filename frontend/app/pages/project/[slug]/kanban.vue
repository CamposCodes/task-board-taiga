<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { Item, Status } from '~/utils/types'

useHead({ title: 'Kanban' })

const { project, can } = useProject()
const actions = useItemActions()
const slug = project.value.slug

const statuses = computed(() => project.value.statuses.filter((s) => s.kind === 'us'))
const openArchived = ref<number[]>([])
const folded = usePreference<number[]>(`kanban-folded:${project.value.id}`, [])
const zoom = usePreference('kanban-zoom', 1)

async function load() {
  const [active, ...archived] = await Promise.all([
    fetchAll<Item>(`/projects/${slug}/userstories`, { archived: false }),
    ...openArchived.value.map((status) => fetchAll<Item>(`/projects/${slug}/userstories`, { status })),
  ])
  return [...active!, ...archived.flat()]
}
const { data, refresh } = await useAsyncData(`kanban:${slug}`, load)
const stories = computed(() => data.value ?? [])
const filters = useItemFilters(stories, 'us', ['tags', 'assignedTo', 'role', 'createdBy'])

const columns = reactive<Record<number, Item[]>>({})
watchEffect(() => {
  for (const status of statuses.value) {
    columns[status.id] = stories.value
      .filter((item) => item.status.id === status.id && filters.matches(item))
      .sort((a, b) => a.boardOrder - b.boardOrder || a.id - b.id)
  }
})
const total = (status: Status) => stories.value.filter((item) => item.status.id === status.id).length
const isFolded = (status: Status) => (status.isArchived ? !openArchived.value.includes(status.id) : folded.value.includes(status.id))

async function toggleFold(status: Status) {
  if (status.isArchived) {
    openArchived.value = isFolded(status) ? [...openArchived.value, status.id] : openArchived.value.filter((id) => id !== status.id)
    await refresh()
  } else {
    folded.value = isFolded(status) ? folded.value.filter((id) => id !== status.id) : [...folded.value, status.id]
  }
}

const dragging = ref(false)
async function onDrop(event: { item: HTMLElement; to: HTMLElement }) {
  dragging.value = false
  const id = Number(event.item.dataset.id)
  const statusId = Number(event.to.dataset.status)
  const item = stories.value.find((i) => i.id === id)
  const list = columns[statusId] ?? []
  const next = list[list.findIndex((i) => i.id === id) + 1]
  if (!item) {
    return
  }
  try {
    await actions.move(item, { statusId, beforeId: next?.id ?? null })
  } finally {
    await refresh()
  }
}

const creating = ref<{ statusId: number } | null>(null)
const bulking = ref<{ statusId: number } | null>(null)
const editing = ref<Item | null>(null)
const assigning = ref<Item | null>(null)

async function saved() {
  creating.value = bulking.value = editing.value = null
  await refresh()
}

async function assign(ids: number[]) {
  const item = assigning.value!
  assigning.value = null
  await actions.update(item, { assignedUsers: ids })
  await refresh()
}

async function remove(item: Item) {
  if (await confirmDelete(`a história de usuário #${item.ref}`)) {
    await actions.remove(item)
    await refresh()
  }
}

let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => {
    const busy = dragging.value || creating.value || bulking.value || editing.value || assigning.value
    if (document.visibilityState === 'visible' && !busy) {
      refresh()
    }
  }, 30_000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="section" style="padding-right: 0">
    <h1 class="page-title">Kanban</h1>
    <div style="padding-right: 16px">
      <BoardToolbar v-model:zoom="zoom" :filters="filters" />
    </div>

    <div class="board-area">
      <div class="board" :class="`zoom-${zoom}`">
        <div v-for="status in statuses" :key="status.id" class="kcol" :class="{ folded: isFolded(status) }">
          <template v-if="isFolded(status)">
            <div class="kcol-head">
              <button class="icon-btn" type="button" title="Abrir coluna" @click="toggleFold(status)">
                <q-icon :name="icons.unfoldColumn" size="12px" />
              </button>
            </div>
            <div class="kcol-body">
              <div class="vertical">
                <i class="square" :style="{ background: status.color }" />
                <span class="medium">{{ status.name }}</span>
                <span v-if="status.isArchived" class="muted medium">(arquivado)</span>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="kcol-head">
              <i class="square" :style="{ background: status.color }" />
              <span class="name" :title="status.name">{{ status.name }}</span>
              <template v-if="can('add_us') && !project.isArchived">
                <button class="icon-btn" type="button" title="Adicionar história de usuário" @click="creating = { statusId: status.id }">
                  <q-icon :name="icons.add" size="18px" />
                </button>
                <button class="icon-btn" type="button" title="Adicionar histórias em lote" @click="bulking = { statusId: status.id }">
                  <q-icon :name="icons.bulk" size="18px" />
                </button>
              </template>
              <button class="icon-btn" type="button" title="Recolher coluna" @click="toggleFold(status)">
                <q-icon :name="icons.foldColumn" size="12px" />
              </button>
            </div>
            <div class="kcol-body">
              <span
                class="count"
                :class="{ over: status.wipLimit && total(status) > status.wipLimit }"
                :title="status.wipLimit ? `Limite de ${status.wipLimit} histórias em andamento` : 'Número de histórias'"
              >
                {{ total(status) }}<template v-if="status.wipLimit">/{{ status.wipLimit }}</template>
              </span>
              <ClientOnly>
                <VueDraggable
                  v-model="columns[status.id]!"
                  class="drop"
                  group="stories"
                  :animation="150"
                  :disabled="!can('modify_us') || project.isArchived"
                  :data-status="status.id"
                  @start="dragging = true"
                  @end="onDrop"
                >
                  <ItemCard
                    v-for="item in columns[status.id]"
                    :key="item.id"
                    :item="item"
                    :zoom="zoom"
                    :editable="can('modify_us') && !project.isArchived"
                    :deletable="can('delete_us') && !project.isArchived"
                    @edit="editing = item"
                    @assign="assigning = item"
                    @remove="remove(item)"
                  />
                </VueDraggable>
                <template #fallback>
                  <ItemCard v-for="item in columns[status.id]" :key="item.id" :item="item" :zoom="zoom" />
                </template>
              </ClientOnly>
            </div>
          </template>
        </div>
      </div>
    </div>

    <ItemLightbox v-if="creating" kind="us" :defaults="creating" position @saved="saved" @close="creating = null" />
    <BulkLightbox v-if="bulking" kind="us" :defaults="bulking" position @saved="saved" @close="bulking = null" />
    <ItemLightbox v-if="editing" kind="us" :item="editing" @saved="saved" @close="editing = null" />
    <UserPicker
      v-if="assigning"
      title="Selecionar responsáveis"
      :members="project.members"
      :selected="assigning.assignedUsers.map((u) => u.id)"
      multiple
      @pick="assign"
      @close="assigning = null"
    />
  </div>
</template>
