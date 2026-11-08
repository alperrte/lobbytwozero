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
  it("yeni açılır liste filtrelerini (model, depolama türü, azami bellek, yuva, CAS) ve üreticiyi taşır", () => {
    const query = readCatalogQuery(new URLSearchParams("chipset=GeForce+RTX+4070&storageType=SSD&maximumMemoryGb=192&memorySlots=4&casLatency=30&brandId=b1&minClockMhz=6000&maxClockMhz=6000&integratedGraphics=true"));
    for (const part of ["chipset=GeForce+RTX+4070", "storageType=SSD", "maximumMemoryGb=192", "memorySlots=4", "casLatency=30", "brandId=b1", "minClockMhz=6000", "maxClockMhz=6000", "integratedGraphics=true"]) expect(query).toContain(part);
    expect(readCatalogQuery(new URLSearchParams("integratedGraphics=maybe"))).not.toContain("integratedGraphics");
  });
  it("çoklu seçimi tekrarlanan parametre olarak korur, yinelenenleri ve boşları atar", () => {
    const query = new URLSearchParams(readCatalogQuery(new URLSearchParams("socket=AM5&socket=AM4&socket=AM5&socket=&gpuVendor=NVIDIA&gpuVendor=AMD&coreCount=6&coreCount=8")));
    expect(query.getAll("socket")).toEqual(["AM5", "AM4"]);
    expect(query.getAll("gpuVendor")).toEqual(["NVIDIA", "AMD"]);
    expect(query.getAll("coreCount")).toEqual(["6", "8"]);
  });
  it("tek parametre gibi okunan alanlar yalnız ilk değeri taşır", () => {
    expect(new URLSearchParams(readCatalogQuery(new URLSearchParams("q=a&q=b"))).getAll("q")).toEqual(["a"]);
  });
});
