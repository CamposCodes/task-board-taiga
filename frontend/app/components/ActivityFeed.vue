<script setup lang="ts">
import type { HistoryEntry, Page } from '~/utils/types'

const timeAgo = useTimeAgo()

const props = defineProps<{ url: string }>()
const api = useApi()

const { data: first } = await useAsyncData(
  () => `feed:${props.url}`,
  () => api<Page<HistoryEntry>>(props.url, { query: { size: 30 } }),
)
const entries = ref<HistoryEntry[]>(first.value?.items ?? [])
const total = ref(first.value?.total ?? 0)
const page = ref(1)
const loading = ref(false)

async function more() {
  loading.value = true
  try {
    const next = await api<Page<HistoryEntry>>(props.url, { query: { size: 30, page: page.value + 1 } })
    entries.value.push(...next.items)
    total.value = next.total
    page.value++
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section style="border-top: 1px solid var(--line)">
    <p v-if="!entries.length" class="muted" style="padding: 24px 0">Nada aconteceu por aqui ainda.</p>
    <div v-for="entry in entries" :key="entry.id" class="activity">
      <span class="date">{{ timeAgo(entry.createdAt) }}</span>
      <UserAvatar :user="entry.actor" :size="32" />
      <p>
        <NuxtLink v-if="entry.actor" class="who" :to="`/profile/${entry.actor.username}`">{{ entry.actor.fullName }}</NuxtLink>
        <span v-else class="who">Alguém</span>
        {{ historyVerb(entry) }}
        <NuxtLink v-if="entry.kind !== 'delete'" class="medium" :to="itemPath(entry.project.slug, entry.target)">
          <template v-if="entry.target.type !== 'wiki'">#{{ entry.target.ref }}</template> {{ entry.target.subject }}
        </NuxtLink>
        <span v-else class="medium">
          <template v-if="entry.target.type !== 'wiki'">#{{ entry.target.ref }}</template> {{ entry.target.subject }}
        </span>
        <template v-if="historyNewValue(entry)">
          para <span style="color: var(--text)">{{ historyNewValue(entry) }}</span>
        </template>
        <span v-if="entry.viaToken" class="chip" style="margin-left: 6px" title="Feito por uma integração, com o token da pessoa">
          via token
        </span>
      </p>
    </div>
    <div v-if="entries.length < total" style="padding: 16px 0; text-align: center">
      <button class="btn gray" type="button" :disabled="loading" @click="more">Carregar mais</button>
    </div>
  </section>
</template>
