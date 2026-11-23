import { describe, expect, it } from "vitest";
import { MAX_BODY_LENGTH, newClientMessageId, validateBody, validateGroupTitle } from "./message-body";

describe("validateBody (sunucu kuralının aynası)", () => {
  it("emoji, ZWJ dizisi, bayrak ve satır sonu serbesttir", () => {
    expect(validateBody("selam 👋\n👨‍👩‍👧 🇹🇷")).toBeNull();
  });

  it("boşluktan ibaret metni reddeder; açıklamada boş metin serbesttir", () => {
    expect(validateBody("   \n")).toBe("empty");
    expect(validateBody("")).toBe("empty");
    expect(validateBody("", { optional: true })).toBeNull();
    expect(validateBody("  ", { optional: true })).toBe("empty");
  });

  it("uzunluğu UTF-16 birimiyle sayar (çoğu emoji 2 birim)", () => {
    expect(validateBody("a".repeat(MAX_BODY_LENGTH))).toBeNull();
    expect(validateBody("a".repeat(MAX_BODY_LENGTH + 1))).toBe("tooLong");
    expect(validateBody("😀".repeat(MAX_BODY_LENGTH / 2 + 1))).toBe("tooLong");
  });

  it("kontrol karakterini ve eşlenmemiş vekili reddeder; sekme serbesttir", () => {
    expect(validateBody("a\u0007b")).toBe("invalidCharacters");
    expect(validateBody("bozuk \uD83D emoji")).toBe("invalidCharacters");
    expect(validateBody("bozuk \uDE00")).toBe("invalidCharacters");
    expect(validateBody("a\tb")).toBeNull();
  });
});

describe("newClientMessageId", () => {
  it("sunucu biçimine uyar ve her çağrıda farklıdır", () => {
    const first = newClientMessageId();
    const second = newClientMessageId();
    expect(first).toMatch(/^[A-Za-z0-9_-]{1,64}$/);
    expect(first).not.toBe(second);
  });
});

describe("validateGroupTitle", () => {
  it("baş/son boşluk kırpılır; boş, uzun ve satır sonu reddedilir", () => {
    expect(validateGroupTitle("  Takım  ")).toBeNull();
    expect(validateGroupTitle("   ")).toBe("empty");
    expect(validateGroupTitle("x".repeat(101))).toBe("tooLong");
    expect(validateGroupTitle("a\nb")).toBe("invalidCharacters");
  });
});
