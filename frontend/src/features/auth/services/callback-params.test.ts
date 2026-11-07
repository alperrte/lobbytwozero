import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { forgetCallbackResult, parseCallbackFragment, readCallbackResult } from "./callback-params";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_";

describe("parseCallbackFragment", () => {
  it("bilinen durumları çözer", () => {
    expect(parseCallbackFragment("#status=authenticated")).toEqual({ kind: "status", status: "authenticated" });
    expect(parseCallbackFragment("#status=google_linked")).toEqual({ kind: "status", status: "google_linked" });
    expect(parseCallbackFragment("#status=steam_linked")).toEqual({ kind: "status", status: "steam_linked" });
  });

  it("token, proof ve challenge'ı taşır", () => {
    expect(parseCallbackFragment(`#status=registration_required&token=${TOKEN}`)).toEqual({
      kind: "status",
      status: "registration_required",
      token: TOKEN
    });
    expect(parseCallbackFragment(`#status=email_verification_required&proof=${TOKEN}`)).toMatchObject({ proof: TOKEN });
    expect(parseCallbackFragment("#status=mfa_required&challenge=abc123")).toMatchObject({
      status: "mfa_required",
      challenge: "abc123"
    });
  });

  it("hata kodunu çözer (yalnız BÜYÜK_HARF kodlar)", () => {
    expect(parseCallbackFragment("#error=EXTERNAL_AUTH_FAILED")).toEqual({ kind: "error", code: "EXTERNAL_AUTH_FAILED" });
    expect(parseCallbackFragment("#error=<script>")).toEqual({ kind: "empty" });
    expect(parseCallbackFragment("#error=lowercase")).toEqual({ kind: "empty" });
    expect(parseCallbackFragment("#error=EXTERNAL_AUTH_FAILED&status=authenticated")).toEqual({ kind: "empty" });
  });

  it.each([
    ["boş", ""],
    ["yalnız #", "#"],
    ["bilinmeyen durum", "#status=admin"],
    ["bilinmeyen anahtar", "#status=authenticated&redirect=https://evil.example"],
    ["tekrarlı anahtar", "#status=authenticated&status=mfa_required"],
    ["değersiz anahtar", "#status"],
    ["geçersiz token karakteri", "#status=registration_required&token=a b"],
    ["token içinde adres", "#status=registration_required&token=https://evil.example"],
    ["# yok", "status=authenticated"]
  ])("%s geçersiz sayılır (empty)", (_ad, hash) => {
    expect(parseCallbackFragment(hash)).toEqual({ kind: "empty" });
  });
});

describe("readCallbackResult", () => {
  beforeEach(() => {
    forgetCallbackResult();
    window.history.replaceState(null, "", "/");
  });
  afterEach(() => {
    forgetCallbackResult();
    window.history.replaceState(null, "", "/");
  });

  it("parçayı okur ve adres çubuğundan siler (yol ve sorgu korunur)", () => {
    window.history.replaceState(null, "", `/auth/callback?x=1#status=registration_required&token=${TOKEN}`);

    expect(readCallbackResult()).toMatchObject({ kind: "status", status: "registration_required", token: TOKEN });

    expect(window.location.hash).toBe("");
    expect(window.location.pathname + window.location.search).toBe("/auth/callback?x=1");
  });

  it("okunduktan sonra bellekte kalır; parça yoksa empty verir", () => {
    window.history.replaceState(null, "", "/auth/callback#status=authenticated");
    readCallbackResult();
    expect(readCallbackResult()).toMatchObject({ status: "authenticated" });

    forgetCallbackResult();
    window.history.replaceState(null, "", "/auth/callback");
    expect(readCallbackResult()).toEqual({ kind: "empty" });
  });

  it("sonuç localStorage/sessionStorage'a yazılmaz", () => {
    window.history.replaceState(null, "", `/x#status=registration_required&token=${TOKEN}`);
    readCallbackResult();
    expect(JSON.stringify({ ...window.localStorage, ...window.sessionStorage })).not.toContain(TOKEN);
  });
});
