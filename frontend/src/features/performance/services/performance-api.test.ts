import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, apiRequest } from "@/shared/lib/api/client";
import { performanceApi } from "./performance-api";
vi.mock("@/shared/lib/api/client", async (original) => ({ ...await original<typeof import("@/shared/lib/api/client")>(), apiRequest: vi.fn() }));

describe("Performance API", () => {
  beforeEach(() => { vi.mocked(apiRequest).mockReset(); document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/"; });
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
  it("primes CSRF before a protected deletion and encodes the resource identifier", async () => {
    vi.mocked(apiRequest).mockImplementation(async (path) => { if (path === "/api/v1/auth/csrf") document.cookie = "XSRF-TOKEN=test-csrf; path=/"; return undefined; });
    await performanceApi.deleteSession("owned/id", new AbortController().signal);
    expect(apiRequest).toHaveBeenNthCalledWith(1, "/api/v1/auth/csrf", expect.any(Object));
    expect(apiRequest).toHaveBeenLastCalledWith("/api/v1/performance/sessions/owned%2Fid", expect.objectContaining({ method: "DELETE", cache: "no-store", headers: { "X-XSRF-TOKEN": "test-csrf" } }));
  });
  it("retries an explicit CSRF rejection once and never retries uncertain mutation responses", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" })).mockResolvedValue(undefined);
    await performanceApi.setConsent(false, new AbortController().signal);
    expect(apiRequest).toHaveBeenCalledTimes(3);
    vi.mocked(apiRequest).mockReset().mockRejectedValue(new ApiHttpError(503));
    await expect(performanceApi.deleteAllSessions(new AbortController().signal)).rejects.toMatchObject({ status: 503 });
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/performance/sessions?confirm=true", expect.objectContaining({ method: "DELETE" }));
  });
  it("fails closed when CSRF cannot be established", async () => {
    vi.mocked(apiRequest).mockResolvedValue(undefined);
    await expect(performanceApi.setConsent(true, new AbortController().signal)).rejects.toMatchObject({ status: 403, code: "CSRF_TOKEN_INVALID" });
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });
});
