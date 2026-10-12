<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { Item, Stats } from '~/utils/types'

const route = useRoute()
const api = useApi()
const actions = useItemActions()
const { project, can, refresh: refreshProject } = useProject()
const slug = project.value.slug

const sprint = computed(() => project.value.milestones.find((m) => m.slug === route.params.sprint)!)
if (!sprint.value) {
  throw createError({ statusCode: 404, statusMessage: 'Sprint não encontrada', fatal: true })
}
useHead({ title: () => `${sprint.value.name} - Quadro de tarefas` })

const showIssues = computed(() => project.value.isIssuesActivated && can('view_issues'))
const { data, refresh } = await useAsyncData(
  () => `taskboard:${slug}:${sprint.value.id}`,
  async () => {
    const milestone = sprint.value.id
    const [stories, tasks, issues, stats] = await Promise.all([
      fetchAll<Item>(`/projects/${slug}/userstories`, { milestone, sort: 'list' }),
      fetchAll<Item>(`/projects/${slug}/tasks`, { milestone }),
      showIssues.value ? fetchAll<Item>(`/projects/${slug}/issues`, { milestone }) : Promise.resolve([]),
      api<Stats>(`/projects/${slug}/stats`, { query: { milestone } }),
    ])
    return { stories, tasks, issues, stats }
  },
)
const stories = computed(() => data.value?.stories ?? [])
const tasks = computed(() => data.value?.tasks ?? [])
const issues = computed(() => data.value?.issues ?? [])
const stats = computed(() => data.value?.stats)

const statuses = computed(() => project.value.statuses.filter((s) => s.kind === 'task'))
const filters = useItemFilters(tasks, 'task', ['tags', 'assignedTo', 'role', 'createdBy'])
const zoom = usePreference('taskboard-zoom', 2)
const showTags = usePreference('taskboard-issue-tags', true)
const foldedColumns = usePreference<number[]>(`taskboard-folded:${project.value.id}`, [])
const foldedRows = ref<number[]>([])
const editable = computed(() => can('modify_task') && !project.value.isArchived)
const canAdd = computed(() => can('add_task') && !project.value.isArchived && !sprint.value.isClosed)

const rows = computed(() => [...stories.value.map((story) => ({ id: story.id, story })), { id: 0, story: null as Item | null }])
const cells = reactive<Record<string, Item[]>>({})
watchEffect(() => {
  for (const row of rows.value) {
    for (const status of statuses.value) {
      cells[`${row.id}:${status.id}`] = tasks.value
        .filter((task) => (task.parent?.id ?? 0) === row.id && task.status.id === status.id && filters.matches(task))
        .sort((a, b) => a.boardOrder - b.boardOrder || a.id - b.id)
    }
  }
})

const totalPoints = computed(() => Number(stats.value?.totals.pointsOpen ?? 0) + Number(stats.value?.totals.pointsClosed ?? 0))
const donePoints = computed(() => Number(stats.value?.totals.pointsClosed ?? 0))
const percent = computed(() => (totalPoints.value ? Math.round((donePoints.value * 100) / totalPoints.value) : 0))
const otherSprints = computed(() => project.value.milestones.filter((m) => !m.isClosed && m.id !== sprint.value.id))
const unfinished = computed(() => stories.value.filter((story) => !story.status.isClosed))

const showChart = ref(false)
const days = computed(() => {
  const { estimatedStart: start, estimatedFinish: finish } = sprint.value
  const list: { iso: string; name: string; label: string; closed: number }[] = []
  for (const day = new Date(`${start}T00:00:00Z`); day.toISOString().slice(0, 10) <= finish; day.setUTCDate(day.getUTCDate() + 1)) {
    const iso = day.toISOString().slice(0, 10)
    list.push({ iso, name: formatDay(iso), label: `${iso.slice(8)}/${iso.slice(5, 7)}`, closed: 0 })
  }
  for (const story of stories.value) {
    if (story.status.isClosed && story.finishedAt) {
      const on = story.finishedAt.slice(0, 10)
      const day = list.find((d) => d.iso >= on) ?? list.at(-1)
      if (day) {
        day.closed += Number(story.points ?? 0)
      }
    }
  }
  return list
})
const today = new Date().toISOString().slice(0, 10)

async function reload() {
  await Promise.all([refresh(), refreshProject()])
}

const dragging = ref(false)
async function onDrop(event: { item: HTMLElement; to: HTMLElement }) {
  dragging.value = false
  const id = Number(event.item.dataset.id)
  const parent = Number(event.to.dataset.parent)
  const statusId = Number(event.to.dataset.status)
  const task = tasks.value.find((t) => t.id === id)
  const list = cells[`${parent}:${statusId}`] ?? []
  const next = list[list.findIndex((t) => t.id === id) + 1]
  if (!task) {
    return
  }
  try {
    await actions.move(task, { statusId, parentId: parent || null, beforeId: next?.id ?? null })
  } finally {
    await refresh()
  }
}

