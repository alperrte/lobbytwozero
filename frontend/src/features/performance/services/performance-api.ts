import { apiRequest } from "@/shared/lib/api/client";
import type { DeviceLink } from "../types/performance";

const base = "/api/v1/performance";

function read<T>(path: string, signal: AbortSignal): Promise<T> {
  return apiRequest<T>(`${base}${path}`, {
    cache: "no-store",
    signal: AbortSignal.any([signal, AbortSignal.timeout(20000)])
  });
}

export const performanceApi = {
  devices: (signal: AbortSignal) => read<DeviceLink[]>("/devices", signal)
};
