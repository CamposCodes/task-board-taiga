<script setup lang="ts">
interface Profile {
  id: number
  username: string
  fullName: string
  bio: string
  roles: string[]
  projects: number
  closedUserStories: number
  contacts: number
}

const route = useRoute()
const api = useApi()
const me = useMe()
const username = computed(() => String(route.params.username || me.value!.username))

const { data: profile } = await useAsyncData(
  () => `profile:${username.value}`,
  () => api<Profile>(`/users/${username.value}/profile`, QUIET).catch(() => null),
  { watch: [username] },
)
if (!profile.value) {
  throw createError({ statusCode: 404, statusMessage: 'Usuário não encontrado', fatal: true })
}
const isMe = computed(() => profile.value?.id === me.value?.id)
useHead({ title: () => `${profile.value?.fullName} - Taskboard` })
</script>

<template>
  <div v-if="profile" class="centered" style="display: flex; gap: 32px; align-items: flex-start; padding-top: 32px">
    <aside style="flex: 0 0 220px; text-align: center">
      <UserAvatar :user="profile" :size="160" />
      <h1 class="page-title" style="margin: 16px 0 0">{{ profile.fullName }}</h1>
      <p class="muted">@{{ profile.username }}</p>
      <p style="margin: 12px 0; font-size: 16px">{{ profile.roles.join(', ') }}</p>
      <div class="row-center" style="justify-content: space-around; padding: 12px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line)">
        <div><div class="medium" style="font-size: 19px">{{ profile.projects }}</div><div class="muted" style="font-size: 11px">Projetos</div></div>
        <div><div class="medium" style="font-size: 19px">{{ profile.closedUserStories }}</div><div class="muted" style="font-size: 11px">Histórias<br>fechadas</div></div>
        <div><div class="medium" style="font-size: 19px">{{ profile.contacts }}</div><div class="muted" style="font-size: 11px">Contatos</div></div>
      </div>
      <p v-if="profile.bio" style="margin-top: 16px; white-space: pre-line; overflow-wrap: anywhere">{{ profile.bio }}</p>
    </aside>

    <section class="grow">
      <h2 class="title-bar" style="font-weight: 400"><span class="row-center gap-8"><q-icon :name="icons.timeline" size="18px" />Linha do tempo</span></h2>
      <ActivityFeed :key="profile.username" :url="`/users/${profile.username}/timeline`" />
    </section>

    <aside v-if="isMe" style="flex: 0 0 200px">
      <h2 class="title-bar" style="margin-bottom: 8px; font-size: 14px">Seu perfil</h2>
      <p class="muted" style="margin-bottom: 12px; font-size: 12px; line-height: 18px">
        As pessoas veem tudo o que você faz e no que está trabalhando. Adicione uma boa biografia para apresentar você.
      </p>
      <NuxtLink class="btn big" to="/user-settings/user-profile">Editar biografia</NuxtLink>
    </aside>
  </div>
</template>
