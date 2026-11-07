import { describe, expect, it } from "vitest";
import { assertSafeApiBaseUrl, securityHeaders } from "./security-headers";

const value = (headers: ReturnType<typeof securityHeaders>, key: string) =>
  headers.find((header) => header.key === key)?.value;

describe("securityHeaders", () => {
  it("clickjacking'e karşı frame-ancestors ve X-Frame-Options gönderir", () => {
    const headers = securityHeaders(false);
    expect(value(headers, "Content-Security-Policy")).toBe("frame-ancestors 'none'");
    expect(value(headers, "X-Frame-Options")).toBe("DENY");
  });

  it("nosniff, Referrer-Policy ve Permissions-Policy gönderir", () => {
    const headers = securityHeaders(false);
    expect(value(headers, "X-Content-Type-Options")).toBe("nosniff");
    expect(value(headers, "Referrer-Policy")).toBe("strict-origin-when-cross-origin");
    expect(value(headers, "Permissions-Policy")).toContain("camera=()");
    expect(value(headers, "Permissions-Policy")).toContain("microphone=()");
  });

  it("HSTS yalnız üretimde gönderilir; includeSubDomains ve preload içermez", () => {
    expect(value(securityHeaders(false), "Strict-Transport-Security")).toBeUndefined();
    const production = value(securityHeaders(true), "Strict-Transport-Security");
    expect(production).toBe("max-age=31536000");
    expect(production).not.toContain("includeSubDomains");
    expect(production).not.toContain("preload");
  });

  it("başlık anahtarları yinelenmez", () => {
    const keys = securityHeaders(true).map((header) => header.key);
    expect(new Set(keys).size).toBe(keys.length);
  });
});

describe("assertSafeApiBaseUrl", () => {
  it.each([undefined, "", "   "])("boş değer (%j) göreli yol demektir, geçerlidir", (raw) => {
    expect(() => assertSafeApiBaseUrl(raw, true)).not.toThrow();
  });

  it("üretimde https adresini kabul eder", () => {
    expect(() => assertSafeApiBaseUrl("https://api.ltz.example", true)).not.toThrow();
  });

  it.each(["http://localhost:5080", "http://127.0.0.1:5080", "http://[::1]:5080"])(
    "üretim derlemesinde de yerel http (%s) kabul edilir (yerel deneme)",
    (raw) => {
      expect(() => assertSafeApiBaseUrl(raw, true)).not.toThrow();
    }
  );

  it("üretimde yerel olmayan http adresini reddeder", () => {
    expect(() => assertSafeApiBaseUrl("http://api.ltz.example", true)).toThrow(/https/);
    expect(() => assertSafeApiBaseUrl("http://10.0.0.5:5080", true)).toThrow(/https/);
  });

  it("geliştirmede yerel olmayan http'ye izin verir", () => {
    expect(() => assertSafeApiBaseUrl("http://dev-box:5080", false)).not.toThrow();
  });

  it("geçersiz adresi ve http(s) dışı şemayı reddeder", () => {
    expect(() => assertSafeApiBaseUrl("api.ltz.example", false)).toThrow(/geçerli/);
    expect(() => assertSafeApiBaseUrl("ftp://api.ltz.example", false)).toThrow(/http/);
    expect(() => assertSafeApiBaseUrl("javascript:alert(1)", true)).toThrow();
  });

  it("localhost'u andıran ama farklı bir host'u yerel saymaz", () => {
    expect(() => assertSafeApiBaseUrl("http://localhost.evil.example", true)).toThrow(/https/);
  });
});
