import type { Choice, Item, Kind, Notice } from '~/utils/types'

const ZONE = 'America/Sao_Paulo'
const dateFormat = new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' })
const longDateFormat = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' })
const dateTimeFormat = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: ZONE,
})
const relativeFormat = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'always' })

const tidy = (text: string) => text.replace(/\./g, '').replace(/ de /g, ' ').replace(',', '')

export function formatDate(isoDate: string | null | undefined): string {
  return isoDate ? dateFormat.format(new Date(isoDate + 'T00:00:00Z')) : ''
}

export function formatDay(isoDate: string | null | undefined): string {
  return isoDate ? tidy(longDateFormat.format(new Date(isoDate + 'T00:00:00Z'))) : ''
}

export function formatDateTime(instant: string | null | undefined): string {
  return instant ? tidy(dateTimeFormat.format(new Date(instant))) : ''
}

export function relativeTime(instant: string, now: number): string {
  const seconds = Math.round((new Date(instant).getTime() - now) / 1000)
  const steps: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000],
    ['month', 2_592_000],
    ['day', 86_400],
    ['hour', 3600],
    ['minute', 60],
  ]
  for (const [unit, size] of steps) {
    if (Math.abs(seconds) >= size) {
      return relativeFormat.format(Math.round(seconds / size), unit)
    }
  }
  return 'agora mesmo'
}

export function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONE }).format(new Date())
}

export function dueState(isoDate: string | null | undefined, closed = false): 'none' | 'set' | 'soon' | 'past' {
  if (!isoDate) {
    return 'none'
  }
  if (closed) {
    return 'set'
  }
  const days = (new Date(isoDate + 'T00:00:00Z').getTime() - new Date(todayIso() + 'T00:00:00Z').getTime()) / 86_400_000
  return days < 0 ? 'past' : days <= 3 ? 'soon' : 'set'
}

export function initials(name: string | null | undefined): string {
  const parts = (name || '?').trim().split(/\s+/)
  return ((parts[0]?.[0] || '') + (parts.length > 1 ? parts[parts.length - 1]![0] : '')).toUpperCase()
}

const PALETTE = ['#1f7d74', '#97c65c', '#8c52ff', '#d3ac50', '#137acf', '#9f760a', '#e44057', '#5178d3', '#40a8e4', '#b4609a']

export function colorFor(text: string): string {
  let hash = 0
  for (const char of text) {
    hash = (hash * 31 + char.codePointAt(0)!) >>> 0
  }
  return PALETTE[hash % PALETTE.length]!
}

export function textColorOn(background: string): string {
  const n = Number.parseInt(background.slice(1), 16)
  const channel = (value: number) => (value <= 10 ? value / 3294.6 : ((value / 255 + 0.055) / 1.055) ** 2.4)
  const luminance = 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255)
  return 1.05 / (luminance + 0.05) >= (luminance + 0.05) / 0.0652 ? '#ffffff' : '#212121'
}

export function fileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(1).replace('.', ',')} ${units[unit]}`
}

export const kindLabels: Record<Kind, string> = {
  us: 'História de usuário',
  task: 'Tarefa',
  issue: 'Problema',
}

export function itemPath(slug: string, item: { type: Kind | 'wiki'; ref: number; subject?: string }): string {
  if (item.type === 'wiki') {
    return `/project/${slug}/wiki/${item.subject}`
  }
  return `/project/${slug}/${item.type}/${item.ref}`
}

const noticeVerbs: Record<Notice['kind'], string> = {
  assigned: 'atribuiu a você',
  comment: 'comentou em',
  create: 'criou',
  change: 'alterou',
  delete: 'excluiu',
}

export function noticeVerb(notice: Notice): string {
  return noticeVerbs[notice.kind] + (notice.itemType === 'wiki' ? ' a página da wiki' : '')
}

export function noticeItem(notice: Notice): string {
  return notice.itemType === 'wiki' ? notice.itemSubject : `#${notice.itemRef} ${notice.itemSubject}`
}

export function noticePath(notice: Notice): string | null {
  return notice.kind === 'delete'
    ? null
    : itemPath(notice.project.slug, { type: notice.itemType, ref: notice.itemRef, subject: notice.itemSubject })
}

export function pointsLabel(points: Choice[], value: number | null): string {
  const found = points.find((p) => (p.value === null ? value === null : value !== null && Number(p.value) === Number(value)))
  return found ? found.name : value === null ? '?' : String(value).replace('.', ',')
}

export function number(value: number): string {
  return String(Math.round(Number(value) * 10) / 10).replace('.', ',')
}

export function sumPoints(items: Item[]): number {
  return items.reduce((total, item) => total + Number(item.points ?? 0), 0)
}

export function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80)
}
