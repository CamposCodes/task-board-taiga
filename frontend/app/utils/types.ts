export interface UserRef {
  id: number
  username: string
  fullName: string
}

export interface Me extends UserRef {
  email: string
  bio: string
  isSuperuser: boolean
  isSso: boolean
  viaToken: boolean
  scopes: string[]
}

export type Kind = 'us' | 'task' | 'issue'

export interface StatusRef {
  id: number
  name: string
  color: string
  isClosed: boolean
}

export interface Status extends StatusRef {
  kind: Kind
  sortOrder: number
  isArchived: boolean
  wipLimit: number | null
}

export type ChoiceKind = 'points' | 'issue_type' | 'severity' | 'priority'

export interface Choice {
  id: number
  kind: ChoiceKind
  name: string
  color: string
  value: number | null
  sortOrder: number
}

export interface ChoiceRef {
  id: number
  name: string
  color: string
}

export interface Role {
  id: number
  name: string
  sortOrder: number
  permissions: string[]
  followsAll: boolean
  membersCount: number
}

export interface Member {
  userId: number
  username: string
  fullName: string
  email: string
  roleId: number
  roleName: string
  isAdmin: boolean
  isOwner: boolean
}

export interface Milestone {
  id: number
  name: string
  slug: string
  estimatedStart: string
  estimatedFinish: string
  isClosed: boolean
  usOpen: number
  usClosed: number
  pointsOpen: number
  pointsClosed: number
}

export interface My {
  isMember: boolean
  isAdmin: boolean
  isOwner: boolean
  roleId: number | null
  roleName: string | null
  permissions: string[]
  liked: boolean
  watching: boolean
}

export interface Project {
  id: number
  slug: string
  name: string
  description: string
  isPrivate: boolean
  isArchived: boolean
  ownerId: number
  tagsColors: Record<string, string>
  createdAt: string
  modifiedAt: string
  isBacklogActivated: boolean
  isKanbanActivated: boolean
  isIssuesActivated: boolean
  isWikiActivated: boolean
  likes: number
  watchers: number
  membersCount: number
  my: My
}

export interface ProjectDetail extends Project {
  statuses: Status[]
  members: Member[]
  milestones: Milestone[]
  roles: Role[]
  points: Choice[]
  issueTypes: Choice[]
  severities: Choice[]
  priorities: Choice[]
}

export interface Item {
  id: number
  projectId: number
  type: Kind
  ref: number
  subject: string
  description: string
  status: StatusRef
  milestone: { id: number; name: string; slug: string } | null
  parent: { id: number; ref: number; subject: string } | null
  points: number | null
  assignedTo: UserRef | null
  assignedUsers: UserRef[]
  watchers: UserRef[]
  createdBy: UserRef | null
  tags: string[]
  dueDate: string | null
  issueType: ChoiceRef | null
  severity: ChoiceRef | null
  priority: ChoiceRef | null
  boardOrder: number
  listOrder: number
  isBlocked: boolean
  blockedNote: string
  teamRequirement: boolean
  clientRequirement: boolean
  isIocaine: boolean
  version: number
  createdAt: string
  modifiedAt: string
  finishedAt: string | null
  tasksTotal: number
  tasksDone: number
  commentsCount: number
  attachmentsCount: number
  tasks?: Item[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface ProjectRef {
  id: number
  slug: string
  name: string
}

export interface HistoryEntry {
  id: number
  kind: 'create' | 'change' | 'delete' | 'comment'
  target: { type: Kind | 'wiki'; id: number; ref: number; subject: string }
  project: ProjectRef
  actor: UserRef | null
  viaToken: boolean
  diff: Record<string, [unknown, unknown]>
  comment: { id: number; body: string } | null
  createdAt: string
}

export interface Comment {
  id: number
  author: UserRef | null
  body: string
  createdAt: string
  editedAt: string | null
}

export interface Attachment {
  id: number
  name: string
  size: number
  contentType: string
  createdBy: UserRef | null
  createdAt: string
}

export interface Notice {
  id: number
  kind: 'assigned' | 'comment' | 'create' | 'change' | 'delete'
  actor: UserRef | null
  project: ProjectRef
  itemType: Kind | 'wiki'
  itemRef: number
  itemSubject: string
  createdAt: string
  read: boolean
}

export interface NoticePage extends Page<Notice> {
  unread: number
}

export interface DashItem {
  id: number
  type: Kind
  ref: number
  subject: string
  status: StatusRef
  project: ProjectRef
}

export interface Stats {
  totals: {
    open: number
    closed: number
    blocked: number
    overdue: number
    pointsOpen: number
    pointsClosed: number
  }
  tasks: { open: number; closed: number; iocaine: number }
}

export interface ApiToken {
  id: number
  name: string
  prefix: string
  scopes: string[]
  expiresAt: string | null
  lastUsedAt: string | null
  createdAt: string
}

export interface AdminUser extends UserRef {
  email: string
  isSuperuser: boolean
  isActive: boolean
  createdAt: string
}
