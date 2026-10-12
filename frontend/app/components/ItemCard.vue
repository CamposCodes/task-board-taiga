<script setup lang="ts">
import type { Item } from '~/utils/types'

const props = defineProps<{ item: Item; zoom: number; editable?: boolean; deletable?: boolean; mini?: boolean; hideTags?: boolean }>()
const emit = defineEmits<{ edit: []; assign: []; remove: [] }>()

const { project } = useProject()
const to = computed(() => itemPath(project.value.slug, props.item))
const due = computed(() => dueState(props.item.dueDate, props.item.status.isClosed))
const isStory = computed(() => props.item.type === 'us')
const people = computed(() => (isStory.value ? props.item.assignedUsers : [props.item.assignedTo]))
const label = computed(() => `#${props.item.ref} ${props.item.subject}${props.item.isBlocked ? ' (bloqueado)' : ''}`)
const dueLabel = computed(() => `${due.value === 'past' ? 'Venceu em' : due.value === 'soon' ? 'Vence logo, em' : 'Vence em'} ${formatDay(props.item.dueDate)}`)
const stats = computed(() =>
  [
    { icon: icons.list, title: 'Tarefas fechadas / total', value: isStory.value && props.item.tasksTotal ? `${props.item.tasksDone}/${props.item.tasksTotal}` : '' },
    { icon: icons.comment, title: 'Comentários', value: props.item.commentsCount || '' },
    { icon: icons.attachment, title: 'Anexos', value: props.item.attachmentsCount || '' },
    { icon: icons.watch, title: 'Observadores', value: props.item.watchers.length || '' },
  ].filter((stat) => stat.value),
)
</script>

<template>
  <div
    class="card"
    :class="[isStory ? 'type-us' : 'type-task', `zoom-${zoom}`, { mini, blocked: item.isBlocked, iocaine: item.isIocaine, readonly: !editable }]"
    :data-id="item.id"
  >
    <NuxtLink v-if="mini" :to="to" :title="label" :aria-label="label">
      <UserAvatar :user="item.assignedTo" :size="30" />
    </NuxtLink>
    <div v-else class="card-inner">
      <div v-if="zoom > 0 && (editable || deletable)" class="card-actions">
        <button type="button" title="Ações do cartão" @click.stop>
          <q-icon :name="icons.more" size="15px" />
          <q-menu auto-close anchor="bottom right" self="top right" :transition-duration="120">
            <div class="menu-list">
              <button v-if="editable" class="option" type="button" @click="emit('edit')"><q-icon :name="icons.edit" size="18px" />Editar cartão</button>
              <button v-if="editable" class="option" type="button" @click="emit('assign')"><q-icon :name="icons.assign" size="18px" />Atribuir a</button>
              <button v-if="deletable" class="option" type="button" @click="emit('remove')"><q-icon :name="icons.trash" size="18px" />Excluir cartão</button>
            </div>
          </q-menu>
        </button>
      </div>

      <div v-if="zoom >= 2 && !hideTags && item.tags.length" class="card-tags">
        <span v-for="tag in item.tags" :key="tag" class="card-tag" :style="{ background: project.tagsColors[tag] }" :title="tag">
          <template v-if="zoom === 3">{{ tag }}</template>
        </span>
      </div>

      <h2 class="card-title">
        <NuxtLink :to="to" :title="`#${item.ref} ${item.subject}`">
          <span class="card-ref">#{{ item.ref }}</span>
          <q-icon v-if="item.isBlocked" class="card-lock" :name="icons.lock" size="14px" role="img" aria-hidden="false" :aria-label="item.blockedNote ? `Bloqueado: ${item.blockedNote}` : 'Bloqueado'" :title="item.blockedNote || 'Bloqueado'" />
          <span v-if="zoom > 0" class="card-subject">{{ item.subject }}</span>
        </NuxtLink>
      </h2>

      <div class="card-foot">
        <div v-if="people.length" class="card-assigned">
          <UserAvatar v-for="(user, index) in people" :key="user?.id ?? index" :user="user" :size="30" />
        </div>
        <div v-if="zoom >= 2" class="card-data">
          <div v-if="stats.length" class="card-stats">
            <span v-for="stat in stats" :key="stat.title" class="stat" :title="stat.title"><q-icon :name="stat.icon" size="14px" />{{ stat.value }}</span>
          </div>
          <q-icon
            v-if="due !== 'none'"
            class="mark"
            :name="due === 'past' ? icons.overdue : icons.clock"
            size="16px"
            :style="{ color: due === 'past' ? 'var(--danger)' : due === 'soon' ? 'var(--warning)' : undefined }"
            role="img"
            aria-hidden="false"
            :aria-label="dueLabel"
            :title="dueLabel"
          />
          <q-icon v-if="item.isIocaine" class="mark" :name="icons.iocaine" size="16px" style="color: var(--violet)" title="Iocaína" />
          <span v-if="isStory" class="card-estimation" title="Pontos">{{ pointsLabel(project.points, item.points) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
