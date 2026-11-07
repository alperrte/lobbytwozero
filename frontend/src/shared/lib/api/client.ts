const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:5080"
).replace(/\/$/, "");

export type ApiProblem = { code?: string; fields?: string[] };
export class ApiHttpError extends Error {
  constructor(public readonly status: number, public readonly problem?: ApiProblem) {
    super(`API isteği başarısız: ${status}`);
    this.name = "ApiHttpError";
  }
}

export async function apiRequest<T>(
  path: `/${string}`,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    ...options
  });

  if (!response.ok) {
    let problem: ApiProblem | undefined;
    try {
      const body = await response.json();
      const violations = Array.isArray(body?.violations) ? body.violations : [];
      problem = {
        code: typeof body?.code === "string" && /^[A-Z0-9_]{1,80}$/.test(body.code) ? body.code : undefined,
        fields: violations.map((value: { field?: unknown }) => value?.field).filter((value: unknown): value is string => typeof value === "string" && /^[A-Za-z0-9_.[\]]{1,100}$/.test(value)).slice(0, 100)
      };
    } catch { /* JSON olmayan hata gövdesi durum kodunu gizlememeli. */ }
    throw new ApiHttpError(response.status, problem);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
