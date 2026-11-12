import { describe, expect, it } from "vitest";
import {
  REVIEW_BODY_MAX,
  batchIds,
  featuredQueryFrom,
  featuredSearchParams,
  hubQueryFrom,
  isReviewUuid,
  isValidRating,
  reviewBodyIssue,
  reviewBodyPayload,
  isReportReason,
  reportDetailIssue
} from "./review-schema";

const id = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";

describe("kimlik doğrulama", () => {
  it("yalnız kanonik UUID kabul eder; Steam AppID ve metin kabul edilmez", () => {
    expect(isReviewUuid(id)).toBe(true);
    expect(isReviewUuid(id.toUpperCase())).toBe(true);
    for (const value of ["730", "", "not-a-uuid", `${id}x`, `../${id}`, `${id}/../x`, null, undefined, 7]) {
      expect(isReviewUuid(value)).toBe(false);
    }
  });
});

describe("puan sınırları", () => {
  it("yalnız 1–10 tam sayıları geçerli sayar", () => {
    for (const value of [1, 5, 10]) expect(isValidRating(value)).toBe(true);
    for (const value of [0, 11, -1, 7.5, Number.NaN, Infinity, "7", null, undefined]) expect(isValidRating(value)).toBe(false);
  });
});

describe("inceleme metni", () => {
  it("boş ve yalnız boşluktan oluşan metni reddeder", () => {
    expect(reviewBodyIssue("")).toBe("required");
    expect(reviewBodyIssue("   \n\t  ")).toBe("required");
  });

  it("sınırı ham UTF-16 uzunluğuyla uygular (backend boşluk temizlemeden önce sayar)", () => {
    expect(reviewBodyIssue("a".repeat(REVIEW_BODY_MAX))).toBeNull();
    expect(reviewBodyIssue("a".repeat(REVIEW_BODY_MAX + 1))).toBe("tooLong");
    // Boşluklarla 5001 olan metin temizlenince 5000'e inse bile backend 400 verir; istemci de reddeder.
    expect(reviewBodyIssue(`  ${"a".repeat(REVIEW_BODY_MAX - 1)}  `)).toBe("tooLong");
    // Emoji iki UTF-16 birimidir.
    expect(reviewBodyIssue("😀".repeat(2500))).toBeNull();
    expect(reviewBodyIssue("😀".repeat(2501))).toBe("tooLong");
  });

  it("satır sonu ve sekmeye izin verir, diğer denetim karakterlerini reddeder", () => {
    expect(reviewBodyIssue("satır 1\nsatır 2\r\n\tgirinti")).toBeNull();
    expect(reviewBodyIssue("a\u0000b")).toBe("invalidCharacters");
    expect(reviewBodyIssue("a\u001bb")).toBe("invalidCharacters");
    expect(reviewBodyIssue("a\u007fb")).toBe("invalidCharacters");
  });

  it("gönderilen metnin baş ve sonundaki boşlukları temizler; HTML'e dokunmaz", () => {
    expect(reviewBodyPayload("  merhaba \n")).toBe("merhaba");
    expect(reviewBodyPayload("<script>alert(1)</script>")).toBe("<script>alert(1)</script>");
  });
});

describe("rapor", () => {
  it("yalnız kapalı listedeki gerekçeleri kabul eder", () => {
    for (const reason of ["SPAM", "HARASSMENT", "HATE_SPEECH", "PERSONAL_INFO", "SPOILER", "OFF_TOPIC", "OTHER"]) expect(isReportReason(reason)).toBe(true);
    for (const reason of ["spam", "", "UNKNOWN", 1, null, undefined]) expect(isReportReason(reason)).toBe(false);
  });

  it("açıklamayı 500 karakter ve denetim karakteriyle sınırlar", () => {
    expect(reportDetailIssue("")).toBeNull();
    expect(reportDetailIssue("x".repeat(500))).toBeNull();
    expect(reportDetailIssue("x".repeat(501))).toBe("tooLong");
    expect(reportDetailIssue("satır\nsekme\t")).toBeNull();
    expect(reportDetailIssue("a\u0000b")).toBe("invalidCharacters");
  });
});

describe("öne çıkanlar sorgusu", () => {
  it("varsayılanları uygular", () => {
    expect(featuredQueryFrom(new URLSearchParams())).toEqual({ page: 0, size: 20, minLikes: 0 });
  });

  it("geçerli değerleri korur", () => {
    expect(featuredQueryFrom(new URLSearchParams("page=3&size=50&minLikes=5"))).toEqual({ page: 3, size: 50, minLikes: 5 });
  });

  it("geçersiz ve aralık dışı değerleri güvenli varsayılana çeker", () => {
    expect(featuredQueryFrom(new URLSearchParams("page=-1&size=abc&minLikes=-4"))).toEqual({ page: 0, size: 20, minLikes: 0 });
    expect(featuredQueryFrom(new URLSearchParams("page=99999&size=7&minLikes=1.5"))).toEqual({ page: 10000, size: 20, minLikes: 0 });
    expect(featuredQueryFrom(new URLSearchParams("page=1;drop&minLikes=99999999999999"))).toEqual({ page: 0, size: 20, minLikes: 0 });
  });

  it("URL'ye yalnız varsayılan olmayan değerleri yazar", () => {
    expect(featuredSearchParams({ page: 0, size: 20, minLikes: 0 }).toString()).toBe("");
    expect(featuredSearchParams({ page: 2, size: 10, minLikes: 3 }).toString()).toBe("page=2&size=10&minLikes=3");
  });
});

describe("merkez sorgusu", () => {
  it("arama metnini normalleştirir ve 128 karakterle sınırlar", () => {
    expect(hubQueryFrom(new URLSearchParams("q=%20%20half%20%20life%20&page=2"))).toEqual({ q: "half life", page: 2 });
    expect(hubQueryFrom(new URLSearchParams(`q=${"x".repeat(300)}`)).q).toHaveLength(128);
    expect(hubQueryFrom(new URLSearchParams("page=zzz"))).toEqual({ q: "", page: 0 });
  });
});

describe("toplu kimlikler", () => {
  it("tekilleştirir, geçersizleri atar ve 50 ile sınırlar", () => {
    const many = Array.from({ length: 80 }, (_, index) => `0198a3c2-bbbb-7d44-9a55-${String(index).padStart(12, "0")}`);
    expect(batchIds([id, id, "x", ...many])).toHaveLength(50);
    expect(batchIds([id, id])).toEqual([id]);
    expect(batchIds([])).toEqual([]);
  });
});
