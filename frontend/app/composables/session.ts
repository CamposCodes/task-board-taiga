import type { Me, Page, Project } from '~/utils/types'

export const useMe = () => useState<Me | null>('me', () => null)

export const useApi = () => useNuxtApp().$api

export const QUIET = { quiet: true } as never

export async function fetchAll<T>(url: string, query: Record<string, unknown> = {}): Promise<T[]> {
  const api = useApi()
  const all: T[] = []
  for (let page = 1; page <= 10; page++) {
    const result = await api<Page<T>>(url, { query: { ...query, page, size: 200 } })
    all.push(...result.items)
    if (all.length >= result.total || result.items.length === 0) {
      break
    }
  }
  return all
}

export function usePreference<T>(key: string, fallback: T) {
  const value = ref(fallback) as Ref<T>
  onMounted(() => {
    try {
      const stored = localStorage.getItem(key)
      if (stored !== null) {
        value.value = JSON.parse(stored)
      }
    } catch {
    }
    watch(value, (next) => localStorage.setItem(key, JSON.stringify(next)), { deep: true })
  })
  return value
}

export function useMyProjects() {
  const projects = useState<Project[]>('myProjects', () => [])
  const loaded = useState('myProjectsLoaded', () => false)
  async function refresh() {
    projects.value = await useApi()<Project[]>('/projects')
    loaded.value = true
  }
  return { projects, loaded, refresh }
}

export function useTimeAgo() {
  const now = useState('now', () => Date.now())
  return (instant: string) => relativeTime(instant, now.value)
}
