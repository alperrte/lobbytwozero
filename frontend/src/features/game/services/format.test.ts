import { describe, expect, it } from "vitest";
import { formatMegabytes, termLabel } from "./format";

describe("Sistem gereksinimi birimleri", () => {
  it("MB değerini 1024 ve üstünde GB olarak gösterir", () => {
    expect(formatMegabytes(512, "en")).toBe("512 MB");
    expect(formatMegabytes(8192, "en")).toBe("8 GB");
    expect(formatMegabytes(1536, "en")).toBe("1.5 GB");
    expect(formatMegabytes(8192, "tr")).toBe("8 GB");
  });
  it("eksik veya geçersiz değer için null döndürür; sıfıra çevirmez", () => {
    expect(formatMegabytes(null, "tr")).toBeNull();
    expect(formatMegabytes(undefined, "tr")).toBeNull();
    expect(formatMegabytes(-1, "tr")).toBeNull();
    expect(formatMegabytes(Number.NaN, "tr")).toBeNull();
  });
});

describe("Terim adı", () => {
  it("seçili dili, sonra diğer dili, sonra kodu kullanır (gerçek API terimlerinde adlar null olabilir)", () => {
    expect(termLabel({ code: "rpg", nameTr: "Rol yapma", nameEn: "RPG" }, "tr")).toBe("Rol yapma");
    expect(termLabel({ code: "rpg", nameTr: null, nameEn: "RPG" }, "tr")).toBe("RPG");
    expect(termLabel({ code: "rpg", nameTr: null, nameEn: null }, "en")).toBe("rpg");
    expect(termLabel({ code: "rpg", nameTr: "  ", nameEn: "" }, "tr")).toBe("rpg");
  });
  it("hiçbir ad/kod yoksa boş metin döndürür", () => {
    expect(termLabel({ nameTr: null, nameEn: null }, "tr")).toBe("");
  });
});
