import { describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/shared/lib/api/client";
import { performanceApi } from "./performance-api";
vi.mock("@/shared/lib/api/client", () => ({ apiRequest: vi.fn() }));

describe("Performance API", () => {
  it("uses the existing gateway client with no-store and an abort signal", async () => {
    vi.mocked(apiRequest).mockResolvedValue([]);
    await performanceApi.devices(new AbortController().signal);
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/performance/devices", expect.objectContaining({ cache: "no-store", signal: expect.any(AbortSignal) }));
  });
  it("encodes server session filters and fixes the payload limit", async () => {
    await performanceApi.sessions({ page: 2, status: "COMPLETED", startedFrom: "2026-01-01T00:00:00Z" }, new AbortController().signal);
    expect(apiRequest).toHaveBeenLastCalledWith("/api/v1/performance/sessions?page=2&size=20&status=COMPLETED&startedFrom=2026-01-01T00%3A00%3A00Z", expect.any(Object));
  });
  it("bounds chart points and encodes untrusted session identifiers", async () => {
    await performanceApi.series("id/../../other", "FPS", undefined, undefined, new AbortController().signal);
    expect(apiRequest).toHaveBeenLastCalledWith("/api/v1/performance/sessions/id%2F..%2F..%2Fother/series?metric=FPS&maxPoints=100", expect.any(Object));
    await performanceApi.report("id/other", new AbortController().signal);
    expect(apiRequest).toHaveBeenLastCalledWith("/api/v1/performance/sessions/id%2Fother/report", expect.objectContaining({ cache: "no-store" }));
  });
});
