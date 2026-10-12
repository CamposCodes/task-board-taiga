<script lang="ts">
const stack: symbol[] = []
</script>

<script setup lang="ts">
defineProps<{ title: string; narrow?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const id = Symbol('lightbox')
const root = ref<HTMLElement>()
let opener: Element | null = null

function onKey(event: KeyboardEvent) {
  if (stack.at(-1) !== id) {
    return
  }
  if (event.key === 'Escape') {
    event.stopImmediatePropagation()
    emit('close')
  } else if (event.key === 'Tab' && root.value) {
    const stops = [...root.value.querySelectorAll<HTMLElement>('a[href], button:not(:disabled), input:not(:disabled), select, textarea, [tabindex="0"]')]
      .filter((el) => el.offsetParent !== null)
    const edge = event.shiftKey ? stops[0] : stops.at(-1)
    if (stops.length && (document.activeElement === edge || !root.value.contains(document.activeElement))) {
      event.preventDefault()
      ;(event.shiftKey ? stops.at(-1) : stops[0])!.focus()
    }
  }
}

onMounted(() => {
  opener = document.activeElement
  stack.push(id)
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  stack.splice(stack.indexOf(id), 1)
  window.removeEventListener('keydown', onKey)
  if (opener instanceof HTMLElement) {
    opener.focus()
  }
})
</script>

<template>
  <Teleport to="body">
    <div ref="root" class="lightbox" role="dialog" aria-modal="true" :aria-label="title">
      <button class="close" type="button" title="Fechar" aria-label="Fechar" @click="emit('close')">
        <q-icon :name="icons.close" size="32px" />
      </button>
      <div class="panel" :class="{ narrow }">
        <h2>{{ title }}</h2>
        <slot />
      </div>
    </div>
  </Teleport>
</template>
