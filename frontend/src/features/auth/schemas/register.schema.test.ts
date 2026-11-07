import { describe, expect, it } from "vitest";
import {
  firstInvalidRegisterField,
  registerLimits,
  validateRegister,
  type RegisterValues
} from "./register.schema";

const valid: RegisterValues = {
  username: "ada_01",
  email: "ada@example.com",
  password: "p".repeat(registerLimits.passwordMin),
  acceptTerms: true
};

describe("validateRegister", () => {
  it("geçerli girdiyi kabul eder", () => {
    expect(validateRegister(valid)).toEqual({});
  });

  it.each([
    [2, "pattern"],
    [3, undefined],
    [20, undefined],
    [21, "pattern"]
  ] as const)("kullanıcı adı %i karakter: %s", (length, expected) => {
    expect(validateRegister({ ...valid, username: "a".repeat(length) }).username).toBe(expected);
  });

  it.each(["ada-01", "ada 01", "adá", "ada.01", "ada@01"])("geçersiz karakterli kullanıcı adı (%s) reddedilir", (username) => {
    expect(validateRegister({ ...valid, username }).username).toBe("pattern");
  });

  it("kullanıcı adı boşsa zorunludur; baştaki/sondaki boşluk kırpılır", () => {
    expect(validateRegister({ ...valid, username: "   " }).username).toBe("required");
    expect(validateRegister({ ...valid, username: "  ada_01  " }).username).toBeUndefined();
  });

  it("e-postayı denetler: zorunlu, biçim, en çok 254 karakter", () => {
    expect(validateRegister({ ...valid, email: "" }).email).toBe("required");
    expect(validateRegister({ ...valid, email: "ada@" }).email).toBe("invalid");
    expect(validateRegister({ ...valid, email: "ada example@x.com" }).email).toBe("invalid");
    const long = `${"a".repeat(registerLimits.emailMax - "@x.test".length)}@x.test`; // tam 254 karakter
    expect(validateRegister({ ...valid, email: long }).email).toBeUndefined();
    expect(validateRegister({ ...valid, email: `a${long}` }).email).toBe("tooLong");
  });

  it.each([
    [14, "tooShort"],
    [15, undefined],
    [128, undefined],
    [129, "tooLong"]
  ] as const)("parola %i karakter: %s", (length, expected) => {
    expect(validateRegister({ ...valid, password: "p".repeat(length) }).password).toBe(expected);
  });

  it("parola kırpılmaz: yalnız boşluktan oluşan 15 karakter geçerli uzunluktur; boş parola zorunludur", () => {
    expect(validateRegister({ ...valid, password: " ".repeat(15) }).password).toBeUndefined();
    expect(validateRegister({ ...valid, password: "" }).password).toBe("required");
  });

  it("koşulların kabulü zorunludur", () => {
    expect(validateRegister({ ...valid, acceptTerms: false }).acceptTerms).toBe("required");
  });

  it("ilk hatalı alanı formdaki sıraya göre verir", () => {
    expect(firstInvalidRegisterField({})).toBeNull();
    expect(firstInvalidRegisterField({ acceptTerms: "required" })).toBe("acceptTerms");
    expect(firstInvalidRegisterField({ acceptTerms: "required", password: "required", email: "required" })).toBe("email");
    expect(firstInvalidRegisterField(validateRegister({ username: "", email: "", password: "", acceptTerms: false }))).toBe("username");
  });
});
