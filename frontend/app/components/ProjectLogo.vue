<script setup lang="ts">
const props = withDefaults(defineProps<{ project: { slug: string; name: string }; size?: number }>(), { size: 32 })

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.round(props.size * 0.5)}px`,
  background: colorFor(props.project.slug),
  color: textColorOn(colorFor(props.project.slug)),
}))
</script>

<template>
  <span class="logo-tile" :style="style">{{ project.name.trim().charAt(0).toUpperCase() }}</span>
</template>
