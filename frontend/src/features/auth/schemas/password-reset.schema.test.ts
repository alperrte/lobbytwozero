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
  const strong = "Aa1!aaaa";

  it("geçerli yeni parola ve eşleşen tekrarı kabul eder", () => {
    expect(validateReset({ newPassword: strong, confirmation: strong })).toEqual({});
  });

  it.each([
    [7, "tooShort"],
    [8, undefined],
    [128, undefined],
    [129, "tooLong"]
  ] as const)("yeni parola %i karakter: %s", (length, expected) => {
    const password = `Aa1!${"a".repeat(length - 4)}`;
    expect(validateReset({ newPassword: password, confirmation: password }).newPassword).toBe(expected);
  });

  it("bileşim kuralı: büyük/küçük harf, rakam ve özel karakter şart", () => {
    expect(validateReset({ newPassword: "aa1!aaaa", confirmation: "aa1!aaaa" }).newPassword).toBe("composition");
    expect(validateReset({ newPassword: "Aa1aaaaa", confirmation: "Aa1aaaaa" }).newPassword).toBe("composition");
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
