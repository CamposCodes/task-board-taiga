<script setup lang="ts">
import type { Notice, NoticePage } from '~/utils/types'

const timeAgo = useTimeAgo()

const me = useMe()
const api = useApi()
const route = useRoute()

const { projects, loaded, refresh: refreshProjects } = useMyProjects()
if (me.value && !loaded.value) {
  await refreshProjects()
}
const pinned = computed(() => projects.value.filter((p) => p.my.isMember).slice(0, 4))

const { data: notices, refresh: refreshNotices } = await useAsyncData(
  'notices',
  () => (me.value ? api<NoticePage>('/notifications', { query: { unread: true, size: 10 } }).catch(() => null) : Promise.resolve(null)),
)
const noticesOpen = ref(false)
const kindIcons: Record<Notice['kind'], string> = {
  assigned: icons.assign,
  comment: icons.comment,
  create: icons.add,
  change: icons.edit,
  delete: icons.trash,
}
let timer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  timer = setInterval(() => document.visibilityState === 'visible' && refreshNotices(), 60_000)
  window.addEventListener('click', closeNotices)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  window.removeEventListener('click', closeNotices)
})
watch(() => route.fullPath, () => (noticesOpen.value = false))

function closeNotices() {
  noticesOpen.value = false
}

async function dismissAll() {
  await api('/notifications/read', { method: 'POST' })
  await refreshNotices()
}

async function openNotice(notice: Notice) {
  await api('/notifications/read', { method: 'POST', body: { ids: [notice.id] } })
  refreshNotices()
  const path = noticePath(notice)
  if (path) {
    await navigateTo(path)
  }
}

async function logout() {
  await api('/auth/logout', { method: 'POST' })
  me.value = null
  await navigateTo('/login')
}
</script>

<template>
  <div>
    <a class="skip" href="#content">Pular para o conteúdo</a>
    <header v-if="me" class="topbar">
      <NuxtLink to="/" class="logo" title="Início">
        <img src="/r2da-mark.svg" width="32" height="32" alt="R2DA">
      </NuxtLink>

      <div class="hover-menu">
        <NuxtLink to="/projects/" class="projects-chip">
          <q-icon :name="icons.folder" size="16px" />
          Projetos
        </NuxtLink>
        <div class="dropdown">
          <NuxtLink v-for="project in pinned" :key="project.id" class="entry" :to="`/project/${project.slug}`">
            <ProjectLogo :project="project" :size="24" />
            <span class="medium">{{ project.name }}</span>
          </NuxtLink>
          <hr v-if="pinned.length">
          <NuxtLink class="entry teal medium" to="/projects/">Ver todos os projetos</NuxtLink>
          <NuxtLink class="btn big" to="/project/new">
            <q-icon :name="icons.add" size="16px" />
            Novo projeto
          </NuxtLink>
        </div>
      </div>

      <div class="grow" />

      <NuxtLink to="/discover" class="tool" title="Descobrir projetos">
        <q-icon :name="icons.discover" size="22px" />
      </NuxtLink>
      <a href="/swagger-ui" target="_blank" rel="noopener" class="tool" title="Documentação da API">
        <q-icon :name="icons.help" size="22px" />
      </a>
      <div class="bell" @keydown.esc="noticesOpen = false">
        <div
          class="tool"
          title="Notificações"
          role="button"
          tabindex="0"
          aria-haspopup="true"
          :aria-expanded="noticesOpen"
          @click.stop="noticesOpen = !noticesOpen"
          @keydown.enter.prevent="noticesOpen = !noticesOpen"
          @keydown.space.prevent="noticesOpen = !noticesOpen"
        >
          <q-icon :name="icons.bell" size="22px" />
          <span v-if="notices?.unread" class="badge">{{ notices.unread }}</span>
        </div>
        <section v-if="noticesOpen" class="dropdown bell-panel" aria-label="Notificações" @click.stop>
          <header class="bell-head">
            <h2>Notificações</h2>
            <span v-if="notices?.unread" class="bell-count">{{ notices.unread }} {{ notices.unread === 1 ? 'nova' : 'novas' }}</span>
          </header>

          <div v-if="!notices?.items.length" class="bell-empty">
            <span class="bell-done"><q-icon :name="icons.check" size="20px" /></span>
            <b>Tudo em dia</b>
            <span>Nenhuma notificação nova.</span>
          </div>
          <ul v-else class="bell-list">
            <li v-for="notice in notices.items" :key="notice.id">
              <button type="button" class="bell-item" @click="openNotice(notice)">
                <span class="bell-who">
                  <UserAvatar :user="notice.actor" :size="36" />
                  <span class="bell-kind" :class="notice.kind"><q-icon :name="kindIcons[notice.kind]" size="12px" /></span>
                </span>
                <span class="bell-body">
                  <span class="bell-lead"><b>{{ notice.actor?.fullName || 'Alguém' }}</b> {{ noticeVerb(notice) }}</span>
                  <span class="bell-subject">
                    <span v-if="notice.itemType !== 'wiki'" class="bell-ref">#{{ notice.itemRef }}</span>
                    {{ notice.itemSubject }}
                  </span>
                  <span class="bell-meta">
                    {{ notice.project.name }}<i aria-hidden="true">·</i><time :datetime="notice.createdAt">{{ timeAgo(notice.createdAt) }}</time>
                  </span>
                </span>
              </button>
            </li>
          </ul>

          <footer class="bell-foot">
            <NuxtLink class="btn" to="/notifications">Ver todas</NuxtLink>
            <button type="button" class="btn gray" :disabled="!notices?.items.length" @click="dismissAll">Dispensar todas</button>
          </footer>
        </section>
      </div>

      <div class="hover-menu me" role="button" tabindex="0" aria-haspopup="true" aria-label="Menu da conta">
        <UserAvatar :user="me" :size="24" />
        <q-icon :name="icons.down" size="20px" />
        <div class="dropdown right">
          <div class="row-center gap-16" style="padding: 8px">
            <UserAvatar :user="me" :size="48" />
            <div>
              <div class="medium" style="font-size: 15px">{{ me.fullName }}</div>
              <div class="muted">{{ me.email }}</div>
              <NuxtLink class="medium" to="/user-settings/user-profile">Editar perfil</NuxtLink>
            </div>
          </div>
          <hr>
          <NuxtLink class="entry medium" to="/profile">Meu perfil</NuxtLink>
          <NuxtLink class="entry medium" to="/user-settings/user-profile">Configurações da conta</NuxtLink>
          <NuxtLink class="entry medium" to="/user-settings/api-tokens">Tokens de API</NuxtLink>
          <NuxtLink class="entry medium" to="/notifications">Notificações</NuxtLink>
          <NuxtLink v-if="me.isSuperuser" class="entry medium" to="/admin/users">Usuários do sistema</NuxtLink>
          <hr>
          <a href="#" class="entry teal medium" @click.prevent="logout">Sair</a>
        </div>
      </div>
    </header>

    <main id="content" class="page" tabindex="-1">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.hover-menu {
  position: relative;
}

