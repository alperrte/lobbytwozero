import { describe, expect, it } from "vitest";
import { catalogCategories, type Product } from "../types/hardware";
import { cardSpecKeys, categoryFilters, filterOptionLabel, formatSpec, specName, productName, productSubtitle, readSpec, specProfiles, specSignature, technicalQueryKeys } from "./spec-profile";

const base = { specifications: { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null }, brands: [], status: "ACTIVE", version: 1 } as const;
const product = (over: Partial<Product>): Product => ({ id: "p", category: "CPU", model: "Model", ...base, ...over } as Product);
const typed = (map: Record<string, { number?: number | null; text?: string | null; unit?: string | null }>) =>
  ({ provider: "PC_PART_DATASET", typedSpecifications: Object.fromEntries(Object.entries(map).map(([key, value]) => [key, { number: null, text: null, unit: null, ...value }])) });
const labels = { yes: "Evet", no: "Hayır" };

describe("katalog kategorileri ve profilleri", () => {
  it("tam olarak beş kategori ve doğru sırayla", () => {
    expect([...catalogCategories]).toEqual(["CPU", "GPU", "MOTHERBOARD", "RAM", "STORAGE"]);
    for (const category of catalogCategories) {
      expect(specProfiles[category]?.card.length).toBeGreaterThan(0);
      expect(categoryFilters[category]?.length).toBeGreaterThan(0);
    }
  });
  it("teknik filtre anahtarları yalnız API'nin bildiği parametrelerdir", () => {
    const names = Object.values(categoryFilters).flat().map((field) => field!.kind === "select" || field!.kind === "toggle" ? [field!.key] : [`min${field!.key}`, `max${field!.key}`]).flat();
    for (const name of names) expect(technicalQueryKeys as readonly string[]).toContain(name);
  });
});

describe("katalog filtre alanları", () => {
  const kinds = (category: (typeof catalogCategories)[number]) => categoryFilters[category]!.map((field) => `${field.kind}:${specName(field)}`);
  it("istenen alanlar kategori başına doğru türdedir", () => {
    expect(kinds("CPU")).toEqual(["exact:coreCount", "range:clockMhz", "range:powerWatts", "toggle:integratedGraphics"]);
    expect(kinds("GPU")).toEqual(["select:chipset", "exact:capacityGb"]);
    expect(kinds("MOTHERBOARD")).toEqual(["select:socket", "select:formFactor", "select:maximumMemoryGb", "select:memorySlots"]);
    expect(kinds("RAM")).toEqual(["select:memoryType", "exact:capacityGb", "exact:clockMhz", "select:casLatency"]);
    expect(kinds("STORAGE")).toEqual(["select:storageType", "exact:capacityGb", "select:interfaceType"]);
  });
  it("seçenek yazısına birim eklenir, değer değişmez", () => {
    const option = (value: string) => ({ value, label: value });
    expect(filterOptionLabel("capacityGb", option("16"), "en")).toBe("16 GB");
    expect(filterOptionLabel("capacityGb", option("2000"), "en")).toBe("2 TB");
    expect(filterOptionLabel("maximumMemoryGb", option("192"), "tr")).toBe("192 GB");
    expect(filterOptionLabel("clockMhz", option("6000"), "tr")).toBe("6.000 MHz");
    expect(filterOptionLabel("casLatency", option("30"), "tr")).toBe("CL30");
    expect(filterOptionLabel("memorySlots", option("4"), "tr")).toBe("4");
    expect(filterOptionLabel("chipset", { value: "GeForce RTX 4070", label: "GeForce RTX 4070" }, "tr")).toBe("GeForce RTX 4070");
    expect(filterOptionLabel("socket", option("1700"), "tr")).toBe("1700");
  });
});

