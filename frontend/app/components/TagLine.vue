<script setup lang="ts">
const props = defineProps<{ tags: string[]; colors: Record<string, string>; editable?: boolean }>()
const emit = defineEmits<{ change: [tags: string[], colors: Record<string, string>] }>()

const PALETTE = [
  '#d35163', '#d351cf', '#ac51d3', '#8151d3', '#5551d3', '#5178d3', '#78d351',
  '#51d355', '#51d381', '#51d3ac', '#51cfd3', '#51a3d3', '#a3d350', '#cfd350',
  '#d3ac50', '#d38050', '#d35450', '#e44057', '#4c566a', '#70728f',
]

const adding = ref(false)
const text = ref('')
const color = ref('')
const paletteOpen = ref(false)
const selected = ref(-1)
const field = ref<HTMLInputElement>()
const chosen = reactive<Record<string, string>>({})

const name = computed(() => text.value.trim().toLowerCase())
const validColor = computed(() => (/^#[0-9a-f]{6}$/i.test(color.value) ? color.value : ''))
const suggestions = computed(() =>
  name.value
    ? Object.keys(props.colors)
        .filter((tag) => tag.includes(name.value) && !props.tags.includes(tag))
        .sort((a, b) => a.localeCompare(b))
    : [],
)
watch(suggestions, () => (selected.value = -1))

function add(tag = name.value) {
  if (tag && !props.tags.includes(tag)) {
    if (validColor.value && !props.colors[tag]) {
      chosen[tag] = validColor.value
    }
    emit('change', [...props.tags, tag], { ...chosen })
  }
  text.value = color.value = ''
  paletteOpen.value = false
  field.value?.focus()
}

function close() {
  adding.value = paletteOpen.value = false
  text.value = color.value = ''
}

function step(by: number) {
  const count = suggestions.value.length
  selected.value = count ? (selected.value + by + count + (selected.value < 0 && by < 0 ? 1 : 0)) % count : -1
}

function pick(value: string) {
  color.value = value
  paletteOpen.value = false
}
</script>

<template>
  <div class="tags-line">
    <span v-for="tag in tags" :key="tag" class="tag" :style="{ background: colors[tag] || chosen[tag] }" :title="tag">
      <span>{{ tag }}</span>
      <button v-if="editable" class="tag-x" type="button" :title="`Remover a tag ${tag}`" @click="emit('change', tags.filter((t) => t !== tag), { ...chosen })">
        <q-icon :name="icons.close" size="20px" />
      </button>
    </span>
    <template v-if="editable">
      <div v-if="adding" class="tag-add" @keydown.esc.stop="close">
        <input
          ref="field"
          v-model="text"
          class="tag-input"
          placeholder="Inserir tag"
          aria-label="Inserir tag"
          maxlength="50"
          autocomplete="off"
          v-focus
          @keydown.enter.prevent="add(suggestions[selected])"
          @keydown.down.prevent="step(1)"
          @keydown.up.prevent="step(-1)"
        >
        <button
          class="tag-color"
          type="button"
          title="Escolher a cor da tag"
          :aria-expanded="paletteOpen"
          :style="{ background: validColor || undefined }"
          @click="paletteOpen = !paletteOpen"
        />
        <button v-if="name" class="tag-save" type="button" title="Salvar" @click="add()">
          <q-icon :name="icons.save" size="16px" />
        </button>
        <ul v-if="suggestions.length && !paletteOpen" class="tag-suggest">
          <li v-for="(tag, index) in suggestions" :key="tag" :class="{ selected: index === selected }" @mousedown.prevent="add(tag)">
            <span>{{ tag }}</span>
            <i :style="{ background: colors[tag] }" />
          </li>
        </ul>
        <div v-if="paletteOpen" class="tag-palette">
          <ul>
            <li v-for="option in PALETTE" :key="option">
              <button class="swatch" type="button" :style="{ background: option }" :title="option" @click="pick(option)" />
            </li>
            <li><button class="swatch" type="button" title="Sem cor" @click="pick('')" /></li>
          </ul>
          <div class="custom">
            <span class="swatch" :style="{ background: validColor || undefined }" />
            <input v-model.trim="color" placeholder="Digite o código hex" aria-label="Código hex da cor" maxlength="7" @keydown.enter.prevent="paletteOpen = false">
          </div>
        </div>
      </div>
      <button v-else class="btn soft" type="button" @click="adding = true">
        Adicionar tag
        <q-icon :name="icons.add" size="16px" />
      </button>
    </template>
  </div>
</template>
