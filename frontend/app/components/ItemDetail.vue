<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { Choice, Comment, HistoryEntry, Item, Kind } from '~/utils/types'

const props = defineProps<{ kind: Kind }>()

const route = useRoute()
const api = useApi()
const me = useMe()
const actions = useItemActions()
const { project, can, refresh: refreshProject } = useProject()
const slug = project.value.slug
const ref_ = computed(() => Number(route.params.ref))

const { data, refresh } = await useAsyncData(
  () => `item:${slug}:${ref_.value}`,
  () => api<Item>(`/projects/${slug}/items/by-ref/${ref_.value}`, QUIET).catch(() => null),
  { watch: [ref_] },
)
if (!data.value) {
  throw createError({ statusCode: 404, statusMessage: 'Item não encontrado', fatal: true })
}
if (data.value.type !== props.kind) {
  await navigateTo(itemPath(slug, data.value), { replace: true })
}
const item = data as Ref<Item>
const { data: neighbors } = await useAsyncData(
  () => `neighbors:${item.value.id}`,
  () => api<{ previous: number | null; next: number | null }>(`/items/${item.value.id}/neighbors`),
)
useHead({ title: () => `#${item.value.ref} ${item.value.subject}` })

const editable = computed(() => can(permission('modify', props.kind)) && !project.value.isArchived)
const statuses = computed(() => project.value.statuses.filter((s) => s.kind === props.kind))
const due = computed(() => dueState(item.value.dueDate, item.value.status.isClosed))
const watching = computed(() => item.value.watchers.some((u) => u.id === me.value?.id))
const iAmMember = computed(() => project.value.members.some((m) => m.userId === me.value?.id))
const sprintPath = computed(() => (item.value.milestone ? `/project/${slug}/taskboard/${item.value.milestone.slug}` : null))
const back = computed(() => {
  if (props.kind === 'issue') {
    return { to: `/project/${slug}/issues`, title: 'Voltar para os problemas' }
  }
  if (sprintPath.value) {
    return { to: sprintPath.value, title: 'Voltar para o quadro de tarefas' }
  }
  return project.value.isKanbanActivated
    ? { to: `/project/${slug}/kanban`, title: 'Voltar para o kanban' }
    : { to: `/project/${slug}/backlog`, title: 'Voltar para o backlog' }
})

async function save(changes: Record<string, unknown>) {
  try {
    item.value = { ...(await actions.update(item.value, changes)), tasks: item.value.tasks }
    if (props.kind === 'us' && ('statusId' in changes || 'milestoneId' in changes)) {
      await refresh()
    }
  } catch {
    await refresh()
  }
  await loadHistory()
}

async function saveTags(tags: string[], colors: Record<string, string>) {
  const fresh = Object.keys(colors).some((tag) => tags.includes(tag) && !project.value.tagsColors[tag])
  await save({ tags, tagsColors: colors })
  if (fresh) {
    await refreshProject()
  }
}

const subjectDraft = ref<string | null>(null)
const descriptionDraft = ref<string | null>(null)

async function saveSubject() {
  const subject = subjectDraft.value?.trim()
  subjectDraft.value = null
  if (subject && subject !== item.value.subject) {
    await save({ subject })
  }
}

async function saveDescription() {
  const description = descriptionDraft.value ?? ''
  descriptionDraft.value = null
  if (description !== item.value.description) {
    await save({ description })
  }
}

const picking = ref<'assigned' | 'watchers' | null>(null)
const taskAssign = ref<Item | null>(null)

async function picked(ids: number[]) {
  const which = picking.value
  picking.value = null
  if (which === 'assigned') {
    await save(props.kind === 'us' ? { assignedUsers: ids } : { assignedTo: ids[0] ?? null })
  } else {
    await save({ watchers: ids })
  }
}

const unassign = (userId: number) =>
  save(props.kind === 'us' ? { assignedUsers: item.value.assignedUsers.map((u) => u.id).filter((id) => id !== userId) } : { assignedTo: null })

const assignToMe = () =>
  save(props.kind === 'us' ? { assignedUsers: [...item.value.assignedUsers.map((u) => u.id), me.value!.id] } : { assignedTo: me.value!.id })

async function toggleWatch() {
  item.value = { ...(await actions.watch(item.value, !watching.value)), tasks: item.value.tasks }
}

async function toggleBlock() {
  if (item.value.isBlocked) {
    await save({ isBlocked: false })
    return
  }
  const note = await askText('Bloquear', 'Por que este item está bloqueado?')
  if (note !== null) {
    await save({ isBlocked: true, blockedNote: note })
  }
}

