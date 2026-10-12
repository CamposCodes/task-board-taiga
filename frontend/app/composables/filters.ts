import type { Item, Kind, UserRef } from '~/utils/types'

export interface FilterOption {
  id: string
  name: string
  color?: string
  user?: UserRef | null
  count: number
}

export interface FilterCategory {
  key: string
  label: string
  options: FilterOption[]
}

export type FilterKey = 'status' | 'tags' | 'assignedTo' | 'role' | 'createdBy' | 'type' | 'severity' | 'priority'

const LABELS: Record<FilterKey, string> = {
  type: 'Tipo',
  severity: 'Gravidade',
  priority: 'Prioridade',
  status: 'Status',
  tags: 'Tags',
  assignedTo: 'Atribuído a',
  role: 'Função',
  createdBy: 'Criado por',
}

export function useItemFilters(items: Ref<Item[]>, kind: Kind, keys: FilterKey[]) {
  const { project } = useProject()
  const q = ref('')
  const mode = ref<'include' | 'exclude'>('include')
  const include = reactive<Record<string, string[]>>({})
  const exclude = reactive<Record<string, string[]>>({})

  const roleOf = computed(() => new Map(project.value.members.map((m) => [m.userId, String(m.roleId)])))

  function valuesOf(item: Item, key: string): string[] {
    switch (key) {
      case 'status':
        return [String(item.status.id)]
      case 'tags':
        return item.tags
      case 'assignedTo':
        return item.assignedUsers.length ? item.assignedUsers.map((u) => String(u.id)) : ['none']
      case 'role':
        return item.assignedUsers.map((u) => roleOf.value.get(u.id)).filter((r): r is string => !!r)
      case 'createdBy':
        return item.createdBy ? [String(item.createdBy.id)] : []
      case 'type':
        return item.issueType ? [String(item.issueType.id)] : []
      case 'severity':
        return item.severity ? [String(item.severity.id)] : []
      default:
        return item.priority ? [String(item.priority.id)] : []
    }
  }

  function optionsOf(key: FilterKey): Omit<FilterOption, 'count'>[] {
    const p = project.value
    const people = p.members.map((m) => ({ id: String(m.userId), name: m.fullName, user: asUser(m) }))
    switch (key) {
      case 'status':
        return p.statuses.filter((s) => s.kind === kind).map((s) => ({ id: String(s.id), name: s.name, color: s.color }))
      case 'tags': {
        const names = new Set([...Object.keys(p.tagsColors), ...items.value.flatMap((i) => i.tags)])
        return [...names].sort((a, b) => a.localeCompare(b)).map((name) => ({ id: name, name, color: p.tagsColors[name] }))
      }
      case 'assignedTo':
        return [{ id: 'none', name: 'Não atribuído', user: null }, ...people]
      case 'role':
        return p.roles.map((r) => ({ id: String(r.id), name: r.name }))
      case 'createdBy':
        return people
      case 'type':
        return p.issueTypes.map((c) => ({ id: String(c.id), name: c.name, color: c.color }))
      case 'severity':
        return p.severities.map((c) => ({ id: String(c.id), name: c.name, color: c.color }))
      default:
        return p.priorities.map((c) => ({ id: String(c.id), name: c.name, color: c.color }))
    }
  }

  const categories = computed<FilterCategory[]>(() =>
    keys.map((key) => ({
      key,
      label: LABELS[key],
      options: optionsOf(key).map((option) => ({
        ...option,
        count: items.value.filter((item) => valuesOf(item, key).includes(option.id)).length,
      })),
    })),
  )

  function matches(item: Item): boolean {
    const text = q.value.trim().toLowerCase()
    if (text) {
      const ref = text.replace(/^#/, '')
      if (!item.subject.toLowerCase().includes(text) && String(item.ref) !== ref) {
        return false
      }
    }
    for (const key of keys) {
      const values = valuesOf(item, key)
      if (include[key]?.length && !include[key].some((id) => values.includes(id))) {
        return false
      }
      if (exclude[key]?.some((id) => values.includes(id))) {
        return false
      }
    }
    return true
  }

  const applied = computed(() =>
    categories.value.flatMap((category) =>
      category.options
        .filter((o) => include[category.key]?.includes(o.id) || exclude[category.key]?.includes(o.id))
        .map((o) => ({ key: category.key, id: o.id, name: o.name, excluded: !!exclude[category.key]?.includes(o.id) })),
    ),
  )

  function remove(key: string, id: string) {
    include[key] = (include[key] || []).filter((x) => x !== id)
    exclude[key] = (exclude[key] || []).filter((x) => x !== id)
  }

  function toggle(key: string, id: string) {
    const was = include[key]?.includes(id) || exclude[key]?.includes(id)
    remove(key, id)
    if (!was) {
      const target = mode.value === 'include' ? include : exclude
      target[key] = [...(target[key] || []), id]
    }
  }

  return reactive({ q, mode, categories, applied, matches, toggle, remove })
}

export type ItemFilters = ReturnType<typeof useItemFilters>
