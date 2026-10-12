<script setup lang="ts">
const props = withDefaults(
  defineProps<{ user: { username: string; fullName: string } | null; size?: number; square?: boolean }>(),
  { size: 32, square: false },
)

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.round(props.size * 0.4)}px`,
  background: props.user ? colorFor(props.user.username) : '#d3d3d3',
  color: props.user ? textColorOn(colorFor(props.user.username)) : undefined,
}))
</script>

<template>
  <span class="avatar" :class="{ square }" :style="style" :title="user?.fullName || 'Não atribuído'">
    {{ user ? initials(user.fullName) : '' }}
  </span>
</template>
