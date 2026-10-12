<script setup lang="ts">
import { NuxtLink } from '#components'

const { project, can } = useProject()
const route = useRoute()
const collapsed = usePreference('project-menu-collapsed', true)

const base = computed(() => `/project/${project.value.slug}`)
const active = (...sections: string[]) => sections.some((section) => route.path.startsWith(`${base.value}/${section}`))

interface Entry {
  key: string
  label: string
  icon: string
  to: string
  active: boolean
}

const scrum = computed(() => project.value.isBacklogActivated && can('view_us'))
const scrumActive = computed(() => active('backlog', 'taskboard'))
const scrumOpen = ref(scrumActive.value)
watch(scrumActive, (now) => now && (scrumOpen.value = true))
const scrumLinks = computed(() => [
  { label: 'Backlog', to: `${base.value}/backlog`, active: active('backlog') },
  ...project.value.milestones
    .filter((m) => !m.isClosed)
    .slice(0, 7)
    .map((m) => ({ label: m.name, to: `${base.value}/taskboard/${m.slug}`, active: route.params.sprint === m.slug })),
])

const main = computed<Entry[]>(() => {
  const p = project.value
  const entries: Entry[] = []
  if (p.isKanbanActivated && can('view_us')) {
    entries.push({ key: 'kanban', label: 'Kanban', icon: icons.kanban, to: `${base.value}/kanban`, active: active('kanban') })
  }
  if (p.isIssuesActivated && can('view_issues')) {
    entries.push({ key: 'issues', label: 'Problemas', icon: icons.issues, to: `${base.value}/issues`, active: active('issues', 'issue/') })
  }
  return entries
})

const secondary = computed<Entry[]>(() => {
  const p = project.value
  const entries: Entry[] = [{ key: 'search', label: 'Procurar', icon: icons.search, to: `${base.value}/search`, active: active('search') }]
  if (p.isWikiActivated && can('view_wiki_pages')) {
    entries.push({ key: 'wiki', label: 'Wiki', icon: icons.wiki, to: `${base.value}/wiki`, active: active('wiki') })
  }
  entries.push({ key: 'team', label: 'Time', icon: icons.team, to: `${base.value}/team`, active: active('team') })
  if (p.my.isAdmin) {
    entries.push({ key: 'admin', label: 'Configurações', icon: icons.settings, to: `${base.value}/admin/project-profile/details`, active: active('admin') })
  }
  return entries
})

const flyout = ref<{ key: string; label: string; to?: string; top: number } | null>(null)
let closing: ReturnType<typeof setTimeout> | undefined

function show(event: MouseEvent, key: string, label: string, to?: string) {
  if (!collapsed.value) {
    return
  }
  clearTimeout(closing)
  const item = event.currentTarget as HTMLElement
  const nav = item.closest('nav') as HTMLElement
  flyout.value = { key, label, to, top: item.getBoundingClientRect().top - nav.getBoundingClientRect().top }
}

function keep() {
  clearTimeout(closing)
}

function hide() {
  closing = setTimeout(() => (flyout.value = null), 120)
}

watch(() => route.fullPath, () => (flyout.value = null))

function toggleScrum() {
  if (collapsed.value) {
    navigateTo(`${base.value}/backlog`)
  } else {
    scrumOpen.value = !scrumOpen.value
  }
}
</script>

<template>
  <nav class="pmenu" :class="{ collapsed }" aria-label="Menu do projeto">
    <div class="scroller">
      <NuxtLink class="head" :to="`${base}/timeline`" :title="project.name">
        <ProjectLogo :project="project" :size="24" />
        <span class="text name">{{ project.name }}</span>
      </NuxtLink>

      <ul class="group">
        <li v-if="scrum" @mouseenter="show($event, 'scrum', 'Scrum')" @mouseleave="hide">
          <button class="option" :class="{ active: scrumActive }" type="button" title="Scrum" :aria-expanded="scrumOpen" @click="toggleScrum">
            <q-icon :name="icons.scrum" size="24px" />
            <span class="text">Scrum</span>
            <q-icon class="text chevron" :name="scrumOpen ? icons.up : icons.down" size="18px" />
          </button>
          <ul v-if="!collapsed && scrumOpen" class="children">
            <li v-for="link in scrumLinks" :key="link.to">
              <NuxtLink :class="{ active: link.active }" :to="link.to" :title="link.label"><span>{{ link.label }}</span></NuxtLink>
            </li>
          </ul>
        </li>
        <li v-for="entry in main" :key="entry.key" @mouseenter="show($event, entry.key, entry.label, entry.to)" @mouseleave="hide">
          <NuxtLink class="option" :class="{ active: entry.active }" :to="entry.to" :title="entry.label">
            <q-icon :name="entry.icon" size="24px" />
            <span class="text">{{ entry.label }}</span>
          </NuxtLink>
        </li>
      </ul>

      <ul class="group secondary">
        <li v-for="entry in secondary" :key="entry.key" @mouseenter="show($event, entry.key, entry.label, entry.to)" @mouseleave="hide">
          <NuxtLink class="option" :class="{ active: entry.active }" :to="entry.to" :title="entry.label">
            <q-icon :name="entry.icon" size="24px" />
            <span class="text">{{ entry.label }}</span>
          </NuxtLink>
        </li>
      </ul>

      <button class="collapse" type="button" :title="collapsed ? 'Abrir menu' : 'Recolher menu'" @click="collapsed = !collapsed; flyout = null">
        <span class="text">recolher menu</span>
        <q-icon :name="collapsed ? icons.expand : icons.collapse" size="24px" />
      </button>
    </div>

    <div v-if="collapsed && flyout" class="flyout" :style="{ top: `${flyout.top}px` }" @mouseenter="keep" @mouseleave="hide">
      <component :is="flyout.to ? NuxtLink : 'div'" class="flyout-head" :to="flyout.to">{{ flyout.label }}</component>
      <ul v-if="flyout.key === 'scrum'" class="children">
        <li v-for="link in scrumLinks" :key="link.to">
          <NuxtLink :class="{ active: link.active }" :to="link.to" :title="link.label"><span>{{ link.label }}</span></NuxtLink>
        </li>
      </ul>
    </div>
  </nav>
