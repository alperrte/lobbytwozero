import {
  afterEach,
  describe,
  expect,
  it,
  vi
} from "vitest";

import { ApiHttpError, apiRequest } from "./client";

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

  it("hata gövdesinden yalnız code, errors[] ve Retry-After'ı taşır", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: "TOO_MANY_ATTEMPTS",
            detail: "Gizli-Sunucu-Metni",
            errors: [{ field: "password", code: "tooShort" }, { bozuk: true }, "metin"]
          }),
          { status: 429, headers: { "Content-Type": "application/json", "Retry-After": "42" } }
        )
      )
    );

    const error = await apiRequest("/api/v1/auth/login").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiHttpError);
    expect(error).toMatchObject({
      status: 429,
      code: "TOO_MANY_ATTEMPTS",
      retryAfterSeconds: 42,
      fieldErrors: [{ field: "password", code: "tooShort" }]
    });
    expect(JSON.stringify(error)).not.toContain("Gizli-Sunucu-Metni");
    expect((error as Error).message).not.toContain("Gizli-Sunucu-Metni");
  });

  it("gövdesiz, JSON olmayan ya da bozuk başlıklı hatada boş ayrıntıyla firlatır", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("<html>", { status: 500, headers: { "Retry-After": "abc" } }))
    );

    const error = await apiRequest("/api/v1/game/x").catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 500, code: undefined, fieldErrors: [], retryAfterSeconds: undefined });
  });

  it("Retry-After en çok bir saate kırpılır; sıfır ve negatif yok sayılır", async () => {
    const respond = (value: string) =>
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 503, headers: { "Retry-After": value } })));

    respond("999999");
    await expect(apiRequest("/api/v1/game/x")).rejects.toMatchObject({ retryAfterSeconds: 3600 });
    respond("0");
    await expect(apiRequest("/api/v1/game/x")).rejects.toMatchObject({ retryAfterSeconds: undefined });
    respond("-5");
    await expect(apiRequest("/api/v1/game/x")).rejects.toMatchObject({ retryAfterSeconds: undefined });
  });

  it("çağıranın yöntem, başlık, gövde ve iptal sinyalini aktarır; çerezleri varsayılan olarak gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);
    const controller = new AbortController();

    await apiRequest<void>("/api/v1/auth/login", {
      method: "POST",
      headers: { "X-XSRF-TOKEN": "t" },
      body: "{}",
      signal: controller.signal
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/auth/login"),
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        headers: { "X-XSRF-TOKEN": "t" },
        body: "{}",
        signal: controller.signal
      })
    );
  });

  it("ProblemDetail içinden yalnız güvenli kodu ve alan adlarını taşır", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({ code: "INVALID_FEEDBACK", detail: "private internal trace", rejectedValue: "private input", violations: [{ field: "title" }, { field: "items[0].body" }, { field: "<script>" }] }) }));
    await expect(apiRequest("/api/v1/game/feedback")).rejects.toMatchObject({ status: 400, problem: { code: "INVALID_FEEDBACK", fields: ["title", "items[0].body"] } });
    try { await apiRequest("/api/v1/game/feedback"); } catch (error) { expect(JSON.stringify(error)).not.toContain("private"); }
  });
});
