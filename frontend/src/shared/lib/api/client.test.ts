import {
  afterEach,
  describe,
  expect,
  it,
  vi
} from "vitest";

import { apiRequest } from "./client";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Ortak API client", () => {
  it("API Gateway uzerinden JSON cevabi okur", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ id: 123 })
    });

    vi.stubGlobal("fetch", fetchMock);

    const result = await apiRequest<{ id: number }>(
      "/api/v1/game/example"
    );

    expect(result).toEqual({ id: 123 });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/game/example"),
      expect.objectContaining({
        credentials: "include"
      })
    );
  });

  it("HTTP hatasini status bilgisiyle firlatir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404
      })
    );

    await expect(
      apiRequest("/api/v1/game/unknown")
    ).rejects.toMatchObject({
      name: "ApiHttpError",
      status: 404
    });
  });

  it("204 cevabini dogru isler", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 204
      })
    );

    await expect(
      apiRequest<void>("/api/v1/game/example")
    ).resolves.toBeUndefined();
  });

  it("ProblemDetail içinden yalnız güvenli kodu ve alan adlarını taşır", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({ code: "INVALID_FEEDBACK", detail: "private internal trace", rejectedValue: "private input", violations: [{ field: "title" }, { field: "items[0].body" }, { field: "<script>" }] }) }));
    await expect(apiRequest("/api/v1/game/feedback")).rejects.toMatchObject({ status: 400, problem: { code: "INVALID_FEEDBACK", fields: ["title", "items[0].body"] } });
    try { await apiRequest("/api/v1/game/feedback"); } catch (error) { expect(JSON.stringify(error)).not.toContain("private"); }
  });
});
