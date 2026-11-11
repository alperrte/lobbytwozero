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
});
