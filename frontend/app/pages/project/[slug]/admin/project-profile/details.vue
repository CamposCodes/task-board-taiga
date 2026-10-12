<script setup lang="ts">
import { Notify } from 'quasar'

const api = useApi()
const { project, refresh } = useProject()
const { refresh: refreshList } = useMyProjects()

const form = reactive({
  name: project.value.name,
  description: project.value.description,
  isPrivate: project.value.isPrivate,
})
const saving = ref(false)

async function save() {
  saving.value = true
  try {
    await api(`/projects/${project.value.id}`, { method: 'PATCH', body: form })
    await Promise.all([refresh(), refreshList()])
    Notify.create({ type: 'positive', message: 'Projeto salvo.' })
  } finally {
    saving.value = false
  }
}

async function setArchived(archived: boolean) {
  await api(`/projects/${project.value.id}/${archived ? 'archive' : 'unarchive'}`, { method: 'POST' })
  await Promise.all([refresh(), refreshList()])
}

async function remove() {
  const typed = await askText('Excluir projeto', `Isto apaga o projeto e tudo o que há nele. Para confirmar, digite o nome do projeto: ${project.value.name}`)
  if (typed === null) {
    return
  }
  if (typed.trim() !== project.value.name) {
    Notify.create({ type: 'negative', message: 'O nome não confere. Nada foi excluído.' })
    return
  }
  await api(`/projects/${project.value.id}`, { method: 'DELETE' })
  await refreshList()
  await navigateTo('/projects/')
}
</script>

<template>
  <div style="max-width: 640px">
    <h1 class="page-title">Detalhes do projeto</h1>
    <form @submit.prevent="save">
      <div class="form-row">
        <label for="project-name">Nome do projeto</label>
        <input id="project-name" v-model="form.name" class="field" maxlength="120" required :disabled="project.isArchived">
      </div>
      <div class="form-row">
        <label for="project-description">Descrição</label>
        <textarea id="project-description" v-model="form.description" class="field" maxlength="5000" :disabled="project.isArchived" />
      </div>
      <div class="form-row row-center gap-16">
        <label class="radio"><input v-model="form.isPrivate" type="radio" :value="false" :disabled="project.isArchived"> Projeto público</label>
        <label class="radio"><input v-model="form.isPrivate" type="radio" :value="true" :disabled="project.isArchived"> Projeto privado</label>
      </div>
      <p class="muted form-row">
        {{ form.isPrivate ? 'Só os membros veem o projeto.' : 'Qualquer pessoa com conta pode ver o projeto. Só os membros alteram.' }}
      </p>
      <button class="btn" type="submit" :disabled="saving || project.isArchived">Salvar</button>
    </form>

    <template v-if="project.my.isOwner">
      <h2 class="label" style="margin-top: 48px">Arquivo e exclusão</h2>
      <p class="form-row">
        Um projeto arquivado sai das listas e fica somente para leitura. Excluir apaga tudo de forma definitiva.
      </p>
      <div class="row-center gap-8">
        <button class="btn gray" type="button" @click="setArchived(!project.isArchived)">
          <q-icon :name="icons.archive" size="16px" />{{ project.isArchived ? 'Restaurar projeto' : 'Arquivar projeto' }}
        </button>
        <button class="btn danger" type="button" @click="remove"><q-icon :name="icons.trash" size="16px" />Excluir projeto</button>
      </div>
    </template>
  </div>
</template>
