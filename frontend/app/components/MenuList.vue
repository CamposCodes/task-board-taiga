<script setup lang="ts">
defineProps<{ options: { id: number | string | null; name: string; color?: string }[] }>()
const emit = defineEmits<{ pick: [id: any] }>()

const anchor = ref<HTMLElement>()
onMounted(() => {
  const trigger = anchor.value?.parentElement
  if (trigger && trigger.tabIndex < 0) {
    trigger.tabIndex = 0
    trigger.setAttribute('role', 'button')
    trigger.setAttribute('aria-haspopup', 'menu')
    trigger.addEventListener('keydown', (event) => {
      if (event.target === trigger && (event.key === 'Enter' || event.key === ' ')) {
        event.preventDefault()
        trigger.click()
      }
    })
  }
})
</script>

<template>
  <span ref="anchor" hidden />
  <q-menu auto-close :transition-duration="120">
    <div class="menu-list" role="menu">
      <button v-for="option in options" :key="String(option.id)" class="option" type="button" role="menuitem" @click="emit('pick', option.id)">
        <span v-if="option.color" class="dot" :style="{ background: option.color }" />
        {{ option.name }}
      </button>
    </div>
  </q-menu>
</template>