async function remove() {
  if (await confirmDelete(`${kindLabels[props.kind].toLowerCase()} #${item.value.ref}`)) {
    await actions.remove(item.value)
    await navigateTo(back.value.to)
  }
}

const choiceOf = (list: Choice[], id: number | undefined) => list.find((c) => c.id === id)

const tasks = ref<Item[]>([])
watchEffect(() => {
  tasks.value = [...(item.value.tasks ?? [])]
})
const taskStatuses = computed(() => project.value.statuses.filter((s) => s.kind === 'task'))
const canTask = computed(() => can('modify_task') && !project.value.isArchived)
const addingTask = ref(false)
const editingTask = ref<Item | null>(null)

async function taskSaved() {
  addingTask.value = false
  editingTask.value = null
  await refresh()
}

async function updateTask(task: Item, changes: Record<string, unknown>) {
  await actions.update(task, changes).catch(() => null)
  await refresh()
}

async function removeTask(task: Item) {
  if (await confirmDelete(`a tarefa #${task.ref}`)) {
    await actions.remove(task)
    await refresh()
  }
}

async function taskDropped(event: { item: HTMLElement }) {
  const id = Number(event.item.dataset.id)
  const task = tasks.value.find((t) => t.id === id)
  const next = tasks.value[tasks.value.findIndex((t) => t.id === id) + 1]
  if (task) {
    await actions.move(task, { order: 'list', beforeId: next?.id ?? null }).catch(() => null)
    await refresh()
  }
}

const tab = ref<'comments' | 'activity'>('comments')
const { data: comments, refresh: loadComments } = await useAsyncData(
  () => `comments:${item.value.id}`,
  () => api<Comment[]>(`/items/${item.value.id}/comments`),
)
const { data: history, refresh: loadHistory } = await useAsyncData(
  () => `history:${item.value.id}`,
  () => api<HistoryEntry[]>(`/items/${item.value.id}/history`),
)
const changes = computed(() => (history.value ?? []).filter((entry) => entry.kind !== 'comment'))
const newComment = ref('')
const editingComment = ref<{ id: number; body: string } | null>(null)
const canComment = computed(() => can(permission('comment', props.kind)) && !project.value.isArchived)

async function addComment() {
  if (!newComment.value.trim()) {
    return
  }
  await api(`/items/${item.value.id}/comments`, { method: 'POST', body: { body: newComment.value } })
  newComment.value = ''
  await loadComments()
}

async function saveComment() {
  const draft = editingComment.value!
  await api(`/comments/${draft.id}`, { method: 'PATCH', body: { body: draft.body } })
  editingComment.value = null
  await loadComments()
}

async function removeComment(comment: Comment) {
  if (await confirmDelete('este comentário')) {
    await api(`/comments/${comment.id}`, { method: 'DELETE' })
    await loadComments()
  }
}
</script>

