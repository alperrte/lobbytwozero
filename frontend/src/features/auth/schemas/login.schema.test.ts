import { describe, expect, it } from "vitest";
import { firstInvalidLoginField, loginLimits, validateLogin } from "./login.schema";

describe("validateLogin", () => {
  it("geçerli girdiyi kabul eder", () => {
    expect(validateLogin({ identifier: "ada@example.com", password: "x" })).toEqual({});
    expect(validateLogin({ identifier: "ada_01", password: "p".repeat(loginLimits.passwordMax) })).toEqual({});
  });

  it("boş alanları zorunlu sayar; kimlik kırpılır, parola kırpılmaz", () => {
    expect(validateLogin({ identifier: "   ", password: "" })).toEqual({
      identifier: "required",
      password: "required"
    });
    expect(validateLogin({ identifier: "ada", password: "   " })).toEqual({});
  });

  it("sınırları uygular", () => {
    const longId = "a".repeat(loginLimits.identifierMax + 1);
    const longPassword = "p".repeat(loginLimits.passwordMax + 1);
    expect(validateLogin({ identifier: longId, password: "x" }).identifier).toBe("tooLong");
    expect(validateLogin({ identifier: "a".repeat(loginLimits.identifierMax), password: "x" })).toEqual({});
    expect(validateLogin({ identifier: "ada", password: longPassword }).password).toBe("tooLong");
  });

  it("ilk hatalı alanı formdaki sıraya göre verir", () => {
    expect(firstInvalidLoginField({})).toBeNull();
    expect(firstInvalidLoginField({ password: "required" })).toBe("password");
    expect(firstInvalidLoginField({ password: "required", identifier: "required" })).toBe("identifier");
  });
});
