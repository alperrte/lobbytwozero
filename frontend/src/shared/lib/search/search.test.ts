import { describe, expect, it } from "vitest";
import {
  collectEntries,
  normalizeQuery,
  searchEntries,
  type SearchEntry,
  type SearchProvider
} from "./search";

const entries: SearchEntry[] = [
  { id: "home", group: "pages", title: "Ana Sayfa", href: "/", keywords: ["başlangıç"] },
  { id: "faq", group: "pages", title: "SSS", description: "Sıkça sorulan sorular", href: "/faq" },
  { id: "about", group: "pages", title: "Hakkımızda", href: "/about" }
];

describe("normalizeQuery", () => {
  it("Türkçe harfleri ve büyük/küçük farkını eşitler", () => {
    expect(normalizeQuery("  HAKKIMIZDA ")).toBe("hakkimizda");
    expect(normalizeQuery("Şıkça")).toBe("sikca");
  });
});

describe("searchEntries", () => {
  it("boş sorguda tüm kayıtları sırayla döndürür", () => {
    expect(searchEntries(entries, "  ")).toEqual(entries);
  });

  it("başlık, açıklama ve anahtar kelimeyle eşleşir; eşleşmeyeni dışlar", () => {
    expect(searchEntries(entries, "hakkimizda").map((e) => e.id)).toEqual(["about"]);
    expect(searchEntries(entries, "sikca").map((e) => e.id)).toEqual(["faq"]);
    expect(searchEntries(entries, "baslangic").map((e) => e.id)).toEqual(["home"]);
    expect(searchEntries(entries, "olmayan")).toEqual([]);
  });

  it("başlık başlangıcı, başlıkta geçen eşleşmeden önce gelir", () => {
    const list: SearchEntry[] = [
      { id: "b", group: "pages", title: "Kenar Sayfa", href: "/b" },
      { id: "a", group: "pages", title: "Sayfa Listesi", href: "/a" }
    ];
    expect(searchEntries(list, "sayfa").map((e) => e.id)).toEqual(["a", "b"]);
  });
});

describe("collectEntries", () => {
  it("tüm sağlayıcıların kayıtlarını birleştirir ve çeviri fonksiyonunu geçirir", () => {
    const providers: SearchProvider[] = [
      { id: "one", getEntries: (t) => [{ id: "1", group: "pages", title: t("x"), href: "/1" }] },
      { id: "two", getEntries: () => [entries[0]] }
    ];
    const result = collectEntries(providers, (key) => key.toUpperCase());
    expect(result.map((e) => e.title)).toEqual(["X", "Ana Sayfa"]);
  });
});
