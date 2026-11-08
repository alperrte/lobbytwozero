import { fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Category, FilterOptions } from "../types/hardware";
import { CatalogFilters, type FilterOptionsState } from "./catalog-filters";

const option = (value: string, label = value) => ({ value, label, count: 1 });
const ready = (options: FilterOptions): FilterOptionsState => ({ data: options, loading: false, reload: vi.fn() });
function show(category: Category | undefined, state: FilterOptionsState, query = "") {
  const onApply = vi.fn();
  render(<NextIntlClientProvider locale="tr" messages={tr}><CatalogFilters category={category} search={new URLSearchParams(query)} filterOptions={state} onApply={onApply} /></NextIntlClientProvider>);
  return onApply;
}
const select = (name: string) => screen.getByRole("combobox", { name });
/** Özel açılır listeyi açar ve seçenek etiketlerini döndürür. */
const optionLabels = (name: string) => {
  fireEvent.click(select(name));
  const labels = within(screen.getByRole("listbox")).getAllByRole("option").map((item) => item.textContent);
  fireEvent.keyDown(select(name), { key: "Escape" });
  return labels;
};
const pick = (name: string, label: string) => { fireEvent.click(select(name)); fireEvent.click(within(screen.getByRole("listbox")).getByRole("option", { name: label })); };
const hidden = (name: string) => (document.querySelector(`input[type=hidden][name=${name}]`) as HTMLInputElement).value;

describe("CatalogFilters", () => {
  it("CPU: çekirdek sayısı açılır liste, frekans ve TDP elle girilir; seçim en az = en fazla olarak gider", () => {
    const onApply = show("CPU", ready({ brandId: [option("brand-1", "AMD")], coreCount: [option("6"), option("8")] }));
    expect(screen.getAllByRole("spinbutton")).toHaveLength(4);
    expect(optionLabels("Çekirdek sayısı")).toEqual(["Tümü", "6", "8"]);
    pick("Çekirdek sayısı", "8");
    pick("Üretici adı", "AMD");
    fireEvent.change(document.querySelector("input[name=minClockMhz]")!, { target: { value: "3000" } });
    fireEvent.change(document.querySelector("input[name=maxPowerWatts]")!, { target: { value: "105" } });
    fireEvent.click(screen.getByRole("button", { name: "Filtreleri uygula" }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ minCoreCount: "8", maxCoreCount: "8", brandId: "brand-1", minClockMhz: "3000", maxPowerWatts: "105", page: "0" }));
    expect(onApply.mock.calls[0][0]).not.toHaveProperty("exactCoreCount");
  });

  it("CPU: tümleşik grafik onay kutusu işaretliyken true gider, işaretsizken URL'deki eski değer boşaltılır", () => {
    const onApply = show("CPU", ready({ brandId: [] }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Tümleşik grafik" }));
    fireEvent.click(screen.getByRole("button", { name: "Filtreleri uygula" }));
    expect(onApply).toHaveBeenLastCalledWith(expect.objectContaining({ integratedGraphics: "true", page: "0" }));
  });

  it("CPU: URL'de integratedGraphics=true ise kutu işaretli gelir ve işareti kaldırınca değer boş gider", () => {
    const onApply = show("CPU", ready({ brandId: [] }), "integratedGraphics=true");
    const box = screen.getByRole("checkbox", { name: "Tümleşik grafik" }) as HTMLInputElement;
    expect(box.checked).toBe(true);
    fireEvent.click(box);
    fireEvent.click(screen.getByRole("button", { name: "Filtreleri uygula" }));
    expect(onApply).toHaveBeenLastCalledWith(expect.objectContaining({ integratedGraphics: "" }));
  });

  it("onay kutusu yalnız işlemcide görünür", () => {
    show("GPU", ready({ brandId: [] }));
    expect(screen.queryByRole("checkbox")).toBeNull();
  });

  it("GPU: model ve video belleği; RAM: hız ve CAS değerleri birimle gösterilir", () => {
    show("GPU", ready({ brandId: [], chipset: [option("GeForce RTX 4070")], capacityGb: [option("12"), option("16")] }));
    expect(select("Model")).toBeDefined();
    expect(optionLabels("Video belleği")).toEqual(["Tümü", "12 GB", "16 GB"]);
  });

  it("RAM: bellek türü, kapasite, hız ve CAS açılır listeleri gelir", () => {
    show("RAM", ready({ brandId: [], memoryType: [option("DDR5")], capacityGb: [option("32")], clockMhz: [option("6000")], casLatency: [option("30")] }));
    expect(optionLabels("Hız")).toContain("6.000 MHz");
    expect(optionLabels("CAS gecikmesi")).toContain("CL30");
    expect(select("Toplam kapasite")).toBeDefined();
    expect(select("Bellek türü")).toBeDefined();
  });

  it("anakart ve depolama alanlarını sunar, tek değerli parametreyi aynı adla gönderir", () => {
    const onApply = show("MOTHERBOARD", ready({ brandId: [], socket: [option("AM5")], formFactor: [option("ATX")], maximumMemoryGb: [option("192")], memorySlots: [option("4")] }), "maximumMemoryGb=192");
    expect(select("Azami bellek").textContent).toBe("192 GB");
    expect(hidden("maximumMemoryGb")).toBe("192");
    for (const label of ["İşlemci soketi", "Form faktörü", "Bellek yuvası"]) expect(select(label)).toBeDefined();
    pick("Bellek yuvası", "4");
    fireEvent.click(screen.getByRole("button", { name: "Filtreleri uygula" }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ maximumMemoryGb: "192", memorySlots: "4" }));
  });

  it("depolama: tür, kapasite ve arayüz; kategorisiz yalnız üretici listesi görünür", () => {
    show("STORAGE", ready({ brandId: [], storageType: [option("SSD")], capacityGb: [option("1000")], interfaceType: [option("M.2 PCIe 4.0 X4")] }));
    expect(select("Depolama türü")).toBeDefined();
    expect(optionLabels("Kapasite")).toContain("1 TB");
    expect(select("Arayüz")).toBeDefined();
  });

  it("kategori yokken yalnız üretici; yüklenirken liste kilitli; hatada yeniden dene sunulur", () => {
    const reload = vi.fn();
    show(undefined, { loading: false, error: new Error("x"), reload });
    expect(screen.queryAllByRole("combobox")).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Yeniden yükle" }));
    expect(reload).toHaveBeenCalled();
  });

  it("seçenekler yüklenirken açılır listeler devre dışıdır", () => {
    show("GPU", { loading: true, reload: vi.fn() });
    for (const box of screen.getAllByRole("combobox")) expect((box as HTMLButtonElement).disabled).toBe(true);
  });

  it("en az en fazladan büyükse göndermez", () => {
    const onApply = show("CPU", ready({ brandId: [], coreCount: [] }));
    fireEvent.change(document.querySelector("input[name=minPowerWatts]")!, { target: { value: "200" } });
    fireEvent.change(document.querySelector("input[name=maxPowerWatts]")!, { target: { value: "100" } });
    fireEvent.click(screen.getByRole("button", { name: "Filtreleri uygula" }));
    expect(onApply).not.toHaveBeenCalled();
  });

  it("Tümünü temizle filtre parametrelerini boşaltır", () => {
    const onApply = show("RAM", ready({ brandId: [] }), "casLatency=30&brandId=x");
    fireEvent.click(screen.getByRole("button", { name: "Tümünü temizle" }));
    expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ casLatency: "", brandId: "", brand: "", minCapacityGb: "", page: "0" }));
  });
});
