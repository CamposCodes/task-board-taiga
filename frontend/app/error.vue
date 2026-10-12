<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError }>()

const notFound = computed(() => props.error.statusCode === 404)
</script>

<template>
  <div class="login" style="flex-direction: column; text-align: center">
    <div style="color: var(--line); font-size: 72px; font-weight: 700">{{ error.statusCode }}</div>
    <h1 class="page-title" style="margin: 8px 0 4px">{{ notFound ? 'Página não encontrada' : 'Algo deu errado' }}</h1>
    <p class="muted">
      {{ notFound ? 'O endereço não existe ou você não tem acesso a ele.' : 'Tente de novo em instantes.' }}
    </p>
    <button class="btn" type="button" style="margin-top: 24px" @click="clearError({ redirect: '/' })">Ir para o início</button>
  </div>
</template>
