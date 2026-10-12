<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { Item, Milestone } from '~/utils/types'

useHead({ title: 'Backlog' })

const { project, can, refresh: refreshProject } = useProject()
const actions = useItemActions()
const slug = project.value.slug

const { data, refresh } = await useAsyncData(`backlog:${slug}`, () =>
  fetchAll<Item>(`/projects/${slug}/userstories`, { sort: 'list' }),
)
const stories = computed(() => data.value ?? [])
const unplanned = computed(() => stories.value.filter((item) => !item.milestone))
const filters = useItemFilters(unplanned, 'us', ['status', 'tags', 'assignedTo', 'role', 'createdBy'])
const showTags = usePreference('backlog-tags', true)
const showChart = ref(false)
const showClosed = ref(false)
const foldedSprints = ref<number[]>([])

const statuses = computed(() => project.value.statuses.filter((s) => s.kind === 'us'))
const editable = computed(() => can('modify_us') && !project.value.isArchived)
const sprints = computed(() => project.value.milestones.filter((m) => showClosed.value || !m.isClosed))
const closedCount = computed(() => project.value.milestones.filter((m) => m.isClosed).length)

const backlog = ref<Item[]>([])
const bySprint = reactive<Record<number, Item[]>>({})
watchEffect(() => {
  backlog.value = unplanned.value.filter(filters.matches)
  for (const sprint of project.value.milestones) {
    bySprint[sprint.id] = stories.value.filter((item) => item.milestone?.id === sprint.id)
  }
})

const defined = computed(() => sumPoints(stories.value))
const closed = computed(() => sumPoints(stories.value.filter((item) => item.status.isClosed)))
const percent = computed(() => (defined.value ? Math.round((closed.value * 100) / defined.value) : 0))
const chartSteps = computed(() =>
  [...project.value.milestones]
    .sort((a, b) => a.estimatedStart.localeCompare(b.estimatedStart))
    .map((sprint) => ({ name: sprint.name, closed: Number(sprint.pointsClosed) })),
)
const speed = computed(() => {
  const done = project.value.milestones.filter((m) => m.isClosed || m.estimatedFinish < todayIso()).length
  return closed.value / Math.max(done, 1)
})

async function reload() {
  await Promise.all([refresh(), refreshProject()])
}

async function onDrop(event: { item: HTMLElement; to: HTMLElement }) {
  const id = Number(event.item.dataset.id)
  const target = event.to.dataset.milestone
  const milestoneId = target ? Number(target) : null
  const list = milestoneId ? (bySprint[milestoneId] ?? []) : backlog.value
  const item = stories.value.find((i) => i.id === id)
  const next = list[list.findIndex((i) => i.id === id) + 1]
  if (!item) {
    return
  }
  try {
    await actions.move(item, { order: 'list', milestoneId, beforeId: next?.id ?? null })
  } finally {
    await reload()
  }
}

async function change(item: Item, changes: Record<string, unknown>) {
  await actions.update(item, changes).catch(() => null)
  await reload()
}

const creating = ref(false)
const bulking = ref(false)
const sprintForm = ref<{ sprint?: Milestone } | null>(null)

async function saved() {
  creating.value = bulking.value = false
  sprintForm.value = null
  await reload()
}

const toggleSprint = (id: number) =>
  (foldedSprints.value = foldedSprints.value.includes(id) ? foldedSprints.value.filter((x) => x !== id) : [...foldedSprints.value, id])
const pointsOptions = computed(() => project.value.points.map((p) => ({ id: p.id, name: p.name })))
const pointsValue = (id: number) => project.value.points.find((p) => p.id === id)?.value ?? null
</script>

