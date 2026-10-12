<script setup lang="ts">
const { project, refresh } = useProject()
const api = useApi()
const { refresh: refreshList } = useMyProjects()

async function toggle(mark: 'like' | 'watch') {
  const on = mark === 'like' ? project.value.my.liked : project.value.my.watching
  await api(`/projects/${project.value.slug}/${mark}`, { method: on ? 'DELETE' : 'POST' })
  await Promise.all([refresh(), refreshList()])
}
</script>

<template>
  <div class="centered">
    <section style="display: flex; gap: 16px; margin-bottom: 32px">
      <ProjectLogo :project="project" :size="96" />
      <div class="grow">
        <div class="row-center" style="justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 8px">
          <div class="row-center gap-4">
            <h1 class="page-title" style="margin: 0">{{ project.name }}</h1>
            <q-icon v-if="project.isPrivate" :name="icons.private" size="24px" title="Projeto privado" />
            <span v-if="project.isArchived" class="chip">arquivado</span>
          </div>
          <div class="row-center" style="gap: 12px">
            <button class="track" :class="{ active: project.my.liked }" type="button" title="Curtir ou descurtir este projeto" @click="toggle('like')">
              <span class="inner"><q-icon :name="icons.like" size="16px" />{{ project.my.liked ? 'Curtido' : 'Curtir' }}</span>
              <span class="counter">{{ project.likes }}</span>
            </button>
            <button class="track" :class="{ active: project.my.watching }" type="button" title="Observar este projeto" @click="toggle('watch')">
              <span class="inner"><q-icon :name="icons.watch" size="16px" />{{ project.my.watching ? 'Observando' : 'Observar' }}</span>
              <span class="counter">{{ project.watchers }}</span>
            </button>
          </div>
        </div>
        <p style="font-size: 16px; line-height: 24px">{{ project.description }}</p>
      </div>
    </section>

    <div style="display: flex; gap: 16px; align-items: flex-start">
      <div class="grow">
        <ActivityFeed :url="`/projects/${project.slug}/activity`" />
      </div>
      <aside style="flex: 0 0 220px">
        <h2 class="title-bar" style="margin-bottom: 8px">Time</h2>
        <div style="display: flex; flex-wrap: wrap; gap: 4px">
          <NuxtLink v-for="member in project.members" :key="member.userId" :to="`/profile/${member.username}`">
            <UserAvatar :user="member" :size="50" />
          </NuxtLink>
        </div>
      </aside>
    </div>
  </div>
</template>
