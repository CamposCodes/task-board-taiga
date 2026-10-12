<script setup lang="ts">
import type { Notice, NoticePage } from '~/utils/types'

const timeAgo = useTimeAgo()

useHead({ title: 'Notificações - Taskboard' })

const api = useApi()
const onlyUnread = ref(false)
const page = ref(1)
const { data, refresh } = await useAsyncData(
  'all-notices',
  () => api<NoticePage>('/notifications', { query: { unread: onlyUnread.value, page: page.value, size: 30 } }),
  { watch: [onlyUnread, page] },
)
const pages = computed(() => Math.max(1, Math.ceil((data.value?.total ?? 0) / 30)))

async function dismissAll() {
  await api('/notifications/read', { method: 'POST' })
  await Promise.all([refresh(), refreshNuxtData('notices')])
}

async function open(notice: Notice) {
  if (!notice.read) {
    await api('/notifications/read', { method: 'POST', body: { ids: [notice.id] } })
    refreshNuxtData('notices')
  }
  const path = noticePath(notice)
  if (path) {
    await navigateTo(path)
  } else {
    await refresh()
  }
}
</script>

<template>
  <div class="centered" style="max-width: 960px">
    <div class="title-bar" style="margin-bottom: 8px; padding: 12px 16px">
      <h1 class="page-title" style="margin: 0; font-size: 19.2px; text-transform: uppercase">Meus eventos</h1>
      <div class="row-center gap-16">
        <button type="button" class="switch" :class="{ on: onlyUnread }" role="switch" :aria-checked="onlyUnread" @click="onlyUnread = !onlyUnread; page = 1">
          <i />Só não lidas
        </button>
        <a href="#" v-if="data?.unread" class="pointer" @click.prevent="dismissAll">Dispensar todas</a>
      </div>
    </div>

    <p v-if="!data?.items.length" class="muted" style="padding: 32px; text-align: center">Nenhuma notificação.</p>
    <div
      v-for="notice in data?.items"
      :key="notice.id"
      class="row-center gap-16 pointer"
      style="margin-bottom: 8px; padding: 12px"
      :style="{ background: notice.read ? 'var(--surface)' : 'var(--brand-soft)' }"
      @click="open(notice)"
    >
      <UserAvatar :user="notice.actor" :size="40" />
      <div class="grow">
        <span class="medium">{{ notice.actor?.fullName || 'Alguém' }}</span>
        {{ noticeVerb(notice) }}
        <span class="teal medium">{{ noticeItem(notice) }}</span>
        <div class="muted" style="margin-top: 4px; font-size: 12px">{{ notice.project.name }}</div>
      </div>
      <span class="muted" style="font-size: 12px; white-space: nowrap">
        {{ timeAgo(notice.createdAt) }}<span v-if="!notice.read" style="color: var(--danger)"> •</span>
      </span>
    </div>

    <div v-if="pages > 1" class="row-center gap-16" style="justify-content: center; padding: 16px">
      <button class="btn gray" type="button" :disabled="page <= 1" @click="page--">Anteriores</button>
      <span class="muted">{{ page }} de {{ pages }}</span>
      <button class="btn gray" type="button" :disabled="page >= pages" @click="page++">Próximas</button>
    </div>
  </div>
</template>