<template>
  <div class="detail">
    <div class="detail-main">
      <header class="detail-header">
        <nav v-if="neighbors?.previous || neighbors?.next" class="neighbors" aria-label="Itens vizinhos">
          <NuxtLink v-if="neighbors.previous" :to="`/project/${slug}/${kind}/${neighbors.previous}`" :title="`Anterior: #${neighbors.previous}`">
            <q-icon :name="icons.fold" size="19px" />
          </NuxtLink>
          <NuxtLink v-if="neighbors.next" :to="`/project/${slug}/${kind}/${neighbors.next}`" :title="`Próximo: #${neighbors.next}`">
            <q-icon :name="icons.unfold" size="19px" />
          </NuxtLink>
        </nav>
        <h1>
          <NuxtLink class="back" :to="back.to" :title="back.title" :aria-label="back.title">
            <q-icon :name="icons.fold" size="28px" />
          </NuxtLink>
          <span class="ref">#{{ item.ref }}</span>
          <input
            v-if="subjectDraft !== null"
            v-model="subjectDraft"
            class="field"
            aria-label="Assunto"
            maxlength="500"
            v-focus
            @keydown.enter="saveSubject"
            @keydown.esc="subjectDraft = null"
            @blur="saveSubject"
          >
          <span v-else :class="{ pointer: editable }" :title="editable ? 'Clique para editar' : undefined" @click="editable && (subjectDraft = item.subject)">
            {{ item.subject }}
            <q-icon v-if="editable" class="pencil" :name="icons.edit" size="16px" />
          </span>
        </h1>
        <div class="kind">{{ kindLabels[kind] }}</div>
        <p v-if="item.parent" class="belongs">
          Esta tarefa pertence a
          <NuxtLink :to="`/project/${slug}/us/${item.parent.ref}`">#{{ item.parent.ref }} {{ item.parent.subject }}</NuxtLink>
        </p>
        <div class="subheader">
          <TagLine :tags="item.tags" :colors="project.tagsColors" :editable="editable" @change="saveTags" />
          <div class="created">
            <div>
              <NuxtLink v-if="item.createdBy" :to="`/profile/${item.createdBy.username}`">Criado por {{ item.createdBy.fullName }}</NuxtLink>
              <div>{{ formatDateTime(item.createdAt) }}</div>
            </div>
            <UserAvatar :user="item.createdBy" :size="32" />
          </div>
        </div>
        <p v-if="item.isBlocked" class="blocked-banner">
          <span class="medium">Bloqueado</span><template v-if="item.blockedNote">: {{ item.blockedNote }}</template>
        </p>
      </header>

      <div class="detail-content">
        <section class="description" :class="{ editable }">
          <template v-if="descriptionDraft !== null">
            <MarkdownEditor
              :model-value="descriptionDraft"
              label="Descrição"
              :min-height="220"
              autofocus
              @update:model-value="descriptionDraft = $event"
              @save="saveDescription"
              @cancel="descriptionDraft = null"
            />
          </template>
          <div v-else @click="editable && (descriptionDraft = item.description)">
            <ClientOnly v-if="item.description">
              <MarkdownView :source="item.description" />
            </ClientOnly>
            <p v-else class="placeholder">
              {{ editable ? 'O espaço vazio é tão chato... vá em frente, seja descritivo...' : 'Sem descrição ainda' }}
            </p>
          </div>
        </section>

        <AttachmentsBox :item-id="item.id" :editable="editable" @changed="loadHistory()" />

        <section v-if="kind === 'us'" style="margin-bottom: 32px">
          <div class="box-head" style="background: var(--fill); color: var(--brand-deep)">
            <span>Tarefas</span>
            <button v-if="can('add_task') && !project.isArchived" class="btn icon" type="button" title="Adicionar tarefa" @click="addingTask = true">
              <q-icon :name="icons.add" size="16px" />
            </button>
          </div>
          <ClientOnly>
            <VueDraggable v-model="tasks" handle=".handle" :animation="150" :disabled="!canTask" @end="taskDropped">
              <div v-for="task in tasks" :key="task.id" class="task-row" :data-id="task.id">
                <q-icon v-if="canTask" class="handle muted" :name="icons.drag" size="16px" style="cursor: grab" />
                <NuxtLink class="grow" :class="{ strike: task.status.isClosed }" :to="`/project/${slug}/task/${task.ref}`" style="color: var(--text)">
                  <span class="teal">#{{ task.ref }}</span> {{ task.subject }}
                </NuxtLink>
                <span v-if="canTask" class="tools">
                  <button class="icon-btn" type="button" title="Editar" @click="editingTask = task"><q-icon :name="icons.edit" size="16px" /></button>
                  <button v-if="can('delete_task')" class="icon-btn" type="button" title="Excluir" @click="removeTask(task)">
                    <q-icon :name="icons.trash" size="16px" />
                  </button>
                </span>
                <span class="pick status-text" :style="{ '--status': task.status.color, flex: '0 0 150px' }">
                  <span class="clip">{{ task.status.name }}</span>
                  <template v-if="canTask">
                    <q-icon :name="icons.down" size="14px" />
                    <MenuList :options="taskStatuses" @pick="updateTask(task, { statusId: $event })" />
                  </template>
                </span>
                <span class="row-center gap-8" :class="{ pointer: canTask }" style="flex: 0 0 150px" @click="canTask && (taskAssign = task)">
                  <UserAvatar :user="task.assignedTo" :size="32" square />
                  <span class="clip">{{ task.assignedTo?.fullName || 'Não atribuído' }}</span>
                </span>
              </div>
            </VueDraggable>
          </ClientOnly>
        </section>

        <nav class="tabs">
          <button class="tab" :class="{ active: tab === 'comments' }" type="button" @click="tab = 'comments'">{{ comments?.length ?? 0 }} comentários</button>
          <button class="tab" :class="{ active: tab === 'activity' }" type="button" @click="tab = 'activity'">{{ changes.length }} atividades</button>
        </nav>

        <section v-if="tab === 'comments'">
          <form v-if="canComment" style="margin-top: 16px" @submit.prevent="addComment">
            <MarkdownEditor
              v-model="newComment"
              label="Novo comentário"
              placeholder="Escreva um novo comentário aqui"
              :min-height="96"
              compact
              save-label="Comentar"
              @save="addComment"
              @cancel="newComment = ''"
            />
          </form>
          <div v-for="comment in [...(comments ?? [])].reverse()" :key="comment.id" class="log-entry">
            <UserAvatar :user="comment.author" :size="48" square />
            <div class="grow">
              <span class="who">{{ comment.author?.fullName || 'Alguém' }}</span>
              <span class="when">{{ formatDateTime(comment.createdAt) }}<template v-if="comment.editedAt"> (editado)</template></span>
              <template v-if="editingComment?.id === comment.id">
                <MarkdownEditor
                  v-model="editingComment.body"
                  label="Editar comentário"
                  style="margin-top: 8px"
                  autofocus
                  @save="saveComment"
                  @cancel="editingComment = null"
                />
              </template>
              <ClientOnly v-else>
                <MarkdownView :source="comment.body" style="margin-top: 10px" />
              </ClientOnly>
            </div>
            <div v-if="canComment && editingComment?.id !== comment.id" class="row-center">
              <button v-if="comment.author?.id === me?.id" class="icon-btn" type="button" title="Editar comentário" @click="editingComment = { id: comment.id, body: comment.body }">
                <q-icon :name="icons.edit" size="16px" />
              </button>
              <button v-if="comment.author?.id === me?.id || project.my.isAdmin" class="icon-btn" type="button" title="Excluir comentário" @click="removeComment(comment)">
                <q-icon :name="icons.trash" size="16px" />
              </button>
            </div>
          </div>
        </section>

        <section v-else>
          <div v-for="entry in changes" :key="entry.id" class="log-entry">
            <UserAvatar :user="entry.actor" :size="48" square />
            <div class="grow">
              <span class="who">{{ entry.actor?.fullName || 'Alguém' }}</span>
              <span class="when">{{ formatDateTime(entry.createdAt) }}</span>
              <span v-if="entry.viaToken" class="chip" style="margin-left: 8px">via token</span>
              <div v-if="entry.kind === 'create'" class="change"><span class="field-name">criação</span></div>
              <div v-for="change in historyChanges(entry)" :key="change.field" class="change">
                <span class="field-name">{{ change.field }}</span>
                <template v-if="change.before">
                  <span>{{ change.before }}</span>
                  <q-icon :name="icons.unfold" size="14px" class="muted" />
                </template>
                <span>{{ change.after }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <aside class="detail-side">
      <div class="state">
        <span class="open">{{ item.status.isClosed ? 'Fechado' : 'Aberto' }}</span>
        <span class="status-badge" :style="{ background: item.status.color, color: textColorOn(item.status.color), cursor: editable ? 'pointer' : 'default' }">
          {{ item.status.name }}
          <template v-if="editable">
            <q-icon :name="icons.down" size="14px" />
            <MenuList :options="statuses" @pick="save({ statusId: $event })" />
          </template>
        </span>
      </div>

      <section v-if="kind === 'us'" class="side-section">
        <span class="label">Pontos</span>
        <div class="side-row" :class="{ clickable: editable }">
          <span>total de pontos</span>
          <span class="value">{{ pointsLabel(project.points, item.points) }}</span>
          <MenuList v-if="editable" :options="project.points.map((p) => ({ id: p.id, name: p.name }))" @pick="save({ points: choiceOf(project.points, $event)?.value ?? null })" />
        </div>
      </section>

      <section v-if="kind === 'issue'" class="side-section">
        <div class="side-row" :class="{ clickable: editable }">
          <span>tipo</span>
          <span class="value">{{ item.issueType?.name || 'não definido' }}<i class="dot" :style="{ background: item.issueType?.color }" /></span>
          <MenuList v-if="editable" :options="project.issueTypes" @pick="save({ typeId: $event })" />
        </div>
        <div class="side-row" :class="{ clickable: editable }">
          <span>gravidade</span>
          <span class="value">{{ item.severity?.name || 'não definida' }}<i class="dot" :style="{ background: item.severity?.color }" /></span>
          <MenuList v-if="editable" :options="project.severities" @pick="save({ severityId: $event })" />
        </div>
        <div class="side-row" :class="{ clickable: editable }">
          <span>prioridade</span>
          <span class="value">{{ item.priority?.name || 'não definida' }}<i class="dot" :style="{ background: item.priority?.color }" /></span>
          <MenuList v-if="editable" :options="project.priorities" @pick="save({ priorityId: $event })" />
        </div>
      </section>

      <section class="side-section">
        <span class="label">Atribuído</span>
        <div v-for="user in item.assignedUsers" :key="user.id" class="person">
          <UserAvatar :user="user" :size="48" square />
          <span>{{ user.fullName }}</span>
          <button v-if="editable" class="icon-btn remove" type="button" title="Remover atribuição" @click="unassign(user.id)">
            <q-icon :name="icons.close" size="16px" />
          </button>
        </div>
        <div v-if="editable" class="side-actions">
          <button v-if="kind === 'us' || !item.assignedUsers.length" class="side-action" type="button" @click="picking = 'assigned'">
            <q-icon :name="icons.add" size="14px" />Adicionar responsável
          </button>
          <button v-else class="side-action" type="button" @click="picking = 'assigned'">Alterar responsável</button>
          <button v-if="iAmMember && !item.assignedUsers.some((u) => u.id === me?.id)" class="side-action" type="button" @click="assignToMe">Atribuir a mim</button>
        </div>
      </section>

      <section class="side-section">
        <span class="label">Observadores</span>
        <div v-for="user in item.watchers" :key="user.id" class="person">
          <UserAvatar :user="user" :size="32" square />
          <span>{{ user.fullName }}</span>
        </div>
        <div class="side-actions">
          <button v-if="editable" class="side-action" type="button" @click="picking = 'watchers'">
            <q-icon :name="icons.add" size="14px" />Adicionar observadores
          </button>
          <button class="side-action" type="button" @click="toggleWatch">
            <q-icon :name="icons.watch" size="14px" />{{ watching ? 'Deixar de observar' : 'Observar' }}
          </button>
        </div>
      </section>

      <div class="side-buttons">
        <button
          class="btn square"
          :class="{ on: due === 'set', soon: due === 'soon', 'on red': due === 'past' }"
          type="button"
          :disabled="!editable"
          :title="item.dueDate ? `Vence em ${formatDay(item.dueDate)}` : 'Definir data de vencimento'"
        >
          <q-icon :name="icons.clock" size="16px" />
          <q-popup-proxy v-if="editable">
            <div style="padding: 12px">
              <span class="label">Data de vencimento</span>
              <input class="field" type="date" aria-label="Data de vencimento" :value="item.dueDate" @change="save({ dueDate: ($event.target as HTMLInputElement).value || null })">
            </div>
          </q-popup-proxy>
        </button>
        <template v-if="kind === 'us'">
          <button class="btn square" :class="{ on: item.teamRequirement }" type="button" :disabled="!editable" title="Requisito de equipe" @click="save({ teamRequirement: !item.teamRequirement })">
            <q-icon :name="icons.teamRequirement" size="16px" />
          </button>
          <button class="btn square" :class="{ on: item.clientRequirement }" type="button" :disabled="!editable" title="Requisito de cliente" @click="save({ clientRequirement: !item.clientRequirement })">
            <q-icon :name="icons.clientRequirement" size="16px" />
          </button>
        </template>
        <button v-if="kind === 'task'" class="btn square" :class="{ on: item.isIocaine }" type="button" :disabled="!editable" title="Iocaína: uma tarefa penosa" @click="save({ isIocaine: !item.isIocaine })">
          <q-icon :name="icons.iocaine" size="16px" />
        </button>
        <button class="btn square" :class="{ 'on red': item.isBlocked }" type="button" :disabled="!editable" :title="item.isBlocked ? 'Desbloquear' : 'Bloquear'" @click="toggleBlock">
          <q-icon :name="icons.lock" size="16px" />
        </button>
        <button v-if="can(permission('delete', kind)) && !project.isArchived" class="btn square" type="button" title="Excluir" @click="remove">
          <q-icon :name="icons.trash" size="16px" />
        </button>
      </div>
    </aside>

    <UserPicker
      v-if="picking"
      :title="picking === 'watchers' ? 'Adicionar observadores' : kind === 'us' ? 'Selecionar responsáveis' : 'Selecionar responsável'"
      :members="project.members"
      :selected="(picking === 'watchers' ? item.watchers : item.assignedUsers).map((u) => u.id)"
      :multiple="picking === 'watchers' || kind === 'us'"
      @pick="picked"
      @close="picking = null"
    />
    <UserPicker
      v-if="taskAssign"
      title="Selecionar responsável"
      :members="project.members"
      :selected="taskAssign.assignedUsers.map((u) => u.id)"
      @pick="updateTask(taskAssign, { assignedTo: $event[0] ?? null }); taskAssign = null"
      @close="taskAssign = null"
    />
    <ItemLightbox v-if="addingTask" kind="task" :defaults="{ parentId: item.id }" @saved="taskSaved" @close="addingTask = false" />
    <ItemLightbox v-if="editingTask" kind="task" :item="editingTask" @saved="taskSaved" @close="editingTask = null" />
  </div>
</template>
