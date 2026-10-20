const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:5080"
).replace(/\/$/, "");

export class ApiHttpError extends Error {
  constructor(public readonly status: number) {
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
    throw new ApiHttpError(response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
