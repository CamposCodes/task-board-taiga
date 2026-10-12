<script setup lang="ts">
import type { ItemFilters } from '~/composables/filters'

defineProps<{ filters: ItemFilters }>()
const open = ref(false)
const showTags = defineModel<boolean | undefined>('showTags', { default: undefined })
const zoom = defineModel<number | undefined>('zoom', { default: undefined })

const ZOOMS = ['Compacto', 'Padrão', 'Detalhado', 'Expandido']
</script>

<template>
  <div class="toolbar">
    <div class="row-center gap-8">
      <button
        class="btn soft"
        :class="{ active: open }"
        type="button"
        :title="`${filters.applied.length} filtros aplicados`"
        aria-haspopup="true"
        :aria-expanded="open"
      >
        <q-icon :name="icons.filters" size="16px" />
        Filtros
        <template v-if="filters.applied.length">({{ filters.applied.length }})</template>
        <q-menu v-model="open" anchor="bottom left" self="top left" :offset="[0, 4]" :transition-duration="120">
          <FilterPanel :filters="filters" />
        </q-menu>
      </button>
      <div class="search-box">
        <input v-model="filters.q" class="field" type="search" placeholder="assunto ou referência" aria-label="Buscar por assunto ou referência">
        <q-icon :name="icons.search" size="16px" />
      </div>
      <button type="button" v-if="showTags !== undefined" class="switch" :class="{ on: showTags }" role="switch" :aria-checked="showTags" @click="showTags = !showTags">
        <i />
        Tags
      </button>
    </div>
    <div class="row-center gap-16">
      <slot />
      <div v-if="zoom !== undefined" class="zoom">
        <span class="caption">Visualização:</span>
        <button
          v-for="(name, level) in ZOOMS"
          :key="name"
          class="dot"
          :class="{ active: zoom === level }"
          type="button"
          :title="name"
          :aria-label="`Visualização: ${name}`"
          :aria-pressed="zoom === level"
          @click="zoom = level"
        >
          <template v-if="zoom === level">{{ name }}</template>
        </button>
      </div>
    </div>
  </div>
</template>
