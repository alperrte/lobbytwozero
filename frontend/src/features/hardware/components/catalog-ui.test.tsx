import { fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import type { Category, Product } from "../types/hardware";
import { ComparisonView } from "./comparison-view";
import { ProductSpecificationTable } from "./specification-table";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ children }: { children: React.ReactNode; href: unknown }) => <a href="#">{children}</a>,
  usePathname: () => "/hardware"
}));

const empty = { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null };
const typed = (map: Record<string, { number?: number | null; text?: string | null; unit?: string | null }>) =>
  ({ provider: "PC_PART_DATASET", typedSpecifications: Object.fromEntries(Object.entries(map).map(([key, value]) => [key, { number: null, text: null, unit: null, ...value }])) });
const make = (id: string, category: Category, model: string, map: Parameters<typeof typed>[0]): Product =>
  ({ id, category, model, brands: [{ id: "b", name: "Marka", status: "ACTIVE", version: 1 }], specifications: empty, status: "ACTIVE", version: 1, source: typed(map) });
const wrap = (node: React.ReactNode, locale: "tr" | "en" = "tr") => render(<NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>{node}</NextIntlClientProvider>);

describe("ComparisonView", () => {
  const left = make("a", "STORAGE", "Alfa", { storageType: { text: "SSD" }, capacityGb: { number: 1000, unit: "GB" }, interfaceType: { text: "M.2 PCIe 4.0 X4" }, cacheMb: { number: 1024, unit: "MB" } });
  const right = make("b", "STORAGE", "Beta", { storageType: { text: "SSD" }, capacityGb: { number: 2000, unit: "GB" }, interfaceType: { text: "M.2 PCIe 4.0 X4" } });

  it("aynı, farklı ve kayıtsız alanları işaretler; eksik değer tire olur, kazanan ilan edilmez", () => {
    wrap(<ComparisonView left={left} right={right} />);
    const rows = screen.getAllByRole("row");
    const byState = (state: string) => rows.filter((row) => row.getAttribute("data-state") === state);
    expect(byState("similar")).toHaveLength(2);
    expect(byState("different")).toHaveLength(1);
    expect(byState("missing")).toHaveLength(1);
    expect(within(byState("different")[0]).getByText("1 TB")).toBeInTheDocument();
    expect(within(byState("different")[0]).getByText("2 TB")).toBeInTheDocument();
    expect(within(byState("missing")[0]).getByText("—")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("1 farklı, 2 aynı, 1 kayıtsız alan");
    expect(screen.queryByText(/kazanan|daha iyi|winner|better/i, { selector: "td, th:not([scope=col])" })).not.toBeInTheDocument();
  });
  it("yalnız farklıları göster düğmesi benzer ve kayıtsız satırları gizler", () => {
    wrap(<ComparisonView left={left} right={right} />);
    fireEvent.click(screen.getByRole("button", { name: "Yalnız farklıları göster" }));
    const rows = screen.getAllByRole("row").filter((row) => row.hasAttribute("data-state"));
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveAttribute("data-state", "different");
  });
  it("İngilizce arayüzde etiketler ve sayı biçimi yerelleşir", () => {
    wrap(<ComparisonView left={left} right={right} />, "en");
    expect(screen.getByRole("button", { name: "Show differences only" })).toBeInTheDocument();
    expect(screen.getByText("Capacity")).toBeInTheDocument();
  });
});

describe("ProductSpecificationTable", () => {
  it("kategoriye özel satırları gösterir, kayıtsız alanda tire ve erişilebilir metin verir", () => {
    wrap(<ProductSpecificationTable product={make("g", "GPU", "PULSE", { chipset: { text: "Radeon RX 7900 XTX" }, capacityGb: { number: 24, unit: "GB" }, boostClockMhz: { number: 2500, unit: "MHz" } })} />);
    expect(screen.getByText("Grafik işlemci")).toBeInTheDocument();
    expect(screen.getByText("Radeon RX 7900 XTX")).toBeInTheDocument();
    expect(screen.getByText("24 GB")).toBeInTheDocument();
    expect(screen.getByText("Kart uzunluğu")).toBeInTheDocument();
    expect(screen.getAllByText("Kayıtlı değil").length).toBeGreaterThan(0);
    expect(screen.queryByText(/fiyat|stok|sepet/i)).not.toBeInTheDocument();
  });
});
