import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import type { TaskboardClient } from "./client.js";

export const READ = { readOnlyHint: true, openWorldHint: false } as const;
export const WRITE = { readOnlyHint: false, destructiveHint: false, openWorldHint: false } as const;

export type Kind = "us" | "task" | "issue";

export const KINDS: Record<Kind, { path: string; one: string; many: string }> = {
  us: { path: "userstories", one: "user story", many: "user stories" },
  task: { path: "tasks", one: "task", many: "tasks" },
  issue: { path: "issues", one: "issue", many: "issues" },
};

export async function run(body: () => Promise<unknown>): Promise<CallToolResult> {
  try {
    return { content: [{ type: "text", text: JSON.stringify(await body(), null, 2) }] };
  } catch (error) {
    return { isError: true, content: [{ type: "text", text: error instanceof Error ? error.message : String(error) }] };
  }
}

export const projectPath = (project: string) => `/projects/${encodeURIComponent(project)}`;

export function loadProject(client: TaskboardClient, project: string): Promise<any> {
  return client.get(projectPath(project));
}

export function itemByRef(client: TaskboardClient, project: string, ref: number): Promise<any> {
  return client.get(`${projectPath(project)}/items/by-ref/${ref}`);
}

const same = (a: unknown, b: unknown) => String(a).trim().toLowerCase() === String(b).trim().toLowerCase();

function byName(list: any[], value: string | number, what: string): number {
  const found = list.find((entry) => entry.id === Number(value)) ?? list.find((entry) => same(entry.name, value));
  if (!found) {
    throw new Error(`Unknown ${what} "${value}". Valid: ${list.map((entry) => entry.name).join(", ")}`);
  }
  return found.id;
}

export function statusId(project: any, kind: Kind, value: string | number): number {
  return byName(project.statuses.filter((s: any) => s.kind === kind), value, `${KINDS[kind].one} status`);
}

export function choiceId(project: any, list: "issueTypes" | "severities" | "priorities", value: string | number): number {
  return byName(project[list], value, list === "issueTypes" ? "issue type" : list === "severities" ? "severity" : "priority");
}

export function memberId(project: any, value: string | number | null): number | null {
  if (value === null || same(value, "none")) {
    return null;
  }
  const found =
    project.members.find((m: any) => m.userId === Number(value)) ??
    project.members.find((m: any) => same(m.username, value) || same(m.fullName, value));
  if (!found) {
    throw new Error(
      `"${value}" is not a member of this project. Members: ${project.members.map((m: any) => m.username).join(", ")}`,
    );
  }
  return found.userId;
}

export function sprintId(project: any, value: string | number | null): number | null {
  if (value === null || same(value, "backlog") || same(value, "none")) {
    return null;
  }
  const found =
    project.milestones.find((m: any) => m.id === Number(value)) ??
    project.milestones.find((m: any) => same(m.name, value) || same(m.slug, value));
  if (!found) {
    const names = project.milestones.map((m: any) => m.name).join(", ") || "(no sprints)";
    throw new Error(`Unknown sprint "${value}". Sprints: ${names}. Use "backlog" for no sprint.`);
  }
  return found.id;
}

const clip = (text: string, max: number) => (text.length > max ? text.slice(0, max) + "…" : text);

export function itemSummary(item: any) {
  return {
    type: item.type,
    ref: item.ref,
    subject: item.subject,
    status: item.status.name,
    closed: item.status.isClosed,
    sprint: item.milestone?.name ?? null,
    assignees: item.assignedUsers.map((user: any) => user.username),
    ...(item.type === "us" ? { points: item.points, tasks: `${item.tasksDone}/${item.tasksTotal}` } : {}),
    ...(item.type === "task" ? { userStory: item.parent ? `#${item.parent.ref} ${item.parent.subject}` : null } : {}),
    ...(item.type === "issue"
      ? { issueType: item.issueType?.name ?? null, severity: item.severity?.name ?? null, priority: item.priority?.name ?? null }
      : {}),
    tags: item.tags,
    dueDate: item.dueDate,
    blocked: item.isBlocked ? item.blockedNote || true : false,
    comments: item.commentsCount,
    attachments: item.attachmentsCount,
    createdAt: item.createdAt,
    modifiedAt: item.modifiedAt,
    finishedAt: item.finishedAt,
  };
}

export function itemDetail(item: any) {
  return {
    ...itemSummary(item),
    description: item.description,
    createdBy: item.createdBy?.username ?? null,
    watchers: item.watchers.map((user: any) => user.username),
    ...(item.type === "us"
      ? {
          teamRequirement: item.teamRequirement,
          clientRequirement: item.clientRequirement,
          taskList: (item.tasks ?? []).map((task: any) => ({
            ref: task.ref,
            subject: task.subject,
            status: task.status.name,
            closed: task.status.isClosed,
            assignees: task.assignedUsers.map((user: any) => user.username),
          })),
        }
      : {}),
  };
}

export function historyEntry(entry: any) {
  const changes: Record<string, unknown> = {};
  for (const [field, pair] of Object.entries(entry.diff ?? {})) {
    changes[field] = field === "description" ? "changed" : pair;
  }
  const target = entry.target.type === "wiki" ? `wiki page ${entry.target.subject}` : `${entry.target.type} #${entry.target.ref} ${entry.target.subject}`;
  return {
    when: entry.createdAt,
    who: entry.actor?.username ?? null,
    viaToken: entry.viaToken,
    action: entry.kind,
    target,
    ...(Object.keys(changes).length ? { changes } : {}),
    ...(entry.comment ? { comment: clip(entry.comment.body, 500) } : {}),
  };
}

export function page(result: any, items: unknown[]) {
  return { total: result.total, page: result.page, size: result.size, items };
}
