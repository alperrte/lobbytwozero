import { apiRequest } from "@/shared/lib/api/client";
import type { DeviceLink, Metric, PerformanceReport, Series, SessionFilter, SessionPage } from "../types/performance";

const base = "/api/v1/performance";

function read<T>(path: string, signal: AbortSignal): Promise<T> {
  return apiRequest<T>(`${base}${path}`, {
    cache: "no-store",
    signal: AbortSignal.any([signal, AbortSignal.timeout(20000)])
  });
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
  report: (sessionId: string, signal: AbortSignal) => read<PerformanceReport>(`/sessions/${encodeURIComponent(sessionId)}/report`, signal)
};