</template>

<style scoped>
.pmenu {
  position: sticky;
  z-index: 100;
  top: var(--topbar);
  flex: none;
  width: 200px;
  height: calc(100vh - var(--topbar));
  background: linear-gradient(180deg, var(--brand), var(--chrome));
}

.pmenu.collapsed {
  width: 48px;
}

.scroller {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden auto;
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.head {
  display: flex;
  flex: none;
  align-items: center;
  min-height: 56px;
  margin-bottom: 12px;
  padding: 16px 16px 16px 12px;
  box-shadow: inset 0 -1px 0 var(--chrome-line);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  line-height: 17.5px;
}

.head .logo-tile {
  flex: none;
  margin-right: 12px;
}

.head .name {
  overflow: hidden;
}

.group {
  flex: none;
  padding-bottom: 8px;
}

.group > li {
  margin-bottom: 8px;
}

.secondary {
  margin-top: auto;
  border-top: 1px solid var(--chrome-line);
  border-bottom: 1px solid var(--chrome-line);
}

.option {
  display: flex;
  align-items: center;
  width: 100%;
  height: 40px;
  padding: 0 8px 0 0;
  border: 0;
  position: relative;
  background: transparent;
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  line-height: 17.5px;
  white-space: nowrap;
  cursor: pointer;
}

.option::before,
.children a::before {
  position: absolute;
  inset: 2px 6px;
  border-radius: 10px;
  content: '';
  transition: background-color 160ms var(--ease);
}

.option > *,
.children a > span {
  position: relative;
}

.option .q-icon:first-child {
  flex: none;
  margin: 0 12px;
}

.option:hover::before,
.children a:hover::before {
  background: rgba(255, 255, 255, 0.08);
}

.option.active {
  color: var(--brand-deep);
  font-weight: 700;
}

.option.active::before {
  background: var(--brand-soft);
}

.children a.active {
  color: #fff;
  font-weight: 700;
}

.children a.active::before {
  background: rgba(255, 255, 255, 0.16);
}

.option.active .q-icon {
  color: var(--brand-deep);
}

.chevron {
  margin-left: auto;
  color: var(--chrome-signal);
}

.children {
  padding-bottom: 8px;
}

.children a {
  display: flex;
  align-items: center;
  height: 40px;
  position: relative;
  padding: 0 8px 0 40px;
  color: #e1eeeb;
  font-size: 14px;
  line-height: 17.5px;
}

.children a span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collapse {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
  height: 40px;
  padding: 8px;
  border: 0;
  background: transparent;
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.collapse .q-icon {
  margin-left: 8px;
  padding: 4px;
  border-radius: 50%;
  background: var(--brand);
  color: var(--chrome-signal);
}

.collapsed .text {
  display: none;
}

.collapsed .collapse {
  justify-content: center;
}

.collapsed .collapse .q-icon {
  margin-left: 0;
}

.flyout {
  position: absolute;
  left: 48px;
  width: 170px;
  background: var(--chrome);
  transition:
    opacity 150ms var(--ease),
    transform 150ms var(--ease);
}

@starting-style {
  .flyout {
    opacity: 0;
    transform: translateX(-4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  @starting-style {
    .flyout {
      transform: none;
    }
  }
}

.flyout-head {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  overflow: hidden;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  line-height: 17.5px;
  white-space: nowrap;
}

.flyout .children {
  padding-bottom: 0;
  box-shadow: 0 4px 24px rgba(11, 48, 44, 0.4);
}

.flyout .children a {
  padding-left: 32px;
}
</style>