async function moveUnfinished(milestoneId: number) {
  await api(`/projects/${slug}/userstories/bulk-milestone`, {
    method: 'POST',
    body: { ids: unfinished.value.map((story) => story.id), milestoneId },
  })
  await reload()
}

const creating = ref<{ parentId: number | null } | null>(null)
const bulking = ref<{ parentId: number | null } | null>(null)
const editing = ref<Item | null>(null)
const assigning = ref<Item | null>(null)
const issueForm = ref<'one' | 'bulk' | null>(null)
const taskDefaults = (form: { parentId: number | null }) =>
  form.parentId ? { parentId: form.parentId } : { milestoneId: sprint.value.id }

async function saved() {
  creating.value = bulking.value = editing.value = issueForm.value = null
  await reload()
}

async function assign(ids: number[]) {
  const task = assigning.value!
  assigning.value = null
  await actions.update(task, { assignedTo: ids[0] ?? null }).catch(() => null)
  await refresh()
}

async function remove(task: Item) {
  if (await confirmDelete(`a tarefa #${task.ref}`)) {
    await actions.remove(task)
    await refresh()
  }
}

const flip = (list: number[], id: number) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id])
const toggleColumn = (id: number) => (foldedColumns.value = flip(foldedColumns.value, id))
const toggleRow = (id: number) => (foldedRows.value = flip(foldedRows.value, id))

