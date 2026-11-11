import { describe, expect, it } from "vitest";
import { isLibraryId, libraryQuery, libraryQueryString } from "./library-query";

describe("Library URL sözleşmesi", () => {
  it("false ve AND filtrelerini sıralama ve sayfalamayla korur", () => {
    const parsed = libraryQuery(new URLSearchParams("favorite=false&completed=true&sort=TITLE&direction=ASC&page=2&size=50&q=ignored&userId=ignored"));
    expect(parsed).toEqual({ favorite: false, completed: true, sort: "TITLE", direction: "ASC", page: 2, size: 50 });
    expect(libraryQueryString(parsed)).toBe("sort=TITLE&direction=ASC&page=2&size=50&favorite=false&completed=true");
  });
  it.each(["page=-1&size=51", "page=2147483648&size=0", "page=NaN&size=abc", "page=1.2&size=-5"])("geçersiz sınırları güvenli varsayılana alır: %s", (value) => {
    expect(libraryQuery(new URLSearchParams(value))).toMatchObject({ page: 0, size: 20 });
  });
  it("favori/bitirildi sayfalarının temel filtresi query ile kapatılamaz", () => {
    expect(libraryQuery(new URLSearchParams("favorite=false&completed=false"), "favorites")).toMatchObject({ favorite: true, completed: false });
    expect(libraryQuery(new URLSearchParams("favorite=false&completed=false"), "completed")).toMatchObject({ favorite: false, completed: true });
  });
  it("katalog sağlayıcı kimliğini kanonik UUID kabul etmez", () => {
    expect(isLibraryId("730")).toBe(false);
    expect(isLibraryId("../../auth")).toBe(false);
    expect(isLibraryId("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66")).toBe(true);
  });
});
