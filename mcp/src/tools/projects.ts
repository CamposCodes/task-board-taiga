import { z } from "zod";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { TaskboardClient } from "../client.js";
import { historyEntry, itemSummary, loadProject, page, projectPath, READ, run, sprintId } from "../helpers.js";

const project = z.string().describe('Project slug or id, as returned by taskboard_projects_list (e.g. "site-novo")');

export function registerProjectTools(server: McpServer, client: TaskboardClient) {
  server.registerTool(
    "taskboard_me",
    {
      title: "Current user",
      description: "The user this server acts as, and whether the token can write.",
      annotations: READ,
    },
    () => run(() => client.get("/me")),
  );

  server.registerTool(
    "taskboard_projects_list",
    {
      title: "List projects",
      description: "Projects the current user can see, with the user's role in each.",
      inputSchema: { archived: z.boolean().optional().describe("true lists archived projects instead of active ones") },
      annotations: READ,
    },
    ({ archived }) =>
      run(async () =>
        (await client.get("/projects", { archived })).map((p: any) => ({
          slug: p.slug,
          name: p.name,
          description: p.description,
          private: p.isPrivate,
          archived: p.isArchived,
          myRole: role(p.my),
        })),
      ),
  );

  server.registerTool(
    "taskboard_projects_get",
    {
      title: "Project details",
      description:
        "One project with the statuses of user stories (the kanban columns), tasks and issues, its members, " +
        "sprints, tags and the values accepted for points and for issue type, severity and priority. " +
        "Call this first to learn the names accepted by the other tools.",
      inputSchema: { project },
      annotations: READ,
    },
    ({ project }) =>
      run(async () => {
        const p = await loadProject(client, project);
        const statuses = (kind: string) =>
          p.statuses
            .filter((s: any) => s.kind === kind)
            .map((s: any) => ({
              name: s.name,
              closed: s.isClosed,
              ...(s.isArchived ? { archived: true } : {}),
              ...(s.wipLimit ? { wipLimit: s.wipLimit } : {}),
            }));
        const names = (list: any[]) => list.map((entry) => entry.name);
        return {
          slug: p.slug,
          name: p.name,
          description: p.description,
          private: p.isPrivate,
          archived: p.isArchived,
          myRole: role(p.my),
          myPermissions: p.my.permissions,
          modules: { backlog: p.isBacklogActivated, kanban: p.isKanbanActivated, issues: p.isIssuesActivated, wiki: p.isWikiActivated },
          userStoryStatuses: statuses("us"),
          taskStatuses: statuses("task"),
          issueStatuses: statuses("issue"),
          points: p.points.map((entry: any) => ({ name: entry.name, value: entry.value })),
          issueTypes: names(p.issueTypes),
          severities: names(p.severities),
          priorities: names(p.priorities),
          members: p.members.map((m: any) => ({ username: m.username, fullName: m.fullName, role: m.roleName, admin: m.isAdmin })),
          sprints: p.milestones.map(sprint),
          tags: Object.keys(p.tagsColors),
        };
      }),
  );

  server.registerTool(
    "taskboard_sprints_list",
    {
      title: "List sprints",
      description: "Sprints of a project with dates, open and closed user stories and points. Newest first.",
      inputSchema: { project },
      annotations: READ,
    },
    ({ project }) => run(async () => (await client.get(`${projectPath(project)}/milestones`)).map(sprint)),
  );

  server.registerTool(
    "taskboard_projects_stats",
    {
      title: "Project numbers",
      description:
        "Counts for a project or one sprint: open, closed, blocked and overdue user stories, stories created and " +
        "closed in the last 7 and 30 days, points, open and closed tasks, and the breakdown by status and by assignee.",
      inputSchema: {
        project,
        sprint: z.string().optional().describe('Sprint name or id, or "backlog" for stories without a sprint. Omit for the whole project.'),
      },
      annotations: READ,
    },
    ({ project, sprint }) =>
      run(async () => {
        const stats = await client.get(`${projectPath(project)}/stats`, {
          milestone: await milestoneFilter(client, project, sprint),
        });
        return {
          userStories: stats.totals,
          tasks: stats.tasks,
          byStatus: stats.byStatus.map((s: any) => ({ status: s.status.name, closed: s.status.isClosed, userStories: s.userstories, points: s.points })),
          byAssignee: stats.byAssignee.map((a: any) => ({ assignee: a.user?.username ?? null, open: a.open, closed: a.closed })),
        };
      }),
  );

  server.registerTool(
    "taskboard_projects_activity",
    {
      title: "Project activity",
      description:
        "What happened in a project, newest first: user stories, tasks, issues and wiki pages created, changed " +
        "(with old and new values), deleted and commented, with who did it and when.",
      inputSchema: {
        project,
        since: z.string().optional().describe("Only entries from this ISO-8601 instant on, e.g. 2026-10-01T00:00:00Z"),
        page: z.number().int().min(1).optional(),
        size: z.number().int().min(1).max(200).optional().describe("Entries per page, default 50"),
      },
      annotations: READ,
    },
    ({ project, since, page: pageNumber, size }) =>
      run(async () => {
        const result = await client.get(`${projectPath(project)}/activity`, { since, page: pageNumber, size });
        return page(result, result.items.map(historyEntry));
      }),
  );

  server.registerTool(
    "taskboard_search",
    {
      title: "Search a project",
      description:
        'Finds user stories, tasks, issues and wiki pages by words in subject and description (Portuguese stemming) ' +
        'or by "#ref". Best matches first, up to 50 of each kind.',
      inputSchema: { project, text: z.string().min(1) },
      annotations: READ,
    },
    ({ project, text }) =>
      run(async () => {
        const found = await client.get(`${projectPath(project)}/search`, { q: text });
        return {
          userStories: found.userstories.map(itemSummary),
          tasks: found.tasks.map(itemSummary),
          issues: found.issues.map(itemSummary),
          wikiPages: found.wikiPages,
        };
      }),
  );

  server.registerTool(
    "taskboard_wiki_get",
    {
      title: "Wiki page",
      description: 'Markdown content of a wiki page. Without a page it lists the pages of the project; "home" is the entry page.',
      inputSchema: { project, page: z.string().optional().describe('Page address, e.g. "home"') },
      annotations: READ,
    },
    ({ project, page: slug }) =>
      run(async () => {
        if (slug === undefined) {
          return client.get(`${projectPath(project)}/wiki`);
        }
        const found = await client.get(`${projectPath(project)}/wiki/${encodeURIComponent(slug)}`);
        return { page: found.slug, content: found.content, modifiedAt: found.modifiedAt, lastModifier: found.lastModifier?.username ?? null };
      }),
  );
}

function role(my: any) {
  return my.isOwner ? "owner" : my.isAdmin ? "admin" : (my.roleName ?? "visitor");
}

function sprint(m: any) {
  return {
    name: m.name,
    start: m.estimatedStart,
    finish: m.estimatedFinish,
    closed: m.isClosed,
    userStoriesOpen: m.usOpen,
    userStoriesClosed: m.usClosed,
    pointsOpen: m.pointsOpen,
    pointsClosed: m.pointsClosed,
  };
}

export async function milestoneFilter(client: TaskboardClient, project: string, sprintName: string | undefined) {
  if (sprintName === undefined) {
    return undefined;
  }
  return sprintId(await loadProject(client, project), sprintName) ?? "none";
}
