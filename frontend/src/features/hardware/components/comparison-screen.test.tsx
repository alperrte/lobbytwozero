import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Category, Product } from "../types/hardware";
import { ComparisonScreen, parseComparisonIds } from "./comparison-screen";

const uid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const replace = vi.fn();
let query = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(query) }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  usePathname: () => "/hardware/compare",
  useRouter: () => ({ push: vi.fn(), replace }),
  Link: ({ children }: { children: React.ReactNode }) => <a href="#">{children}</a>
}));
vi.mock("./comparison-provider", () => ({
  MAX_COMPARISON: 4,
  useComparison: () => ({ products: [], toggle: vi.fn(), clear: vi.fn() })
}));
const catalog: Record<string, Product> = {};
const add = (n: number, category: Category = "CPU") => {
  catalog[uid(n)] = { id: uid(n), category, model: `Model ${n}`, brands: [], specifications: { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null }, status: "ACTIVE", version: 1 } as Product;
};
const product = vi.fn((id: string) => Promise.resolve(catalog[id]));
vi.mock("../services/hardware-api", () => ({ hardwareApi: { product: (id: string) => product(id) } }));

const show = () => render(<NextIntlClientProvider locale="tr" messages={tr}><ComparisonScreen /></NextIntlClientProvider>);

describe("parseComparisonIds", () => {
  it("geçersiz ve tekrar eden kimlikleri atar, en çok dört tutar", () => {
    const raw = [uid(1), "x", uid(2), uid(1), uid(3), uid(4), uid(5)].join(",");
    expect(parseComparisonIds(raw)).toEqual([uid(1), uid(2), uid(3), uid(4)]);
    expect(parseComparisonIds(null)).toEqual([]);
  });
});

describe("ComparisonScreen", () => {
  beforeEach(() => { product.mockClear(); replace.mockClear(); [1, 2, 3].forEach((n) => add(n)); add(9, "GPU"); });

  it("iki üründen azsa istek atmadan yönlendirici mesaj gösterir", () => {
    query = `ids=${uid(1)}`;
    show();
    expect(screen.getByText("Karşılaştırmak için ürün seç")).toBeInTheDocument();
    expect(product).not.toHaveBeenCalled();
  });
  it("URL'deki her kimlik için ürünü yükler ve üç sütunlu tablo gösterir", async () => {
    query = `ids=${uid(1)},${uid(2)},${uid(3)}`;
    show();
    await waitFor(() => expect(screen.getByTestId("comparison-view")).toBeInTheDocument());
    expect(product).toHaveBeenCalledTimes(3);
    expect(screen.getAllByRole("columnheader")).toHaveLength(4);
  });
  it("farklı kategorileri tabloya koymaz, uyarı gösterir", async () => {
    query = `ids=${uid(1)},${uid(9)}`;
    show();
    await waitFor(() => expect(screen.getByText("Ürünler farklı kategorilerde")).toBeInTheDocument());
    expect(screen.queryByTestId("comparison-view")).not.toBeInTheDocument();
  });
  it("ürünü çıkarınca kalan kimliklerle URL'yi günceller", async () => {
    query = `ids=${uid(1)},${uid(2)},${uid(3)}`;
    show();
    await waitFor(() => expect(screen.getByTestId("comparison-view")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /Model 2 .*karşılaştırmadan çıkar/ }));
    expect(replace).toHaveBeenCalledWith({ pathname: "/hardware/compare", query: { ids: `${uid(1)},${uid(3)}` } }, { scroll: false });
  });
});
