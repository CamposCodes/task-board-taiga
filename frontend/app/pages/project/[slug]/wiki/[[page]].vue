<script setup lang="ts">
import type { UserRef } from '~/utils/types'

interface WikiPage {
  id: number
  slug: string
  content: string
  version: number
  lastModifier: UserRef | null
  modifiedAt: string
}

const route = useRoute()
const api = useApi()
const { project, can } = useProject()
const slug = project.value.slug
const page = computed(() => String(route.params.page || 'home'))
useHead({ title: () => `${page.value} - Wiki` })

const base = `/projects/${slug}/wiki`
const { data: pages, refresh: refreshPages } = await useAsyncData(`wiki-pages:${slug}`, () => api<{ slug: string }[]>(base))
const { data: current, refresh } = await useAsyncData(
  () => `wiki:${slug}:${page.value}`,
  () => api<WikiPage>(`${base}/${page.value}`, QUIET).catch(() => null),
  { watch: [page] },
)

const archived = computed(() => project.value.isArchived)
const editable = computed(() => !archived.value && can(current.value ? 'modify_wiki_page' : 'add_wiki_page'))
const draft = ref<string | null>(null)

const source = computed(() =>
  (current.value?.content ?? '').replace(/\[\[([^\]|]+)(?:\|([^\]]+))?\]\]/g, (_, target: string, label?: string) =>
    `[${label || target}](/project/${slug}/wiki/${slugify(target)})`,
  ),
)

async function save() {
  await api(`${base}/${page.value}`, { method: 'PUT', body: { content: draft.value, version: current.value?.version } })
  draft.value = null
  await Promise.all([refresh(), refreshPages()])
}

async function addPage() {
  const title = await askText('Nova página', 'Qual é o título da página?')
  const target = title ? slugify(title) : ''
  if (target) {
    await navigateTo(`/project/${slug}/wiki/${target}`)
  }
}

async function remove() {
  if (await confirmDelete(`a página "${page.value}"`)) {
    await api(`${base}/${page.value}`, { method: 'DELETE' })
    await refreshPages()
    await navigateTo(`/project/${slug}/wiki/home`)
    await refresh()
  }
}
</script>

<template>
  <div style="display: flex">
    <aside class="side-nav">
      <div class="group">Páginas</div>
      <NuxtLink class="item" :class="{ active: page === 'home' }" :to="`/project/${slug}/wiki/home`"><q-icon :name="icons.wikiPage" size="18px" />home</NuxtLink>
      <NuxtLink
        v-for="entry in (pages ?? []).filter((p) => p.slug !== 'home')"
        :key="entry.slug"
        class="item"
        :class="{ active: page === entry.slug }"
        :to="`/project/${slug}/wiki/${entry.slug}`"
      >
        <q-icon :name="icons.wikiPage" size="18px" />{{ entry.slug }}
      </NuxtLink>
      <div v-if="!archived && can('add_wiki_page')" class="slot">
        <button class="btn soft" type="button" @click="addPage"><q-icon :name="icons.add" size="16px" />Adicionar página</button>
      </div>
    </aside>

    <section class="section grow" style="max-width: 1024px; padding-top: 24px">
      <div class="row-center" style="justify-content: space-between">
        <h1 class="page-title">{{ page === 'home' ? 'Wiki' : page }}</h1>
        <div class="row-center gap-8">
          <button v-if="editable && draft === null" class="btn gray" type="button" @click="draft = current?.content ?? ''">
            <q-icon :name="icons.edit" size="16px" />Editar
          </button>
          <button v-if="current && !archived && can('delete_wiki_page') && draft === null" class="icon-btn" type="button" title="Excluir página" @click="remove">
            <q-icon :name="icons.trash" size="18px" />
          </button>
        </div>
      </div>

      <template v-if="draft !== null">
        <MarkdownEditor
          :model-value="draft"
          label="Conteúdo da página"
          :min-height="360"
          autofocus
          @update:model-value="draft = $event"
          @save="save"
          @cancel="draft = null"
        >
          <template #hint>Markdown. Use [[nome-da-pagina]] para ligar a outra página.</template>
        </MarkdownEditor>
      </template>
      <template v-else>
        <ClientOnly v-if="current?.content">
          <MarkdownView :source="source" />
        </ClientOnly>
        <p v-else style="font-size: 16px">Sem descrição ainda</p>
        <p v-if="current" class="muted" style="margin-top: 32px; font-size: 12px">
          Editado por {{ current.lastModifier?.fullName || 'alguém' }} em {{ formatDateTime(current.modifiedAt) }}
        </p>
      </template>
    </section>
  </div>
</template>
