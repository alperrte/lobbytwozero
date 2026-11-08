import { describe, expect, it } from "vitest";
import { formatInviteCode, normalizeInviteCode, validateInviteCode } from "./invite-code";

describe("davet kodu", () => {
  it("boşluk ve tireleri atar, harfleri büyütür", () => {
    expect(normalizeInviteCode(" 7kx4m-9qbtr ")).toBe("7KX4M9QBTR");
    expect(normalizeInviteCode("7KX4 M9Q BTR")).toBe("7KX4M9QBTR");
  });

  it("geçerli kodu kabul eder (gruplanmış yazım dahil)", () => {
    expect(validateInviteCode("7KX4M9QBTR")).toBeNull();
    expect(validateInviteCode("7kx4m-9qbtr")).toBeNull();
  });

  it("boş, kısa/uzun ve yasak karakterli kodu ayırır", () => {
    expect(validateInviteCode("   ")).toBe("empty");
    expect(validateInviteCode("ABC")).toBe("length");
    expect(validateInviteCode("7KX4M9QBTR1")).toBe("length");
    // I, L, O, U sunucu alfabesinde yoktur.
    expect(validateInviteCode("DENZ1PLYS2")).toBe("characters");
    expect(validateInviteCode("ABCDE$1234")).toBe("characters");
    expect(validateInviteCode("ABC$")).toBe("characters");
  });

  it("10 karakterlik kodu 5-5 gruplar, diğerlerine dokunmaz", () => {
    expect(formatInviteCode("7KX4M9QBTR")).toBe("7KX4M-9QBTR");
    expect(formatInviteCode("ABC")).toBe("ABC");
  });
});
