<script setup lang="ts">
import type { Project } from '~/utils/types'

useHead({ title: 'Novo projeto - Taskboard' })

const api = useApi()
const { refresh } = useMyProjects()

const TEMPLATES = [
  { key: 'scrum', name: 'Scrum', icon: icons.scrum, text: 'Priorize o backlog e entregue em sprints curtas, com um quadro de tarefas para cada uma.' },
  { key: 'kanban', name: 'Kanban', icon: icons.kanban, text: 'Acompanhe o trabalho em um quadro contínuo, com uma coluna por etapa.' },
]
const form = reactive({ template: '', name: '', description: '', isPrivate: true })
const saving = ref(false)

async function create() {
  saving.value = true
  try {
    const project = await api<Project>('/projects', { method: 'POST', body: form })
    await refresh()
    await navigateTo(`/project/${project.slug}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="centered" style="max-width: 720px">
    <h1 class="page-title" style="margin-top: 24px; text-align: center">Novo projeto</h1>

    <template v-if="!form.template">
      <p class="muted" style="margin-bottom: 24px; text-align: center; font-size: 16px">Qual modelo se encaixa melhor no seu projeto?</p>
      <button
        v-for="template in TEMPLATES"
        :key="template.key"
        class="row-center gap-16 pointer"
        type="button"
        style="width: 100%; margin-bottom: 16px; padding: 24px; border: 2px solid var(--line); border-radius: 4px; background: #fff; text-align: left"
        @click="form.template = template.key"
      >
        <q-icon :name="template.icon" size="48px" class="teal" />
        <span>
          <span class="medium teal" style="display: block; font-size: 19.2px; text-transform: uppercase">{{ template.name }}</span>
          <span class="muted" style="font-size: 15px">{{ template.text }}</span>
        </span>
      </button>
      <p class="muted" style="text-align: center">Os dois módulos podem ser ligados ou desligados depois, nas configurações do projeto.</p>
    </template>

    <form v-else @submit.prevent="create">
      <p class="form-row" style="text-align: center">
        Modelo <span class="medium">{{ TEMPLATES.find((t) => t.key === form.template)!.name }}</span> ·
        <a href="#" class="pointer" @click.prevent="form.template = ''">trocar</a>
      </p>
      <div class="form-row">
        <input v-model="form.name" class="field" placeholder="Nome do projeto (obrigatório)" aria-label="Nome do projeto" maxlength="120" required v-focus>
      </div>
      <div class="form-row">
        <textarea v-model="form.description" class="field" placeholder="Descrição do projeto" aria-label="Descrição do projeto" maxlength="5000" />
      </div>
      <div class="form-row row-center gap-16">
        <label class="radio"><input v-model="form.isPrivate" type="radio" :value="false"> Projeto público</label>
        <label class="radio"><input v-model="form.isPrivate" type="radio" :value="true"> Projeto privado</label>
      </div>
      <div class="row-center gap-8">
        <NuxtLink class="btn gray" to="/projects/">Voltar</NuxtLink>
        <button class="btn grow" type="submit" :disabled="saving">Criar projeto</button>
      </div>
    </form>
  </div>
</template>