<template>
  <div class="section backlog-layout">
    <div class="grow">
      <h1 class="page-title">Scrum</h1>

      <div class="band wide">
        <div class="main">
          <div class="progress-wrap">
            <div class="progress"><i :style="{ width: `${percent}%` }" /></div>
            <span class="percent">{{ percent }}%</span>
          </div>
          <div class="stats">
            <div class="stat"><b>{{ number(defined) }}</b><span>pontos<br>definidos</span></div>
            <div class="stat"><b>{{ number(closed) }}</b><span>pontos<br>fechados</span></div>
            <div class="stat last"><b>{{ number(speed) }}</b><span>pontos /<br>sprint</span></div>
          </div>
        </div>
        <button class="tool graph" :class="{ active: showChart }" type="button" title="Mostrar ou ocultar o gráfico" :aria-pressed="showChart" @click="showChart = !showChart">
          <q-icon :name="icons.chart" size="24px" />
        </button>
      </div>
      <BurndownChart v-if="showChart" :steps="chartSteps" :total="defined" axis="Sprints" />

      <div class="row-center gap-8" style="margin-bottom: 12px">
        <h2 style="font-size: 19.2px">Backlog</h2>
        <span class="muted" style="font-size: 11px">{{ unplanned.length }} histórias de usuário</span>
      </div>
      <BoardToolbar v-model:show-tags="showTags" :filters="filters">
        <template v-if="can('add_us') && !project.isArchived">
          <button class="btn" type="button" @click="creating = true"><q-icon :name="icons.add" size="16px" />História de usuário</button>
          <button class="btn icon gray" type="button" title="Adicionar histórias em lote" @click="bulking = true">
            <q-icon :name="icons.bulk" size="16px" />
          </button>
        </template>
      </BoardToolbar>

      <div style="display: flex; align-items: flex-start">
        <div class="grow">
          <div class="backlog-head">
            <span class="grow">História de usuário</span>
            <span class="cell-status">Status</span>
            <span class="cell-points teal">Pontos</span>
          </div>
          <ClientOnly>
            <VueDraggable
              v-model="backlog"
              group="stories"
              handle=".handle"
              :animation="150"
              :disabled="!editable"
              data-milestone=""
              style="min-height: 60px"
              @end="onDrop"
            >
              <div v-for="item in backlog" :key="item.id" class="backlog-row" :data-id="item.id">
                <q-icon v-if="editable" class="handle" :name="icons.drag" size="18px" title="Arraste para ordenar ou para uma sprint" />
                <div class="main">
                  <NuxtLink :class="{ strike: item.status.isClosed }" :to="`/project/${slug}/us/${item.ref}`">
                    <span class="ref">#{{ item.ref }}</span><span class="subject">{{ item.subject }}</span>
                  </NuxtLink>
                  <q-icon v-if="item.isBlocked" :name="icons.lock" size="14px" style="color: var(--danger)" :title="item.blockedNote || 'Bloqueado'" />
                  <template v-if="showTags">
                    <span v-for="tag in item.tags" :key="tag" class="tag" :style="{ background: project.tagsColors[tag] }" :title="tag"><span>{{ tag }}</span></span>
                  </template>
                </div>
                <span class="cell-status">
                  <span class="pick status-text" :style="{ '--status': item.status.color }">
                    {{ item.status.name }}
                    <template v-if="editable">
                      <q-icon :name="icons.down" size="14px" />
                      <MenuList :options="statuses" @pick="change(item, { statusId: $event })" />
                    </template>
                  </span>
                </span>
                <span class="cell-points">
                  <span class="points-chip" :class="{ pick: editable }" style="font-size: 13px">
                    {{ pointsLabel(project.points, item.points) }}
                    <MenuList v-if="editable" :options="pointsOptions" @pick="change(item, { points: pointsValue($event) })" />
                  </span>
                </span>
              </div>
            </VueDraggable>
          </ClientOnly>
          <p v-if="!backlog.length" class="empty-drop">
            {{ unplanned.length ? 'Nenhuma história com esses filtros.' : 'O backlog está vazio.' }}
          </p>
        </div>
      </div>
    </div>

    <aside class="sprints">
      <div class="heading">
        <span>{{ project.milestones.length }} Sprints</span>
        <button v-if="can('add_milestone') && !project.isArchived" class="btn icon" type="button" title="Adicionar sprint" @click="sprintForm = {}">
          <q-icon :name="icons.add" size="16px" />
        </button>
      </div>

      <div v-for="sprint in sprints" :key="sprint.id" class="sprint">
        <div class="sprint-head">
          <button class="icon-btn" type="button" :title="foldedSprints.includes(sprint.id) ? 'Abrir' : 'Recolher'" @click="toggleSprint(sprint.id)">
            <q-icon :name="foldedSprints.includes(sprint.id) ? icons.unfold : icons.down" size="18px" />
          </button>
          <div class="grow">
            <NuxtLink class="name" :to="`/project/${slug}/taskboard/${sprint.slug}`">{{ sprint.name }}</NuxtLink>
            <button v-if="can('modify_milestone') && !project.isArchived" class="icon-btn" type="button" title="Editar sprint" @click="sprintForm = { sprint }">
              <q-icon :name="icons.edit" size="14px" />
            </button>
            <span v-if="sprint.isClosed" class="chip">fechada</span>
            <div class="dates">{{ formatDay(sprint.estimatedStart) }}-{{ formatDay(sprint.estimatedFinish) }}</div>
          </div>
          <div class="numbers">
            <div><b>{{ number(sprint.pointsClosed) }}</b> fechado</div>
            <div><b>{{ number(Number(sprint.pointsClosed) + Number(sprint.pointsOpen)) }}</b> total</div>
          </div>
        </div>
        <div class="meter">
          <i :style="{ width: `${Number(sprint.pointsClosed) + Number(sprint.pointsOpen) ? (Number(sprint.pointsClosed) * 100) / (Number(sprint.pointsClosed) + Number(sprint.pointsOpen)) : 0}%` }" />
        </div>
        <template v-if="!foldedSprints.includes(sprint.id)">
          <ClientOnly>
            <VueDraggable
              v-model="bySprint[sprint.id]!"
              class="sprint-list"
              group="stories"
              filter="a"
              :prevent-on-filter="false"
              :animation="150"
              :disabled="!editable || sprint.isClosed"
              :data-milestone="sprint.id"
              @end="onDrop"
            >
              <div v-for="item in bySprint[sprint.id]" :key="item.id" class="sprint-row" :data-id="item.id">
                <NuxtLink :class="{ strike: item.status.isClosed }" :to="`/project/${slug}/us/${item.ref}`" style="color: inherit">
                  <span class="ref">#{{ item.ref }}</span>{{ item.subject }}
                </NuxtLink>
              </div>
            </VueDraggable>
          </ClientOnly>
          <NuxtLink class="btn gray big" :to="`/project/${slug}/taskboard/${sprint.slug}`" style="margin-top: 12px">Quadro de tarefas da sprint</NuxtLink>
        </template>
      </div>

      <p v-if="!project.milestones.length" class="muted">Ainda não há sprints neste projeto.</p>
      <p v-if="closedCount" style="text-align: center">
        <a href="#" class="pointer" @click.prevent="showClosed = !showClosed">{{ showClosed ? 'Ocultar sprints fechadas' : 'Mostrar sprints fechadas' }}</a>
      </p>
    </aside>

    <ItemLightbox v-if="creating" kind="us" :defaults="{ milestoneId: null }" position @saved="saved" @close="creating = false" />
    <BulkLightbox v-if="bulking" kind="us" :defaults="{ milestoneId: null }" position @saved="saved" @close="bulking = false" />
    <SprintLightbox v-if="sprintForm" :sprint="sprintForm.sprint" @saved="saved" @close="sprintForm = null" />
  </div>
</template>