let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => {
    const busy = dragging.value || creating.value || bulking.value || editing.value || assigning.value || issueForm.value
    if (document.visibilityState === 'visible' && !busy) {
      refresh()
    }
  }, 30_000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="tb-page">
    <header>
      <h1>
        {{ sprint.name }}
        <span class="date">{{ formatDay(sprint.estimatedStart) }} a {{ formatDay(sprint.estimatedFinish) }}</span>
        <span v-if="sprint.isClosed" class="chip" style="margin-left: 8px">fechada</span>
      </h1>
    </header>

    <div class="band">
      <div class="main">
        <div class="progress-wrap">
          <div class="progress"><i :style="{ width: `${percent}%` }" /></div>
          <span class="percent">{{ percent }}%</span>
        </div>
        <div class="stats">
          <div class="stat"><b>{{ number(totalPoints) }}</b><span>pontos<br>totais</span></div>
          <div class="stat before-line"><b>{{ number(donePoints) }}</b><span>pontos<br>completados</span></div>
          <div class="stat after-line"><b>{{ stats?.tasks.open ?? 0 }}</b><span>tarefas<br>abertas</span></div>
          <div class="stat"><b>{{ stats?.tasks.closed ?? 0 }}</b><span>tarefas<br>fechadas</span></div>
          <div v-if="can('modify_us')" class="stat before-line">
            <button class="tool" type="button" title="Mover as histórias não concluídas para outra sprint" :disabled="!unfinished.length || !otherSprints.length">
              <q-icon :name="icons.swap" size="21px" />
              <MenuList v-if="unfinished.length && otherSprints.length" :options="otherSprints" @pick="moveUnfinished" />
            </button>
          </div>
          <div class="stat after-line last">
            <q-icon :name="icons.iocaine" size="21px" /><b>{{ stats?.tasks.iocaine ?? 0 }}</b><span>doses de<br>iocaína</span>
          </div>
        </div>
      </div>
      <button class="tool graph" :class="{ active: showChart }" type="button" title="Mostrar ou ocultar o gráfico" :aria-pressed="showChart" @click="showChart = !showChart">
        <q-icon :name="icons.chart" size="24px" />
      </button>
    </div>
    <BurndownChart v-if="showChart" :steps="days" :known="days.filter((day) => day.iso <= today).length" :total="totalPoints" axis="Dias" />

    <BoardToolbar v-model:zoom="zoom" :filters="filters" />

    <div class="tb-area">
      <div class="tb-table" :class="`zoom-${zoom}`">
        <div class="tb-row tb-head">
          <h2 class="tb-th story"><span>História de usuário</span></h2>
          <h2 v-for="status in statuses" :key="status.id" class="tb-th" :class="{ folded: foldedColumns.includes(status.id) }" :style="{ borderBottomColor: status.color }">
            <span :title="status.name">{{ status.name }}</span>
            <button type="button" :title="foldedColumns.includes(status.id) ? `Abrir ${status.name}` : 'Recolher coluna'" @click="toggleColumn(status.id)">
              <q-icon :name="foldedColumns.includes(status.id) ? icons.unfoldColumn : icons.foldColumn" size="12px" />
            </button>
          </h2>
        </div>

        <div class="tb-lanes">
          <div v-for="row in rows" :key="row.id" class="tb-row tb-lane" :class="{ folded: foldedRows.includes(row.id), blocked: row.story?.isBlocked }">
            <div class="tb-title">
              <button
                class="tb-fold"
                type="button"
                :title="foldedRows.includes(row.id) ? 'Abrir raia' : 'Recolher raia'"
                :aria-expanded="!foldedRows.includes(row.id)"
                @click="toggleRow(row.id)"
              >
                <q-icon :name="foldedRows.includes(row.id) ? icons.down : icons.up" size="16px" />
              </button>
              <div class="tb-us">
                <h3>
                  <NuxtLink v-if="row.story" :to="`/project/${slug}/us/${row.story.ref}`" :title="`#${row.story.ref} ${row.story.subject}`">
                    <span class="us-ref">#{{ row.story.ref }}</span>
                    <q-icon v-if="row.story.isBlocked" class="us-lock" :name="icons.lock" size="14px" role="img" aria-hidden="false" :aria-label="`Bloqueada${row.story.blockedNote ? `: ${row.story.blockedNote}` : ''}`" />
                    <span class="us-subject">{{ row.story.subject }}</span>
                  </NuxtLink>
                  <span v-else class="us-subject">Tarefas sem história</span>
                </h3>
                <div v-if="row.story" class="us-data">
                  <span class="points-chip" title="Pontos">{{ pointsLabel(project.points, row.story.points) }}</span>
                  <span class="us-status status-text" :style="{ '--status': row.story.status.color }">{{ row.story.status.name }}</span>
                </div>
                <div v-if="canAdd" class="add">
                  <button type="button" title="Adicionar tarefa" @click="creating = { parentId: row.story?.id ?? null }">
                    <q-icon :name="icons.add" size="16px" />
                  </button>
                  <button type="button" title="Adicionar tarefas em lote" @click="bulking = { parentId: row.story?.id ?? null }">
                    <q-icon :name="icons.bulk" size="16px" />
                  </button>
                </div>
              </div>
            </div>
            <div v-for="status in statuses" :key="status.id" class="tb-cell" :class="{ folded: foldedColumns.includes(status.id) }">
              <ClientOnly>
                <VueDraggable
                  v-model="cells[`${row.id}:${status.id}`]!"
                  class="drop"
                  group="tasks"
                  :animation="150"
                  :disabled="!editable"
                  :data-parent="row.id"
                  :data-status="status.id"
                  @start="dragging = true"
                  @end="onDrop"
                >
                  <ItemCard
                    v-for="task in cells[`${row.id}:${status.id}`]"
                    :key="task.id"
                    :item="task"
                    :zoom="zoom"
                    :mini="foldedRows.includes(row.id) || foldedColumns.includes(status.id)"
                    :editable="editable"
                    :deletable="can('delete_task') && !project.isArchived"
                    @edit="editing = task"
                    @assign="assigning = task"
                    @remove="remove(task)"
                  />
                </VueDraggable>
              </ClientOnly>
            </div>
          </div>
        </div>

        <section v-if="showIssues" class="tb-issues">
          <div class="bar">
            <div class="row-center">
              <span class="title"><q-icon :name="icons.issues" size="16px" />Problemas da sprint</span>
              <button type="button" class="switch" :class="{ on: showTags }" role="switch" :aria-checked="showTags" @click="showTags = !showTags"><i />Tags</button>
            </div>
            <div v-if="can('add_issue') && !project.isArchived" class="new">
              <button class="icon-btn" type="button" title="Adicionar problema à sprint" @click="issueForm = 'one'"><q-icon :name="icons.add" size="16px" /></button>
              <button class="icon-btn" type="button" title="Adicionar problemas em lote" @click="issueForm = 'bulk'"><q-icon :name="icons.bulk" size="16px" /></button>
            </div>
          </div>
          <div class="cards">
            <ItemCard v-for="issue in issues" :key="issue.id" :item="issue" :zoom="3" :hide-tags="!showTags" />
          </div>
        </section>
      </div>
    </div>

    <ItemLightbox v-if="creating" kind="task" :defaults="taskDefaults(creating)" @saved="saved" @close="creating = null" />
    <BulkLightbox v-if="bulking" kind="task" :defaults="taskDefaults(bulking)" @saved="saved" @close="bulking = null" />
    <ItemLightbox v-if="editing" kind="task" :item="editing" @saved="saved" @close="editing = null" />
    <ItemLightbox v-if="issueForm === 'one'" kind="issue" :defaults="{ milestoneId: sprint.id }" @saved="saved" @close="issueForm = null" />
    <BulkLightbox v-if="issueForm === 'bulk'" kind="issue" :defaults="{ milestoneId: sprint.id }" @saved="saved" @close="issueForm = null" />
    <UserPicker
      v-if="assigning"
      title="Selecionar responsável"
      :members="project.members"
      :selected="assigning.assignedUsers.map((u) => u.id)"
      @pick="assign"
      @close="assigning = null"
    />
  </div>
</template>
