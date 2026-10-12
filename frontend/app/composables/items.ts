import { Dialog } from 'quasar'
import type { Attachment, Item, UserRef } from '~/utils/types'

export function useItemActions() {
  const api = useApi()
  return {
    update: (item: Item, changes: Record<string, unknown>) =>
      api<Item>(`/items/${item.id}`, { method: 'PATCH', body: { ...changes, version: item.version } }),
    move: (item: Item, body: Record<string, unknown>) => api<Item>(`/items/${item.id}/move`, { method: 'POST', body }),
    remove: (item: Item) => api(`/items/${item.id}`, { method: 'DELETE' }),
    watch: (item: Item, on: boolean) => api<Item>(`/items/${item.id}/watch`, { method: on ? 'POST' : 'DELETE' }),
    upload: async (itemId: number, files: File[]) => {
      const uploaded: Attachment[] = []
      for (const file of files) {
        const body = new FormData()
        body.append('file', file)
        uploaded.push(await api<Attachment>(`/items/${itemId}/attachments`, { method: 'POST', body }))
      }
      return uploaded
    },
  }
}

export function confirmDelete(what: string): Promise<boolean> {
  return new Promise((resolve) => {
    Dialog.create({
      title: 'Excluir',
      message: `Tem certeza de que deseja excluir ${what}? Esta ação não pode ser desfeita.`,
      ok: { label: 'Excluir', color: 'negative', unelevated: true, noCaps: true },
      cancel: { label: 'Cancelar', flat: true, noCaps: true, color: 'grey-8' },
    })
      .onOk(() => resolve(true))
      .onCancel(() => resolve(false))
      .onDismiss(() => resolve(false))
  })
}

export function askText(title: string, message: string, initial = ''): Promise<string | null> {
  return new Promise((resolve) => {
    Dialog.create({
      title,
      message,
      prompt: { model: initial, type: 'text', outlined: true },
      ok: { label: 'Salvar', color: 'primary', unelevated: true, noCaps: true },
      cancel: { label: 'Cancelar', flat: true, noCaps: true, color: 'grey-8' },
    })
      .onOk((text: string) => resolve(text))
      .onCancel(() => resolve(null))
      .onDismiss(() => resolve(null))
  })
}

export function asUser(member: { userId: number; username: string; fullName: string }): UserRef {
  return { id: member.userId, username: member.username, fullName: member.fullName }
}
