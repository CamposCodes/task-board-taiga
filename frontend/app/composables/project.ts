import type { InjectionKey, Ref } from 'vue'
import type { Kind, ProjectDetail } from '~/utils/types'

export interface ProjectContext {
  project: Ref<ProjectDetail>
  refresh: () => Promise<void>
  can: (permission: string) => boolean
}

export const PROJECT_KEY = Symbol('project') as InjectionKey<ProjectContext>

export function useProject(): ProjectContext {
  return inject(PROJECT_KEY)!
}

export function permission(action: 'view' | 'add' | 'modify' | 'comment' | 'delete', kind: Kind): string {
  return `${action}_${kind}${action === 'view' && kind !== 'us' ? 's' : ''}`
}

export const kindPath: Record<Kind, string> = { us: 'userstories', task: 'tasks', issue: 'issues' }
