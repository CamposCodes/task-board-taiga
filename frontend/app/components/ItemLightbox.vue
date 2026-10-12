<script setup lang="ts">
import type { Choice, Item, Kind } from '~/utils/types'

const props = defineProps<{
  kind: Kind
  item?: Item
  defaults?: { statusId?: number; milestoneId?: number | null; parentId?: number | null }
  position?: boolean
}>()
const emit = defineEmits<{ saved: [item: Item]; close: [] }>()

const { project, refresh: refreshProject } = useProject()
const me = useMe()
const api = useApi()
const actions = useItemActions()

const TITLES: Record<Kind, [string, string]> = {
  us: ['Nova história de usuário', 'Editar história de usuário'],
  task: ['Nova tarefa', 'Editar tarefa'],
  issue: ['Novo problema', 'Editar problema'],
}
const HINTS: Record<Kind, string> = {
  us: 'Adicione um texto descritivo para ajudar os outros a entender melhor esta história de usuário',
  task: 'Adicione um texto descritivo para ajudar os outros a entender melhor esta tarefa',
  issue: 'Adicione um texto descritivo para ajudar os outros a entender melhor este problema',
}

const statuses = computed(() => project.value.statuses.filter((s) => s.kind === props.kind))
const usual = (list: Choice[]) => (list.find((c) => c.name.toLowerCase() === 'normal') ?? list[0])?.id ?? null

const form = reactive({
  subject: props.item?.subject ?? '',
  description: props.item?.description ?? '',
  tags: [...(props.item?.tags ?? [])],
  tagsColors: {} as Record<string, string>,
  statusId: props.item?.status.id ?? props.defaults?.statusId ?? (statuses.value.find((s) => !s.isClosed) ?? statuses.value[0])!.id,
  position: 'bottom' as 'bottom' | 'top',
  assigned: props.item?.assignedUsers.map((u) => u.id) ?? [],
  points: props.item?.points ?? null,
  typeId: props.item ? (props.item.issueType?.id ?? null) : usual(project.value.issueTypes),
  severityId: props.item ? (props.item.severity?.id ?? null) : usual(project.value.severities),
  priorityId: props.item ? (props.item.priority?.id ?? null) : usual(project.value.priorities),
  dueDate: props.item?.dueDate ?? null,
  teamRequirement: props.item?.teamRequirement ?? false,
  clientRequirement: props.item?.clientRequirement ?? false,
  isIocaine: props.item?.isIocaine ?? false,
  isBlocked: props.item?.isBlocked ?? false,
  blockedNote: props.item?.blockedNote ?? '',
})
const pending = ref<File[]>([])
const picking = ref(false)
const saving = ref(false)

const status = computed(() => statuses.value.find((s) => s.id === form.statusId)!)
const people = computed(() => project.value.members.filter((m) => form.assigned.includes(m.userId)))
const iAmMember = computed(() => project.value.members.some((m) => m.userId === me.value?.id))
const due = computed(() => dueState(form.dueDate))
const choice = (list: Choice[], id: number | null) => list.find((c) => c.id === id)

function setTags(tags: string[], colors: Record<string, string>) {
  form.tags = tags
  form.tagsColors = colors
}

