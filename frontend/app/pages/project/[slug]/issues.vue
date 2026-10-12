<script setup lang="ts">
import type { Item } from '~/utils/types'

useHead({ title: 'Problemas' })

const { project, can } = useProject()
const actions = useItemActions()
const slug = project.value.slug

const { data, refresh } = await useAsyncData(`issues:${slug}`, () => fetchAll<Item>(`/projects/${slug}/issues`, { sort: '-modified' }))
const issues = computed(() => data.value ?? [])
const filters = useItemFilters(issues, 'issue', ['type', 'severity', 'priority', 'status', 'tags', 'assignedTo', 'role', 'createdBy'])
const showTags = usePreference('issues-tags', true)
const statuses = computed(() => project.value.statuses.filter((s) => s.kind === 'issue'))
const editable = computed(() => can('modify_issue') && !project.value.isArchived)
const canAdd = computed(() => can('add_issue') && !project.value.isArchived)

type SortKey = 'type' | 'severity' | 'priority' | 'ref' | 'status' | 'modified' | 'assigned'
const sort = ref<{ key: SortKey; desc: boolean }>({ key: 'modified', desc: true })
const COLUMNS: { key: SortKey; label: string }[] = [
  { key: 'type', label: 'Tipo' },
  { key: 'severity', label: 'Gravidade' },
  { key: 'priority', label: 'Prioridade' },
  { key: 'ref', label: 'Problema' },
  { key: 'status', label: 'Status' },
  { key: 'modified', label: 'Modificado' },
  { key: 'assigned', label: 'Atribuído a' },
]

const position = computed(() => {
  const p = project.value
  return new Map([...p.issueTypes, ...p.severities, ...p.priorities].map((c) => [c.id, c.sortOrder]))
})
const statusPosition = computed(() => new Map(statuses.value.map((s) => [s.id, s.sortOrder])))

function sortValue(item: Item, key: SortKey): string | number {
  switch (key) {
    case 'type':
      return position.value.get(item.issueType?.id ?? 0) ?? 0
    case 'severity':
      return position.value.get(item.severity?.id ?? 0) ?? 0
    case 'priority':
      return position.value.get(item.priority?.id ?? 0) ?? 0
    case 'ref':
      return item.ref
    case 'status':
      return statusPosition.value.get(item.status.id) ?? 0
    case 'modified':
      return item.modifiedAt
    default:
      return item.assignedTo?.fullName.toLowerCase() ?? '￿'
  }
}

const shown = computed(() => {
  const { key, desc } = sort.value
  return issues.value.filter(filters.matches).sort((a, b) => {
    const [x, y] = [sortValue(a, key), sortValue(b, key)]
    return (x < y ? -1 : x > y ? 1 : 0) * (desc ? -1 : 1)
  })
})

function sortBy(key: SortKey) {
  sort.value = { key, desc: sort.value.key === key ? !sort.value.desc : false }
}

const creating = ref(false)
const bulking = ref(false)
const assigning = ref<Item | null>(null)

async function saved() {
  creating.value = bulking.value = false
  await refresh()
}

async function change(issue: Item, changes: Record<string, unknown>) {
  await actions.update(issue, changes).catch(() => null)
  await refresh()
}
</script>

<template>
  <div class="section" style="padding-left: 32px">
    <h1 class="page-title">Problemas</h1>
    <BoardToolbar v-model:show-tags="showTags" :filters="filters">
      <template v-if="canAdd">
        <button class="btn" type="button" @click="creating = true"><q-icon :name="icons.add" size="16px" />Novo problema</button>
        <button class="btn icon gray" type="button" title="Adicionar problemas em lote" @click="bulking = true">
          <q-icon :name="icons.bulk" size="16px" />
        </button>
      </template>
    </BoardToolbar>

    <div style="display: flex; align-items: flex-start">
      <div class="grow">
        <div v-if="!issues.length" class="empty">
          <div class="art" />
          <p class="medium">Não há problemas para reportar :-)</p>
          <a href="#" v-if="canAdd" class="pointer" style="font-size: 16px" @click.prevent="creating = true">Você encontrou um problema?</a>
        </div>
        <table v-else class="table">
          <thead>
            <tr>
              <th v-for="column in COLUMNS" :key="column.key" class="sortable" @click="sortBy(column.key)">
                {{ column.label }}
                <q-icon v-if="sort.key === column.key" :name="sort.desc ? icons.sortDown : icons.sortUp" size="14px" class="teal" />
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="issue in shown" :key="issue.id">
              <td class="valued"><i class="dot" :style="{ background: issue.issueType?.color }" />{{ issue.issueType?.name }}</td>
              <td class="valued"><i class="dot" :style="{ background: issue.severity?.color }" />{{ issue.severity?.name }}</td>
              <td class="valued"><i class="dot" :style="{ background: issue.priority?.color }" />{{ issue.priority?.name }}</td>
              <td>
                <NuxtLink :class="{ strike: issue.status.isClosed }" :to="`/project/${slug}/issue/${issue.ref}`" style="color: var(--text)">
                  <span class="teal">#{{ issue.ref }}</span> {{ issue.subject }}
                </NuxtLink>
                <q-icon v-if="issue.isBlocked" :name="icons.lock" size="14px" style="margin-left: 6px; color: var(--danger)" :title="issue.blockedNote || 'Bloqueado'" />
                <template v-if="showTags">
                  <span v-for="tag in issue.tags" :key="tag" class="tag" style="margin-left: 4px; vertical-align: middle" :style="{ background: project.tagsColors[tag] }" :title="tag"><span>{{ tag }}</span></span>
                </template>
              </td>
              <td style="width: 210px">
                <span class="pick status-text" :style="{ '--status': issue.status.color }">
                  {{ issue.status.name }}
                  <template v-if="editable">
                    <q-icon :name="icons.down" size="14px" />
                    <MenuList :options="statuses" @pick="change(issue, { statusId: $event })" />
                  </template>
                </span>
              </td>
              <td style="width: 120px; white-space: nowrap">{{ formatDay(issue.modifiedAt.slice(0, 10)) }}</td>
              <td style="width: 210px">
                <span class="row-center gap-8" :class="{ pointer: editable }" @click="editable && (assigning = issue)">
                  <UserAvatar :user="issue.assignedTo" :size="24" />
                  <span class="clip">{{ issue.assignedTo?.fullName || 'Não atribuído' }}</span>
                </span>
              </td>
            </tr>
            <tr v-if="!shown.length">
              <td colspan="7" class="muted" style="padding: 24px; text-align: center">Nenhum problema com esses filtros.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <ItemLightbox v-if="creating" kind="issue" @saved="saved" @close="creating = false" />
    <BulkLightbox v-if="bulking" kind="issue" @saved="saved" @close="bulking = false" />
    <UserPicker
      v-if="assigning"
      title="Selecionar responsável"
      :members="project.members"
      :selected="assigning.assignedUsers.map((u) => u.id)"
      @pick="change(assigning, { assignedTo: $event[0] ?? null }); assigning = null"
      @close="assigning = null"
    />
  </div>
</template>
