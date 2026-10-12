import { z } from "zod";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { TaskboardClient } from "../client.js";
import {
  choiceId,
  historyEntry,
  itemByRef,
  itemDetail,
  itemSummary,
  type Kind,
  KINDS,
  loadProject,
  memberId,
  page,
  projectPath,
  READ,
  run,
  sprintId,
  statusId,
  WRITE,
} from "../helpers.js";

const project = z.string().describe("Project slug or id");
const ref = z.number().int().positive().describe("Number of the user story, task or issue (#12 is 12)");
const status = z.string().describe('Status name, e.g. "Em andamento" (see taskboard_projects_get)');
const sprint = z.string().describe('Sprint name or id, or "backlog" for no sprint');
const assignee = z.string().describe('Username of a project member, or "none"');

const listFilters = {
  project,
  status: status.optional(),
  sprint: sprint.optional(),
  assignee: z.string().optional().describe('Username, "me" or "none" (unassigned)'),
  tag: z.string().optional(),
  text: z.string().optional().describe('Words to find in subject and description, or "#12" for a ref'),
  dueBefore: z.string().optional().describe("Due date earlier than this day (YYYY-MM-DD); with closed=false it lists what is overdue"),
  modifiedSince: z.string().optional().describe("Changed from this ISO-8601 instant on"),
  closed: z.boolean().optional().describe("true: only in a closed status; false: only open"),
  blocked: z.boolean().optional(),
  sort: z.string().optional().describe('order (default), list, ref, created, modified, due or points; prefix "-" for descending'),
  page: z.number().int().min(1).optional(),
  size: z.number().int().min(1).max(200).optional().describe("Items per page, default 50"),
};

const LIST_HELP: Record<Kind, string> = {
  us: "User stories of a project, in kanban order by default. A story may be in a sprint or in the backlog.",
  task: "Tasks of a project, each with the user story it belongs to. A task without a story belongs to a sprint.",
  issue: "Issues (bugs, questions, improvements) of a project, with type, severity and priority.",
};

