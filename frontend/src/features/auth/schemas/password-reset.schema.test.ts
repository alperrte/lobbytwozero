import { describe, expect, it } from "vitest";
import { firstInvalidResetField, validateForgotPassword, validateReset } from "./password-reset.schema";

describe("validateForgotPassword", () => {
  it("geçerli e-postayı kabul eder; boş, biçimsiz ve çok uzunu reddeder", () => {
    expect(validateForgotPassword("ada@example.com")).toEqual({});
    expect(validateForgotPassword("  ada@example.com  ")).toEqual({});
    expect(validateForgotPassword("")).toEqual({ email: "required" });
    expect(validateForgotPassword("ada")).toEqual({ email: "invalid" });
    expect(validateForgotPassword(`${"a".repeat(250)}@x.test`)).toEqual({ email: "tooLong" });
  });
});

describe("validateReset", () => {
  const strong = "p".repeat(15);

  it("geçerli yeni parola ve eşleşen tekrarı kabul eder", () => {
    expect(validateReset({ newPassword: strong, confirmation: strong })).toEqual({});
  });

  it.each([
    [14, "tooShort"],
    [15, undefined],
    [128, undefined],
    [129, "tooLong"]
  ] as const)("yeni parola %i karakter: %s", (length, expected) => {
    const password = "p".repeat(length);
    expect(validateReset({ newPassword: password, confirmation: password }).newPassword).toBe(expected);
  });

  it("boş alanlar zorunludur; tekrar farklıysa eşleşmez", () => {
    expect(validateReset({ newPassword: "", confirmation: "" })).toEqual({ newPassword: "required", confirmation: "required" });
    expect(validateReset({ newPassword: strong, confirmation: `${strong}x` })).toEqual({ confirmation: "mismatch" });
  });

  it("parola kırpılmaz: sonda boşluk fark yaratır", () => {
    expect(validateReset({ newPassword: strong, confirmation: `${strong} ` }).confirmation).toBe("mismatch");
  });

  it("ilk hatalı alanı formdaki sıraya göre verir", () => {
    expect(firstInvalidResetField({})).toBeNull();
    expect(firstInvalidResetField({ confirmation: "mismatch" })).toBe("confirmation");
    expect(firstInvalidResetField({ confirmation: "required", newPassword: "required" })).toBe("newPassword");
  });
});
