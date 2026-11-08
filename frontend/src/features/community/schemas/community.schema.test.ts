import { describe, expect, it } from "vitest";
import { contentTypeNeeds, LIMITS, validateImage, validateSlug, validateText, validateTitle } from "./community.schema";

const file = (type: string, size: number) => new File([new Uint8Array(Math.max(size, 0))], "a", { type });

describe("Community doğrulama kuralları (API sınırlarıyla aynı)", () => {
  it("gönderi gövdesi: boşluk dışında en az 1, en çok 5000, satır sonu serbest", () => {
    expect(validateText("   ", LIMITS.postBody)).toBe("required");
    expect(validateText("a".repeat(5000), LIMITS.postBody)).toBeNull();
    expect(validateText("a".repeat(5001), LIMITS.postBody)).toBe("tooLong");
    expect(validateText("satır\nsatır", LIMITS.postBody)).toBeNull();
    expect(validateText("a\u0000b", LIMITS.postBody)).toBe("invalidChars");
  });

  it("isteğe bağlı alan boşken geçerlidir", () => {
    expect(validateText("", LIMITS.repostNote, { required: false })).toBeNull();
    expect(validateText("a".repeat(501), LIMITS.repostNote, { required: false })).toBe("tooLong");
  });

  it("başlık 1-120 karakter ve satır sonu içeremez", () => {
    expect(validateTitle("")).toBe("required");
    expect(validateTitle("Rehber")).toBeNull();
    expect(validateTitle("a\nb")).toBe("invalidChars");
    expect(validateTitle("a".repeat(121))).toBe("tooLong");
  });

  it("slug yalnız küçük harf, rakam ve tekil tire; 3-80 karakter", () => {
    expect(validateSlug("ab")).toBe("tooShort");
    expect(validateSlug("elden-ring")).toBeNull();
    expect(validateSlug("Elden-Ring")).toBe("invalidSlug");
    expect(validateSlug("elden--ring")).toBe("invalidSlug");
    expect(validateSlug("-elden")).toBe("invalidSlug");
    expect(validateSlug("a".repeat(81))).toBe("tooLong");
  });

  it("görsel: yalnız JPEG/PNG, en çok 2 MiB, gönderi başına en çok 4", () => {
    expect(validateImage(file("image/png", 10), 0)).toBeNull();
    expect(validateImage(file("image/jpeg", 10), 3)).toBeNull();
    expect(validateImage(file("image/webp", 10), 0)).toBe("type");
    expect(validateImage(file("image/gif", 10), 0)).toBe("type");
    expect(validateImage(file("image/png", LIMITS.imageBytes + 1), 0)).toBe("size");
    expect(validateImage(file("image/png", LIMITS.imageBytes), 0)).toBeNull();
    expect(validateImage(file("image/png", 10), 4)).toBe("count");
    expect(validateImage(file("image/png", 0), 0)).toBe("empty");
  });

  it("türlü içerik başlık ve oyun ister", () => {
    expect(contentTypeNeeds("")).toEqual({ title: false, game: false });
    expect(contentTypeNeeds("GUIDES")).toEqual({ title: true, game: true });
  });
});
