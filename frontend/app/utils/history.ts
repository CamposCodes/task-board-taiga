import type { HistoryEntry } from '~/utils/types'

const fieldLabels: Record<string, string> = {
  subject: 'assunto',
  description: 'descrição',
  status: 'status',
  milestone: 'sprint',
  parent: 'história de usuário',
  points: 'pontos',
  assignedTo: 'atribuído a',
  tags: 'tags',
  dueDate: 'data de vencimento',
  isBlocked: 'bloqueado',
  blockedNote: 'motivo do bloqueio',
  type: 'tipo',
  severity: 'gravidade',
  priority: 'prioridade',
  teamRequirement: 'requisito de equipe',
  clientRequirement: 'requisito de cliente',
  isIocaine: 'iocaína',
  attachment: 'anexo',
}

const targetNames: Record<string, string> = {
  us: 'a história de usuário',
  task: 'a tarefa',
  issue: 'o problema',
  wiki: 'a página da wiki',
}

export interface Change {
  field: string
  before: string
  after: string
}

function show(field: string, value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return field === 'assignedTo' ? 'não atribuído' : 'não definido'
  }
  if (typeof value === 'boolean') {
    return value ? 'sim' : 'não'
  }
  if (Array.isArray(value)) {
    return value.length ? value.join(', ') : 'nenhuma'
  }
  return field === 'dueDate' ? formatDay(String(value)) : String(value)
}

export function historyChanges(entry: HistoryEntry): Change[] {
  return Object.entries(entry.diff || {}).map(([field, [before, after]]) => ({
    field: fieldLabels[field] || field,
    before: field === 'description' ? '' : show(field, before),
    after: field === 'description' ? 'alterada' : show(field, after),
  }))
}

export function historyVerb(entry: HistoryEntry): string {
  const target = targetNames[entry.target.type] || 'o item'
  if (entry.kind === 'create') {
    return `criou ${target}`
  }
  if (entry.kind === 'delete') {
    return `excluiu ${target}`
  }
  if (entry.kind === 'comment') {
    return `comentou n${target}`
  }
  const fields = Object.keys(entry.diff || {})
  if (fields.length === 1) {
    return `atualizou o atributo "${fieldLabels[fields[0]!] || fields[0]}" d${target}`
  }
  return `atualizou ${target}`
}

export function historyNewValue(entry: HistoryEntry): string {
  const fields = Object.keys(entry.diff || {})
  if (entry.kind !== 'change' || fields.length !== 1 || fields[0] === 'description') {
    return ''
  }
  return show(fields[0]!, entry.diff[fields[0]!]![1])
}
