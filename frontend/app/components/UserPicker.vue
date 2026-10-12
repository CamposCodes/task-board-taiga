<script setup lang="ts">
import type { Member } from '~/utils/types'

const props = defineProps<{ title: string; members: Member[]; selected: number[]; multiple?: boolean }>()
const emit = defineEmits<{ pick: [ids: number[]]; close: [] }>()

const search = ref('')
const chosen = ref([...props.selected])
const shown = computed(() => {
  const text = search.value.trim().toLowerCase()
  return props.members.filter((m) => !text || m.fullName.toLowerCase().includes(text) || m.username.includes(text))
})

function choose(member: Member) {
  if (!props.multiple) {
    emit('pick', [member.userId])
    return
  }
  chosen.value = chosen.value.includes(member.userId)
    ? chosen.value.filter((id) => id !== member.userId)
    : [...chosen.value, member.userId]
}
</script>

<template>
  <AppLightbox :title="title" narrow @close="emit('close')">
    <div class="search-box" style="width: 100%; margin-bottom: 8px">
      <input v-model="search" class="field" type="search" placeholder="Procurar pelo nome..." aria-label="Procurar pelo nome" v-focus>
      <q-icon :name="icons.search" size="16px" />
    </div>
    <div style="max-height: 50vh; overflow-y: auto">
      <button
        v-for="member in shown"
        :key="member.userId"
        class="user-option"
        :class="{ selected: chosen.includes(member.userId) }"
        type="button"
        @click="choose(member)"
      >
        <UserAvatar :user="member" :size="40" />
        <span class="grow">
          <span class="medium" style="display: block">{{ member.fullName }}</span>
          <span class="muted">{{ member.roleName }}</span>
        </span>
        <q-icon v-if="chosen.includes(member.userId)" :name="icons.check" size="20px" class="teal" />
      </button>
      <p v-if="!shown.length" class="muted" style="padding: 16px; text-align: center">Ninguém com esse nome no projeto.</p>
    </div>
    <div class="row-center gap-8" style="margin-top: 16px">
      <button v-if="!multiple && selected.length" class="btn gray" type="button" @click="emit('pick', [])">Remover atribuição</button>
      <button v-if="multiple" class="btn big" type="button" @click="emit('pick', chosen)">Confirmar</button>
    </div>
  </AppLightbox>
</template>
