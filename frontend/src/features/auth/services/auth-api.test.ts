import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi, toLoginResult } from "./auth-api";
import { AuthApiError } from "./auth-http";

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=tok; path=/";
});
afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
});

function stub(status: number, body?: unknown) {
  const fn = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" }
    })
  );
  vi.stubGlobal("fetch", fn);
  return fn;
}

describe("toLoginResult", () => {
  it("oturum açıldı yanıtını kabul eder", () => {
    expect(toLoginResult({ status: "AUTHENTICATED", accessTokenExpiresAt: "2026-11-06T10:00:00Z" })).toEqual({
      status: "AUTHENTICATED",
      accessTokenExpiresAt: "2026-11-06T10:00:00Z"
    });
  });

  it("MFA durumlarını challenge ile kabul eder", () => {
    const body = { challengeId: "c1", challengeExpiresAt: "2026-11-06T10:05:00Z" };
    expect(toLoginResult({ status: "MFA_REQUIRED", ...body })).toMatchObject({ status: "MFA_REQUIRED" });
    expect(toLoginResult({ status: "MFA_ENROLLMENT_REQUIRED", ...body })).toMatchObject({
      status: "MFA_ENROLLMENT_REQUIRED"
    });
  });

  it.each([
    ["boş gövde", null],
    ["bilinmeyen durum", { status: "OTHER" }],
    ["süresiz AUTHENTICATED", { status: "AUTHENTICATED" }],
    ["challenge'sız MFA", { status: "MFA_REQUIRED" }],
    ["metin", "AUTHENTICATED"]
  ])("%s oturum açılmış sayılmaz", (_ad, value) => {
    expect(() => toLoginResult(value)).toThrow(AuthApiError);
  });
});

describe("authApi", () => {
  it("login doğru yol, yöntem ve gövde ile çağrılır", async () => {
    const fn = stub(200, { status: "AUTHENTICATED", accessTokenExpiresAt: "2026-11-06T10:00:00Z" });

    await authApi.login("ada", "parola-123");

    const [url, init] = fn.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:5080/api/v1/auth/login");
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual({ identifier: "ada", password: "parola-123" });
  });

  it("getMe ve logout doğru uçlara gider", async () => {
    const me = { accountId: "a1", roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "x" };
    const fn = stub(200, me);
    await expect(authApi.getMe()).resolves.toEqual(me);
    expect(String(fn.mock.calls[0]![0])).toBe("http://localhost:5080/api/v1/auth/me");

    const out = stub(204);
    await authApi.logout();
    const [url, init] = out.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:5080/api/v1/auth/session/logout");
    expect(init.method).toBe("POST");
  });
});
