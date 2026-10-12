export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export class TaskboardClient {
  private readonly baseUrl: string;

  constructor(
    url: string,
    private readonly token: string,
  ) {
    this.baseUrl = url.replace(/\/+$/, "").replace(/\/api\/v1$/, "") + "/api/v1";
  }

  private async request<T>(
    method: string,
    path: string,
    options: { query?: Record<string, unknown>; body?: unknown } = {},
  ): Promise<T> {
    const url = new URL(this.baseUrl + path);
    for (const [key, value] of Object.entries(options.query ?? {})) {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    }

    let response: Response;
    try {
      response = await fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${this.token}`,
          Accept: "application/json",
          ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
        },
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      });
    } catch (error) {
      throw new Error(`Taskboard API unreachable at ${this.baseUrl}: ${(error as Error).message}`);
    }

    const text = await response.text();
    let data: unknown = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = null;
    }
    if (!response.ok) {
      const detail = (data as { detail?: string } | null)?.detail;
      throw new ApiError(response.status, detail || `${response.status} ${response.statusText} on ${method} ${path}`);
    }
    return data as T;
  }

  get<T = any>(path: string, query?: Record<string, unknown>): Promise<T> {
    return this.request<T>("GET", path, { query });
  }

  post<T = any>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("POST", path, { body });
  }

  patch<T = any>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("PATCH", path, { body });
  }
}