export function registerItemTools(server: McpServer, client: TaskboardClient, canWrite: boolean) {
  for (const kind of Object.keys(KINDS) as Kind[]) {
    const { path, many } = KINDS[kind];
    server.registerTool(
      `taskboard_${path}_list`,
      {
        title: `List ${many}`,
        description:
          `${LIST_HELP[kind]} One summary line each (no description). Combine filters to answer questions such as ` +
          "what is overdue, blocked or unassigned. The answer has the total, so ask for the next page when total is " +
          "larger than page * size.",
        inputSchema: {
          ...listFilters,
          ...(kind === "task" ? { userStory: z.number().int().positive().optional().describe("Only the tasks of this user story (its number)") } : {}),
        },
        annotations: READ,
      },
      (args: any) =>
        run(async () => {
          const p = args.status !== undefined || args.sprint !== undefined ? await loadProject(client, args.project) : null;
          const result = await client.get(`${projectPath(args.project)}/${path}`, {
            status: args.status !== undefined ? statusId(p, kind, args.status) : undefined,
            milestone: args.sprint !== undefined ? (sprintId(p, args.sprint) ?? "none") : undefined,
            parent: args.userStory !== undefined ? (await itemByRef(client, args.project, args.userStory)).id : undefined,
            assignee: args.assignee,
            tag: args.tag,
            q: args.text,
            dueBefore: args.dueBefore,
            modifiedSince: args.modifiedSince,
            closed: args.closed,
            blocked: args.blocked,
            sort: args.sort,
            page: args.page,
            size: args.size,
          });
          return page(result, result.items.map(itemSummary));
        }),
    );
  }

  server.registerTool(
    "taskboard_items_get",
    {
      title: "User story, task or issue details",
      description:
        "One item by its number, whatever its kind, with the description. A user story comes with its tasks. " +
        "Ask for comments and history only when needed.",
      inputSchema: {
        project,
        ref,
        include: z.array(z.enum(["comments", "history"])).optional().describe("Extra sections to load"),
      },
      annotations: READ,
    },
    ({ project, ref, include }) =>
      run(async () => {
        const item = await itemByRef(client, project, ref);
        const result: Record<string, unknown> = itemDetail(item);
        if (include?.includes("comments")) {
          result.commentList = (await client.get(`/items/${item.id}/comments`)).map((c: any) => ({
            author: c.author?.username ?? null,
            when: c.createdAt,
            body: c.body,
          }));
        }
        if (include?.includes("history")) {
          result.history = (await client.get(`/items/${item.id}/history`)).map(historyEntry);
        }
        return result;
      }),
  );

  if (!canWrite) {
    return;
  }

  const createFields = {
    project,
    subject: z.string().min(1).max(500),
    description: z.string().optional().describe("Markdown"),
    status: status.optional(),
    assignee: assignee.optional(),
    tags: z.array(z.string()).optional(),
    dueDate: z.string().optional().describe("YYYY-MM-DD"),
  };

  server.registerTool(
    "taskboard_userstories_create",
    {
      title: "Create user story",
      description: "Creates a user story. Without a status it goes to the first open column; without a sprint, to the backlog.",
      inputSchema: { ...createFields, sprint: sprint.optional(), points: z.number().min(0).optional() },
      annotations: WRITE,
    },
    (args) =>
      run(async () => {
        const p = await loadProject(client, args.project);
        return itemDetail(
          await client.post(`${projectPath(args.project)}/userstories`, {
            ...common(p, "us", args),
            milestoneId: args.sprint !== undefined ? sprintId(p, args.sprint) : undefined,
            points: args.points,
          }),
        );
      }),
  );

  server.registerTool(
    "taskboard_tasks_create",
    {
      title: "Create task",
      description: "Adds a task to a user story, or to a sprint when the task belongs to no story.",
      inputSchema: {
        ...createFields,
        userStory: z.number().int().positive().optional().describe("Number of the user story that receives the task"),
        sprint: sprint.optional().describe("Sprint of a task without a user story"),
      },
      annotations: WRITE,
    },
    (args) =>
      run(async () => {
        const p = await loadProject(client, args.project);
        return itemDetail(
          await client.post(`${projectPath(args.project)}/tasks`, {
            ...common(p, "task", args),
            parentId: args.userStory !== undefined ? (await itemByRef(client, args.project, args.userStory)).id : undefined,
            milestoneId: args.sprint !== undefined ? sprintId(p, args.sprint) : undefined,
          }),
        );
      }),
  );

  server.registerTool(
    "taskboard_issues_create",
    {
      title: "Create issue",
      description: "Reports an issue. Type, severity and priority take the project's defaults when omitted.",
      inputSchema: {
        ...createFields,
        issueType: z.string().optional().describe('e.g. "Bug" (see taskboard_projects_get)'),
        severity: z.string().optional(),
        priority: z.string().optional(),
      },
      annotations: WRITE,
    },
    (args) =>
      run(async () => {
        const p = await loadProject(client, args.project);
        return itemDetail(
          await client.post(`${projectPath(args.project)}/issues`, {
            ...common(p, "issue", args),
            typeId: args.issueType !== undefined ? choiceId(p, "issueTypes", args.issueType) : undefined,
            severityId: args.severity !== undefined ? choiceId(p, "severities", args.severity) : undefined,
            priorityId: args.priority !== undefined ? choiceId(p, "priorities", args.priority) : undefined,
          }),
        );
      }),
  );

  server.registerTool(
    "taskboard_items_update",
    {
      title: "Update user story, task or issue",
      description:
        "Changes only the fields given, on the item with that number. Use null to clear sprint, assignee, points " +
        "or dueDate. Points and sprint apply to user stories; issueType, severity and priority to issues. " +
        "Set a closed status to finish the item.",
      inputSchema: {
        project,
        ref,
        subject: z.string().min(1).max(500).optional(),
        description: z.string().optional().describe("Markdown; replaces the whole description"),
        status: status.optional(),
        sprint: sprint.nullable().optional(),
        assignee: assignee.nullable().optional(),
        points: z.number().min(0).nullable().optional(),
        tags: z.array(z.string()).optional().describe("Replaces the whole list"),
        dueDate: z.string().nullable().optional().describe("YYYY-MM-DD"),
        blocked: z.boolean().optional(),
        blockedNote: z.string().optional().describe("Why the item is blocked"),
        issueType: z.string().optional(),
        severity: z.string().optional(),
        priority: z.string().optional(),
      },
      annotations: { ...WRITE, idempotentHint: true },
    },
    (args) =>
      run(async () => {
        const item = await itemByRef(client, args.project, args.ref);
        const p = await loadProject(client, args.project);
        const body: Record<string, unknown> = { version: item.version };
        if (args.subject !== undefined) body.subject = args.subject;
        if (args.description !== undefined) body.description = args.description;
        if (args.status !== undefined) body.statusId = statusId(p, item.type, args.status);
        if (args.sprint !== undefined) body.milestoneId = args.sprint === null ? null : sprintId(p, args.sprint);
        if (args.assignee !== undefined) body.assignedTo = args.assignee === null ? null : memberId(p, args.assignee);
        if (args.points !== undefined) body.points = args.points;
        if (args.tags !== undefined) body.tags = args.tags;
        if (args.dueDate !== undefined) body.dueDate = args.dueDate;
        if (args.blocked !== undefined) body.isBlocked = args.blocked;
        if (args.blockedNote !== undefined) body.blockedNote = args.blockedNote;
        if (args.issueType !== undefined) body.typeId = choiceId(p, "issueTypes", args.issueType);
        if (args.severity !== undefined) body.severityId = choiceId(p, "severities", args.severity);
        if (args.priority !== undefined) body.priorityId = choiceId(p, "priorities", args.priority);
        return itemDetail(await client.patch(`/items/${item.id}`, body));
      }),
  );

  server.registerTool(
    "taskboard_userstories_move",
    {
      title: "Move user story on the kanban",
      description: "Moves a user story to a column and position, like dragging it on the kanban.",
      inputSchema: {
        project,
        ref,
        status: status.optional().describe("Destination column; omit to reorder inside the current column"),
        position: z.enum(["top", "bottom"]).optional().describe("Default: bottom"),
        before: z.number().int().positive().optional().describe("Number of the story that must come right after this one; overrides position"),
      },
      annotations: { ...WRITE, idempotentHint: true },
    },
    ({ project, ref, status, position, before }) =>
      run(async () => {
        const story = await itemByRef(client, project, ref);
        if (story.type !== "us") {
          throw new Error(`#${ref} is a ${KINDS[story.type as Kind].one}, not a user story. Use taskboard_items_update to change its status.`);
        }
        const targetStatus = status !== undefined ? statusId(await loadProject(client, project), "us", status) : story.status.id;
        let beforeId: number | null = null;
        if (before !== undefined) {
          beforeId = (await itemByRef(client, project, before)).id;
        } else if (position === "top") {
          const column = await client.get(`${projectPath(project)}/userstories`, { status: targetStatus, size: 2 });
          beforeId = column.items.find((other: any) => other.id !== story.id)?.id ?? null;
        }
        return itemDetail(await client.post(`/items/${story.id}/move`, { statusId: targetStatus, beforeId }));
      }),
  );

  server.registerTool(
    "taskboard_userstories_set_sprint",
    {
      title: "Send user stories to a sprint",
      description: 'Puts several user stories in a sprint at once, or back in the backlog with sprint "backlog". Their tasks follow.',
      inputSchema: { project, refs: z.array(ref).min(1).max(100), sprint },
      annotations: { ...WRITE, idempotentHint: true },
    },
    ({ project, refs, sprint }) =>
      run(async () => {
        const milestoneId = sprintId(await loadProject(client, project), sprint);
        const ids = [];
        for (const storyRef of refs) {
          ids.push((await itemByRef(client, project, storyRef)).id);
        }
        return client.post(`${projectPath(project)}/userstories/bulk-milestone`, { ids, milestoneId });
      }),
  );

  server.registerTool(
    "taskboard_items_comment",
    {
      title: "Comment on a user story, task or issue",
      description: "Adds a comment. The comment is signed by the user of the token.",
      inputSchema: { project, ref, body: z.string().min(1).max(20000).describe("Markdown") },
      annotations: WRITE,
    },
    ({ project, ref, body }) =>
      run(async () => {
        const item = await itemByRef(client, project, ref);
        const comment = await client.post(`/items/${item.id}/comments`, { body });
        return { item: `${item.type} #${item.ref} ${item.subject}`, author: comment.author?.username ?? null, when: comment.createdAt, body: comment.body };
      }),
  );
}

function common(p: any, kind: Kind, args: { subject: string; description?: string; status?: string; assignee?: string; tags?: string[]; dueDate?: string }) {
  return {
    subject: args.subject,
    description: args.description,
    statusId: args.status !== undefined ? statusId(p, kind, args.status) : undefined,
    assignedTo: args.assignee !== undefined ? memberId(p, args.assignee) : undefined,
    tags: args.tags,
    dueDate: args.dueDate,
  };
}
