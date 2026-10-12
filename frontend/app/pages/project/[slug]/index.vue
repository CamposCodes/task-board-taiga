<script setup lang="ts">
const route = useRoute()
await navigateTo(`/project/${route.params.slug}/timeline`, { replace: true })
</script>

<template>
  <div />
</template>