async function submit() {
  saving.value = true
  try {
    const body: Record<string, unknown> = {
      subject: form.subject,
      description: form.description,
      tags: form.tags,
      tagsColors: form.tagsColors,
      statusId: form.statusId,
      dueDate: form.dueDate || null,
      isBlocked: form.isBlocked,
      blockedNote: form.isBlocked ? form.blockedNote : '',
    }
    if (props.kind === 'us') {
      Object.assign(body, {
        assignedUsers: form.assigned,
        points: form.points,
        teamRequirement: form.teamRequirement,
        clientRequirement: form.clientRequirement,
      })
    } else {
      body.assignedTo = form.assigned[0] ?? null
    }
    if (props.kind === 'task') {
      body.isIocaine = form.isIocaine
    }
    if (props.kind === 'issue') {
      Object.assign(body, { typeId: form.typeId, severityId: form.severityId, priorityId: form.priorityId })
    }
    let saved: Item
    if (props.item) {
      saved = await actions.update(props.item, body)
    } else {
      if (props.position) {
        body.position = form.position
      }
      if (props.defaults?.milestoneId !== undefined) {
        body.milestoneId = props.defaults.milestoneId
      }
      if (props.defaults?.parentId !== undefined && props.kind === 'task') {
        body.parentId = props.defaults.parentId
      }
      saved = await api<Item>(`/projects/${project.value.slug}/${kindPath[props.kind]}`, { method: 'POST', body })
      if (pending.value.length) {
        await actions.upload(saved.id, pending.value)
        saved.attachmentsCount = pending.value.length
      }
    }
    if (Object.keys(form.tagsColors).length) {
      await refreshProject()
    }
    emit('saved', saved)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppLightbox :title="TITLES[kind][item ? 1 : 0]" @close="emit('close')">
    <form @submit.prevent="submit">
      <div class="columns">
        <div class="grow">
          <div class="form-row">
            <input v-model="form.subject" class="field" placeholder="Assunto" aria-label="Assunto" maxlength="500" required v-focus>
          </div>
          <div class="form-row">
            <TagLine :tags="form.tags" :colors="project.tagsColors" editable @change="setTags" />
          </div>
          <div class="form-row">
            <MarkdownEditor v-model="form.description" label="Descrição" :placeholder="HINTS[kind]" :min-height="200" :actions="false" />
          </div>
          <AttachmentsBox v-model:pending="pending" :item-id="item?.id" editable />
        </div>

        <div class="side">
          <div class="status-select" :style="{ background: status.color, color: textColorOn(status.color) }">
            <span class="clip">{{ status.name }}</span>
            <q-icon :name="icons.down" size="18px" />
            <MenuList :options="statuses" @pick="form.statusId = $event" />
          </div>

          <div v-if="position && !item" class="form-row">
            <span class="label">Localização</span>
            <div class="row-center" style="justify-content: space-between">
              <label class="radio"><input v-model="form.position" type="radio" value="bottom"> embaixo</label>
              <label class="radio"><input v-model="form.position" type="radio" value="top"> acima</label>
            </div>
          </div>

          <div class="form-row row-center gap-8">
            <UserAvatar :user="people[0] ? asUser(people[0]) : null" :size="48" square />
            <div style="font-size: 15px; line-height: 20px">
              <div v-for="person in people" :key="person.userId">{{ person.fullName }}</div>
              <a href="#" class="pointer" @click.prevent="picking = true">{{ people.length ? 'Alterar' : 'Atribuir' }}</a>
              <template v-if="iAmMember && !form.assigned.includes(me!.id)">
                <span class="muted"> ou </span>
                <a href="#" class="pointer" @click.prevent="form.assigned = kind === 'us' ? [...form.assigned, me!.id] : [me!.id]">Atribuir a mim</a>
              </template>
            </div>
          </div>

          <div v-if="kind === 'us'" class="form-row">
            <span class="label">Pontos</span>
            <div class="side-row clickable">
              <span>total de pontos</span>
              <span class="value">{{ pointsLabel(project.points, form.points) }}</span>
              <MenuList :options="project.points" @pick="form.points = choice(project.points, $event)?.value ?? null" />
            </div>
          </div>

          <div v-if="kind === 'issue'" class="form-row">
            <div class="side-row clickable">
              <span>tipo</span>
              <span class="value">{{ choice(project.issueTypes, form.typeId)?.name }}<i class="dot" :style="{ background: choice(project.issueTypes, form.typeId)?.color }" /></span>
              <MenuList :options="project.issueTypes" @pick="form.typeId = $event" />
            </div>
            <div class="side-row clickable">
              <span>gravidade</span>
              <span class="value">{{ choice(project.severities, form.severityId)?.name }}<i class="dot" :style="{ background: choice(project.severities, form.severityId)?.color }" /></span>
              <MenuList :options="project.severities" @pick="form.severityId = $event" />
            </div>
            <div class="side-row clickable">
              <span>prioridade</span>
              <span class="value">{{ choice(project.priorities, form.priorityId)?.name }}<i class="dot" :style="{ background: choice(project.priorities, form.priorityId)?.color }" /></span>
              <MenuList :options="project.priorities" @pick="form.priorityId = $event" />
            </div>
          </div>

          <div class="row-center gap-8">
            <button
              class="btn square"
              :class="{ on: due === 'set', soon: due === 'soon', 'on red': due === 'past' }"
              type="button"
              :title="form.dueDate ? `Vence em ${formatDay(form.dueDate)}` : 'Definir data de vencimento'"
            >
              <q-icon :name="icons.clock" size="16px" />
              <q-popup-proxy>
                <div style="padding: 12px">
                  <span class="label">Data de vencimento</span>
                  <input v-model="form.dueDate" class="field" type="date" aria-label="Data de vencimento">
                </div>
              </q-popup-proxy>
            </button>
            <template v-if="kind === 'us'">
              <button
                class="btn square"
                :class="{ on: form.teamRequirement }"
                type="button"
                title="Requisito de equipe: precisa existir, mas não tem valor direto para o cliente"
                @click="form.teamRequirement = !form.teamRequirement"
              >
                <q-icon :name="icons.teamRequirement" size="16px" />
              </button>
              <button
                class="btn square"
                :class="{ on: form.clientRequirement }"
                type="button"
                title="Requisito de cliente: um pedido novo, que não estava previsto"
                @click="form.clientRequirement = !form.clientRequirement"
              >
                <q-icon :name="icons.clientRequirement" size="16px" />
              </button>
            </template>
            <button
              v-if="kind === 'task'"
              class="btn square"
              :class="{ on: form.isIocaine }"
              type="button"
              title="Iocaína: uma tarefa penosa, que alguém toma para poupar o time"
              @click="form.isIocaine = !form.isIocaine"
            >
              <q-icon :name="icons.iocaine" size="16px" />
            </button>
            <button
              class="btn square"
              :class="{ 'on red': form.isBlocked }"
              type="button"
              title="Bloquear este item, por exemplo quando depende de outra coisa"
              @click="form.isBlocked = !form.isBlocked"
            >
              <q-icon :name="icons.lock" size="16px" />
            </button>
          </div>
        </div>
      </div>

      <div v-if="form.isBlocked" class="form-row">
        <input v-model="form.blockedNote" class="field" placeholder="Por que este item está bloqueado?" aria-label="Motivo do bloqueio" maxlength="1000">
      </div>

      <button class="btn big" type="submit" :disabled="saving">{{ item ? 'Salvar' : 'Criar' }}</button>
    </form>

    <UserPicker
      v-if="picking"
      :title="kind === 'us' ? 'Selecionar responsáveis' : 'Selecionar responsável'"
      :members="project.members"
      :selected="form.assigned"
      :multiple="kind === 'us'"
      @pick="form.assigned = $event; picking = false"
      @close="picking = false"
    />
  </AppLightbox>
</template>
