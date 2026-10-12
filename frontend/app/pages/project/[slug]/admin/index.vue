<script setup lang="ts">
const route = useRoute()
await navigateTo(`/project/${route.params.slug}/admin/project-profile/details`, { replace: true })
</script>

<template>
  <div />
</template>
