export type DeviceLink = {
  id: string;
  deviceId: string;
  label: string;
  status: "ACTIVE" | "DISABLED";
  linkedAt: string;
  lastSyncAt: string | null;
  disabledAt: string | null;
};

export type SessionStatus = "ACTIVE" | "COMPLETED" | "ABANDONED";
export type PerformanceSession = {
  id: string;
  gameId: string;
  gameTitle: string;
  hardwareProfileId: string | null;
  deviceLinkId: string | null;
  source: "MANUAL";
  status: SessionStatus;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number | null;
  sampleCount: number;
};
export type SessionPage = { items: PerformanceSession[]; page: number; size: number; hasNext: boolean; totalElements: number };
export type SessionFilter = { page: number; status?: SessionStatus; startedFrom?: string; startedTo?: string };
export const metrics = ["FPS", "CPU_TEMP_C", "GPU_TEMP_C", "CPU_USAGE_PERCENT", "GPU_USAGE_PERCENT", "RAM_USED_MB", "VRAM_USED_MB", "DISK_READ_MBPS", "DISK_WRITE_MBPS", "FAN_RPM"] as const;
export type Metric = (typeof metrics)[number];
export type SeriesPoint = { at: string; value: number; min: number; max: number; count: number };
export type Series = {
  sessionId: string; metric: Metric; unit: string; resolution: "RAW" | "BUCKET";
  downsampled: boolean; bucketMillis: number | null; aggregation: "NONE" | "AVG_MIN_MAX";
  totalSamples: number; points: SeriesPoint[];
};
export type PerformanceReport = {
  id: string; sessionId: string; resolution: string; graphicsPreset: "LOW" | "MEDIUM" | "HIGH" | "ULTRA" | "CUSTOM";
  avgFps: number | null; p1LowFps: number | null; maxCpuTempC: number | null; maxGpuTempC: number | null;
  avgCpuUsagePercent: number | null; avgGpuUsagePercent: number | null;
  notes: string | null; source: "MANUAL"; createdAt: string; updatedAt: string;
};
