import { describe, expect, it } from "vitest";
import {
  firstInvalidVerifyField,
  normalizeVerificationCode,
  validateResendEmail,
  validateVerifyEmail
} from "./verify-email.schema";

describe("normalizeVerificationCode", () => {
  it("yalnız rakamları alır, 6 haneye kırpar ve baştaki sıfırı korur", () => {
    expect(normalizeVerificationCode("012345")).toBe("012345");
    expect(normalizeVerificationCode("123 456")).toBe("123456");
    expect(normalizeVerificationCode("123-456")).toBe("123456");
    expect(normalizeVerificationCode("1234567890")).toBe("123456");
    expect(normalizeVerificationCode("abc")).toBe("");
    expect(typeof normalizeVerificationCode("000000")).toBe("string");
  });
});

describe("validateVerifyEmail", () => {
  const valid = { email: "ada@example.com", code: "012345", password: "dogru-parola-123456" };

  it("geçerli girdiyi kabul eder (başı 0 olan kod dahil)", () => {
    expect(validateVerifyEmail(valid)).toEqual({});
  });

  it.each([
    ["", "required"],
    ["12345", "pattern"],
    ["1234567", "pattern"],
    ["12a456", "pattern"],
    ["123456", undefined]
  ] as const)("kod %j: %s", (code, expected) => {
    expect(validateVerifyEmail({ ...valid, code }).code).toBe(expected);
  });

  it("e-postayı ve parolayı denetler", () => {
    expect(validateVerifyEmail({ ...valid, email: "" }).email).toBe("required");
    expect(validateVerifyEmail({ ...valid, email: "x" }).email).toBe("invalid");
    expect(validateVerifyEmail({ ...valid, password: "" }).password).toBe("required");
    expect(validateVerifyEmail({ ...valid, password: "p".repeat(129) }).password).toBe("tooLong");
    expect(validateVerifyEmail({ ...valid, password: "p".repeat(128) }).password).toBeUndefined();
  });

  it("ilk hatalı alanı formdaki sıraya göre verir", () => {
    expect(firstInvalidVerifyField({})).toBeNull();
    expect(firstInvalidVerifyField({ password: "required", code: "required" })).toBe("code");
    expect(firstInvalidVerifyField(validateVerifyEmail({ email: "", code: "", password: "" }))).toBe("email");
  });
});

describe("validateResendEmail", () => {
  it("yalnız e-postayı denetler", () => {
    expect(validateResendEmail("ada@example.com")).toBeUndefined();
    expect(validateResendEmail("")).toBe("required");
    expect(validateResendEmail("ada")).toBe("invalid");
  });
});