.hover-menu > .dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  display: none;
}

.hover-menu > .dropdown.right {
  right: -8px;
  left: auto;
  min-width: 300px;
  transform-origin: top right;
}

.hover-menu:hover > .dropdown,
.hover-menu:focus-within > .dropdown {
  display: block;
}

.me {
  height: 48px;
}

.bell {
  position: relative;
  display: flex;
}

.bell-panel {
  position: absolute;
  top: 100%;
  right: -60px;
  display: flex;
  flex-direction: column;
  width: 400px;
  max-height: min(620px, calc(100vh - var(--topbar) - 16px));
  padding: 0;
  overflow: hidden;
  transform-origin: calc(100% - 80px) top;
  cursor: default;
}

.bell-head {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
}

.bell-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  line-height: 24px;
}

.bell-count {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-size: 12px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
}

.bell-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--border) transparent;
}

.bell-list li + li {
  border-top: 1px solid var(--line);
}

.bell-item {
  --row: var(--surface);
  display: flex;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  border: 0;
  background: var(--row);
  color: var(--text);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: background-color 120ms var(--ease);
}

.bell-item:active {
  --row: var(--fill-strong);
}

.bell-item:focus-visible {
  outline-offset: -2px;
}

.bell-who {
  position: relative;
  flex: none;
  height: 36px;
}

.bell-kind {
  position: absolute;
  right: -6px;
  bottom: -6px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--fill-strong);
  box-shadow: 0 0 0 2px var(--row);
  color: var(--icon);
  transition: box-shadow 120ms var(--ease);
}

.bell-kind.assigned {
  background: var(--brand);
  color: #fff;
}

.bell-kind.delete {
  background: var(--danger-soft);
  color: var(--danger);
}

.bell-body {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bell-lead {
  color: var(--text-2);
  font-size: 13px;
  line-height: 18px;
}

.bell-lead b {
  color: var(--text);
  font-weight: 500;
}

.bell-subject {
  display: -webkit-box;
  overflow: hidden;
  line-height: 20px;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.bell-ref {
  color: var(--brand);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.bell-meta {
  margin-top: 2px;
  overflow: hidden;
  color: var(--text-2);
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bell-meta i {
  margin: 0 6px;
  font-style: normal;
}

.bell-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 32px 16px;
  color: var(--text-2);
  font-size: 13px;
  line-height: 18px;
}

.bell-empty b {
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
}

.bell-done {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin-bottom: 10px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.bell-foot {
  display: flex;
  flex: none;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
}

.bell-panel .bell-foot .btn {
  flex: 1 1 0;
  margin-top: 0;
}

@media (hover: hover) and (pointer: fine) {
  .bell-item:hover {
    --row: var(--fill);
  }

  .bell-item:active {
    --row: var(--fill-strong);
  }
}

@media (max-width: 480px) {
  .bell-panel {
    position: fixed;
    top: var(--topbar);
    right: 8px;
    left: 8px;
    width: auto;
    transform-origin: top right;
  }
}

.dropdown .btn {
  margin-top: 4px;
}
</style>
