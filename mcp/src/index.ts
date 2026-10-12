#!/usr/bin/env node
import { webcrypto } from "node:crypto";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { WebStandardStreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js";
import { ApiError, TaskboardClient } from "./client.js";
import { registerItemTools } from "./tools/items.js";
import { registerProjectTools } from "./tools/projects.js";

const INSTRUCTIONS = `Taskboard is a project management tool. A project has user stories (worked on a kanban or planned in sprints),
tasks that belong to a user story or to a sprint, issues (bugs, questions, improvements) and a wiki.
Projects are addressed by slug and every user story, task and issue by its number (#12 is ref 12); the number is unique in the project.
Statuses, sprints, people and issue values are given by name. Start with taskboard_projects_list, then taskboard_projects_get
to learn the valid names. Texts are usually in Portuguese.`;

const readOnly = process.env.TASKBOARD_READ_ONLY === "1";
const MAX_BODY_BYTES = 4 * 1024 * 1024;

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing environment variable ${name}`);
  }
  return value;
}

async function buildServer(client: TaskboardClient) {
  const me = await client.get("/me");
  const canWrite = !readOnly && (!me.viaToken || me.scopes.includes("write"));
  const server = new McpServer({ name: "taskboard-mcp", version: "0.2.0" }, { instructions: INSTRUCTIONS });
  registerProjectTools(server, client);
  registerItemTools(server, client, canWrite);
  return { server, me, canWrite };
}

async function stdio() {
  const url = required("TASKBOARD_URL");
  const { server, me, canWrite } = await buildServer(new TaskboardClient(url, required("TASKBOARD_TOKEN")));
  await server.connect(new StdioServerTransport());
  console.error(`taskboard-mcp: ${url} as ${me.username} (${canWrite ? "read and write" : "read-only"})`);
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    req.on("data", (chunk: Buffer) => {
      size += chunk.length;
      if (size <= MAX_BODY_BYTES) {
        chunks.push(chunk);
      }
    });
    req.on("end", () =>
      size > MAX_BODY_BYTES ? reject(new RangeError("body too large")) : resolve(Buffer.concat(chunks).toString("utf8")),
    );
    req.on("error", reject);
  });
}

function http() {
  (globalThis as { crypto?: unknown }).crypto ??= webcrypto;
  const url = required("TASKBOARD_URL");
  const port = Number(process.env.PORT || 3333);
  const host = process.env.HOST || "127.0.0.1";
  const path = process.env.MCP_PATH || "/mcp";

  const refuse = (res: ServerResponse, status: number, message: string, headers: Record<string, string> = {}) => {
    res.writeHead(status, { "Content-Type": "application/json", ...headers });
    res.end(JSON.stringify({ jsonrpc: "2.0", error: { code: -32000, message }, id: null }));
  };

  createServer(async (req, res) => {
    if (req.url?.split("?")[0] !== path) {
      return refuse(res, 404, "Not found");
    }
    if (req.method !== "POST") {
      return refuse(res, 405, "Method not allowed", { Allow: "POST" });
    }
    const authorization = req.headers.authorization ?? "";
    if (!/^Bearer \S+$/i.test(authorization)) {
      return refuse(res, 401, "Send a Taskboard API token: Authorization: Bearer tb_...", { "WWW-Authenticate": "Bearer" });
    }
    try {
      let body: unknown;
      try {
        body = JSON.parse(await readBody(req));
      } catch (error) {
        return error instanceof RangeError ? refuse(res, 413, "Request too large") : refuse(res, 400, "Invalid JSON");
      }

      const { server } = await buildServer(new TaskboardClient(url, authorization.slice(7)));
      const transport = new WebStandardStreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
      await server.connect(transport);
      try {
        const headers = new Headers();
        for (const [name, value] of Object.entries(req.headers)) {
          if (value !== undefined) {
            headers.set(name, Array.isArray(value) ? value.join(", ") : value);
          }
        }
        const answer = await transport.handleRequest(new Request(`http://${host}:${port}${path}`, { method: "POST", headers }), {
          parsedBody: body,
        });
        const payload = Buffer.from(await answer.arrayBuffer());
        res.writeHead(answer.status, { ...Object.fromEntries(answer.headers), "Content-Length": String(payload.length) });
        res.end(payload);
      } finally {
        await transport.close();
        await server.close();
      }
    } catch (error) {
      if (res.headersSent) {
        return;
      }
      if (error instanceof ApiError && error.status === 401) {
        return refuse(res, 401, "Invalid or revoked Taskboard API token", { "WWW-Authenticate": "Bearer" });
      }
      console.error("taskboard-mcp:", error);
      refuse(res, 502, error instanceof Error ? error.message : "Internal error");
    }
  }).listen(port, host, () => console.error(`taskboard-mcp: http://${host}:${port}${path} -> ${url}`));
}

const fail = (error: unknown) => {
  console.error("taskboard-mcp:", error instanceof Error ? error.message : error);
  process.exit(1);
};

if (process.argv.includes("--http")) {
  try {
    http();
  } catch (error) {
    fail(error);
  }
} else {
  stdio().catch(fail);
}