describe("readSpec / formatSpec", () => {
  it("typedSpecifications değerini önce okur; yoksa temel alana düşer; ikisi de yoksa undefined", () => {
    const item = product({ specifications: { ...base.specifications, clockMhz: 3000 }, source: typed({ boostClockMhz: { number: 4600, unit: "MHz" }, clockMhz: { number: 3700, unit: "MHz" } }) });
    expect(readSpec(item, "clockMhz")).toMatchObject({ number: 3700, unit: "MHz" });
    expect(readSpec(product({ specifications: { ...base.specifications, clockMhz: 2400 } }), "clockMhz")).toMatchObject({ number: 2400, unit: "MHz" });
    expect(readSpec(item, "coreCount")).toBeUndefined();
    expect(readSpec(product({ source: typed({ coreCount: { number: null, text: "  " } }) }), "coreCount")).toBeUndefined();
  });
  it("sayıyı yerel biçimle yazar, 1000 GB üstünü TB yapar, true/false etiketler ve eksikte undefined döner", () => {
    expect(formatSpec(undefined, "tr", labels)).toBeUndefined();
    expect(formatSpec({ number: 3700, text: null, unit: "MHz" }, "en", labels)).toBe("3,700 MHz");
    expect(formatSpec({ number: 3700, text: null, unit: "MHz" }, "tr", labels)).toBe("3.700 MHz");
    expect(formatSpec({ number: 2000, text: null, unit: "GB" }, "en", labels)).toBe("2 TB");
    expect(formatSpec({ number: 512, text: null, unit: "GB" }, "en", labels)).toBe("512 GB");
    expect(formatSpec({ number: null, text: "true", unit: "" }, "tr", labels)).toBe("Evet");
    expect(formatSpec({ number: null, text: "false", unit: "" }, "tr", labels)).toBe("Hayır");
    expect(formatSpec({ number: null, text: "AM5", unit: "" }, "tr", labels)).toBe("AM5");
  });
  it("benzerlik imzası biçimden bağımsızdır ve eksik değeri boş sayar", () => {
    expect(specSignature({ number: 8, text: null, unit: "GB" })).toBe(specSignature({ number: 8, text: null, unit: "GB" }));
    expect(specSignature({ number: 8, text: null, unit: "GB" })).not.toBe(specSignature({ number: 16, text: null, unit: "GB" }));
    expect(specSignature({ number: null, text: "DDR5", unit: "" })).toBe(specSignature({ number: null, text: "ddr5", unit: "" }));
    expect(specSignature(undefined)).toBe("");
  });
});

describe("kart ve başlık yardımcıları", () => {
  it("kartta yalnız kayıtlı değerler gelir ve en çok dört tanedir", () => {
    const gpu = product({ category: "GPU", source: typed({ capacityGb: { number: 8, unit: "GB" }, boostClockMhz: { number: 2500, unit: "MHz" }, color: { text: "Black" } }) });
    expect(cardSpecKeys(gpu)).toEqual(["capacityGb", "boostClockMhz", "color"]);
    const full = product({ category: "RAM", source: typed({ memoryType: { text: "DDR5" }, capacityGb: { number: 32, unit: "GB" }, clockMhz: { number: 6000, unit: "MHz" }, casLatency: { number: 30 }, color: { text: "Black" } }) });
    expect(cardSpecKeys(full)).toHaveLength(4);
    expect(cardSpecKeys(product({}))).toEqual([]);
  });
  it("ürün adı üreticiyi yalnız model içinde geçmiyorsa ekler", () => {
    const brand = { id: "b", name: "AMD", status: "ACTIVE", version: 1 } as const;
    expect(productName(product({ model: "Ryzen 5 5600X", brands: [brand] }))).toBe("AMD Ryzen 5 5600X");
    expect(productName(product({ model: "AMD Ryzen 5 5600X", brands: [brand] }))).toBe("AMD Ryzen 5 5600X");
    expect(productName(product({ model: "Ryzen 5 5600X" }))).toBe("Ryzen 5 5600X");
  });
  it("alt başlık: GPU çipi, CPU mimarisi, RAM modül dizilimi; diğerlerinde yok", () => {
    expect(productSubtitle(product({ category: "GPU", model: "PULSE", source: typed({ chipset: { text: "Radeon RX 7900 XTX" } }) }))).toBe("Radeon RX 7900 XTX");
    expect(productSubtitle(product({ category: "CPU", source: typed({ microarchitecture: { text: "Zen 3" } }) }))).toBe("Zen 3");
    expect(productSubtitle(product({ category: "RAM", source: typed({ moduleCount: { number: 2 }, moduleCapacityGb: { number: 16, unit: "GB" } }) }))).toBe("2 × 16 GB");
    expect(productSubtitle(product({ category: "STORAGE" }))).toBeUndefined();
    expect(productSubtitle(product({ category: "GPU" }))).toBeUndefined();
  });
});
