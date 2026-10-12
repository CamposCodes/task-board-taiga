<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const props = defineProps<{ source: string }>()

const html = computed(() =>
  import.meta.client
    ? DOMPurify.sanitize(marked.parse(props.source || '', { async: false, gfm: true, breaks: true }))
    : '',
)
</script>

<template>
  <div class="markdown" v-html="html" />
</template>
