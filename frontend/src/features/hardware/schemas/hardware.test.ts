import { describe, expect, it } from "vitest";
import { isCategory, isUuid, readCatalogQuery, safeExternalUrl } from "./hardware";
describe("Hardware girdi sınırları", () => {
  it.each(["javascript:alert(1)", "http://store.test", "https://user:pass@store.test", "//store.test", "invalid"])("güvenilmeyen dış URL'yi link yapmaz: %s", (url) => expect(safeExternalUrl(url)).toBeUndefined());
  it("https kaynağını ve gerçek kategori/UUID biçimini kabul eder", () => {
    expect(safeExternalUrl("https://store.test/p?a=1")).toBe("https://store.test/p?a=1");
    expect(isUuid("00000000-0000-0000-0000-000000000001")).toBe(true);
    expect(isUuid("../profiles")).toBe(false); expect(isCategory("CPU")).toBe(true); expect(isCategory("fake")).toBe(false);
  });
  it("katalogda yalnız backend filtrelerini taşır ve sayfalama sınırını korur", () => {
    const query = readCatalogQuery(new URLSearchParams("q=CPU&price=10&page=-2&stock=true&socket=AM5"));
    expect(query).toContain("q=CPU"); expect(query).toContain("socket=AM5"); expect(query).toContain("page=0"); expect(query).not.toContain("price"); expect(query).not.toContain("stock");
  });
});
