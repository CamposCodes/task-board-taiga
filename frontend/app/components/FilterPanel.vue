<script setup lang="ts">
import type { ItemFilters } from '~/composables/filters'

defineProps<{ filters: ItemFilters }>()
const open = ref<string | null>(null)
</script>

<template>
  <aside class="filters">
    <div v-if="filters.applied.length" class="applied">
      <span v-for="entry in filters.applied" :key="entry.key + entry.id" class="chip" :class="{ excluded: entry.excluded }">
        {{ entry.name }}
        <q-icon :name="icons.close" size="12px" class="pointer" title="Tirar filtro" @click="filters.remove(entry.key, entry.id)" />
      </span>
    </div>
    <div class="modes">
      <label class="radio"><input v-model="filters.mode" type="radio" value="include"> Incluir</label>
      <label class="radio"><input v-model="filters.mode" type="radio" value="exclude"> Excluir</label>
    </div>
    <template v-for="category in filters.categories" :key="category.key">
      <button
        class="category"
        :class="{ open: open === category.key }"
        type="button"
        @click="open = open === category.key ? null : category.key"
      >
        {{ category.label }}
        <q-icon :name="open === category.key ? icons.down : icons.unfold" size="18px" class="teal" />
      </button>
      <div v-if="open === category.key" class="options">
        <button v-for="option in category.options" :key="option.id" class="choice" type="button" @click="filters.toggle(category.key, option.id)">
          <UserAvatar v-if="option.user !== undefined" :user="option.user" :size="22" />
          <span v-else-if="option.color" class="dot" :style="{ background: option.color }" />
          <span class="clip">{{ option.name }}</span>
          <span v-if="option.count" class="count">{{ option.count }}</span>
        </button>
        <p v-if="!category.options.length" class="muted" style="padding: 8px 12px">Nada para filtrar aqui.</p>
      </div>
    </template>
  </aside>
</template>
