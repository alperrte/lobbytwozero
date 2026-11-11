import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { CSRF_HEADER, CSRF_PATH, readCsrfCookie } from "@/shared/lib/session/csrf";
import type { DeviceLink, Metric, PerformanceReport, Series, SessionFilter, SessionPage, SyncConsent } from "../types/performance";

const base = "/api/v1/performance";

function read<T>(path: string, signal: AbortSignal): Promise<T> {
  return apiRequest<T>(`${base}${path}`, {
    cache: "no-store",
    signal: AbortSignal.any([signal, AbortSignal.timeout(20000)])
  });
}

/** Only an explicit CSRF rejection is retried. Unknown mutation outcomes require a fresh read. */
async function write<T>(path: string, method: "PUT" | "DELETE", signal: AbortSignal): Promise<T> {
  const requestSignal = AbortSignal.any([signal, AbortSignal.timeout(20000)]);
  async function primeCsrf() {
    await apiRequest<void>(CSRF_PATH, { cache: "no-store", signal: requestSignal });
  }
  if (!readCsrfCookie()) await primeCsrf();
  for (let attempt = 0; ; attempt++) {
    const csrf = readCsrfCookie();
    if (!csrf) throw new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" });
    try {
      return await apiRequest<T>(`${base}${path}`, { method, cache: "no-store", signal: requestSignal, headers: { [CSRF_HEADER]: csrf } });
    } catch (error) {
      if (attempt === 0 && error instanceof ApiHttpError && error.status === 403 && error.code === "CSRF_TOKEN_INVALID") {
        await primeCsrf();
      } else throw error;
    }
  }
}

export const performanceApi = {
  devices: (signal: AbortSignal) => read<DeviceLink[]>("/devices", signal),
  sessions: (filter: SessionFilter, signal: AbortSignal) => {
    const query = new URLSearchParams({ page: String(filter.page), size: "20" });
    if (filter.status) query.set("status", filter.status);
    if (filter.startedFrom) query.set("startedFrom", filter.startedFrom);
    if (filter.startedTo) query.set("startedTo", filter.startedTo);
    return read<SessionPage>(`/sessions?${query}`, signal);
  },
  series: (sessionId: string, metric: Metric, from: string | undefined, to: string | undefined, signal: AbortSignal) => {
    const query = new URLSearchParams({ metric, maxPoints: "100" });
    if (from) query.set("from", from);
    if (to) query.set("to", to);
    return read<Series>(`/sessions/${encodeURIComponent(sessionId)}/series?${query}`, signal);
  },
  report: (sessionId: string, signal: AbortSignal) => read<PerformanceReport>(`/sessions/${encodeURIComponent(sessionId)}/report`, signal),
  consent: (signal: AbortSignal) => read<SyncConsent>("/sync-consent", signal),
  setConsent: (granted: boolean, signal: AbortSignal) => write<SyncConsent>("/sync-consent", granted ? "PUT" : "DELETE", signal),
  deleteSession: (sessionId: string, signal: AbortSignal) => write<void>(`/sessions/${encodeURIComponent(sessionId)}`, "DELETE", signal),
  deleteAllSessions: (signal: AbortSignal) => write<{ deletedSessions: number }>("/sessions?confirm=true", "DELETE", signal)
};
