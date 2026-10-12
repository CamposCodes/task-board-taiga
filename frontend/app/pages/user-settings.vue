<script setup lang="ts">
const me = useMe()
const LINKS = [
  { to: '/user-settings/user-profile', label: 'Configurações de usuário', icon: icons.account },
  { to: '/user-settings/change-password', label: 'Alterar senha', icon: icons.password },
  { to: '/user-settings/api-tokens', label: 'Tokens de API', icon: icons.token },
]
</script>

<template>
  <div style="display: flex">
    <aside class="side-nav chrome">
      <NuxtLink v-for="link in LINKS" :key="link.to" class="item" :to="link.to"><q-icon :name="link.icon" size="24px" />{{ link.label }}</NuxtLink>
      <NuxtLink v-if="me?.isSuperuser" class="item" to="/admin/users"><q-icon :name="icons.systemUsers" size="24px" />Usuários do sistema</NuxtLink>
    </aside>
    <section class="section grow">
      <NuxtPage />
    </section>
  </div>
</template>
