import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import records from "../../../../e2e/fixtures/hardware-visual-products.json";
import type { Product } from "../types/hardware";
import { approvedImagePath, productVisual } from "../schemas/product-visual";
import { HardwareProductVisual } from "./hardware-product-visual";

// Mevcut local katalogdan salt okunur snapshot; teknik değer üretilmez.
const products = records as unknown as Product[];
const wrap = (node: React.ReactNode, locale: "tr" | "en" = "tr") => render(<NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>{node}</NextIntlClientProvider>);

describe("HardwareProductVisual", () => {
  it("gerçek beş kategori ve depolama biçimleri için uygun çizimi seçer", () => {
    expect(products.map((product) => productVisual(product).kind)).toEqual([
      "CPU", "CPU", "GPU", "GPU", "GPU", "MOTHERBOARD", "RAM", "STORAGE_M2", "STORAGE_SATA", "STORAGE_HDD"
    ]);
    expect(productVisual(undefined, "STORAGE").kind).toBe("STORAGE");
    expect(productVisual(undefined, "PSU").kind).toBe("OTHER");
  });

  it("kart üreticisi ile GPU üreticisini ayırır; model adını değiştirmez", () => {
    const before = JSON.stringify(products);
    expect(productVisual(products[2])).toMatchObject({ manufacturer: "MSI", vendor: "NVIDIA" });
    expect(productVisual(products[3])).toMatchObject({ manufacturer: "Sapphire", vendor: "AMD" });
    expect(productVisual(products[4])).toMatchObject({ manufacturer: "ASRock", vendor: "Intel" });
    wrap(<HardwareProductVisual product={products[2]} />);
    expect(screen.getByText("MSI")).toBeVisible();
    expect(screen.getByLabelText("GPU üreticisi: NVIDIA")).toBeVisible();
    expect(screen.getByText("Temsilî görsel")).toBeVisible();
    expect(screen.queryByText(/Founders Edition/)).toBeNull();
    expect(JSON.stringify(products)).toBe(before);
  });

  it("işlemci ve ekran kartı için üreticiye özel çizim seçer, üreticisi bilinmeyene genel çizim verir", () => {
    expect(productVisual(products[0])).toMatchObject({ asset: "cpu-amd", cpuVendor: "AMD" });
    expect(productVisual(products[1])).toMatchObject({ asset: "cpu-intel", cpuVendor: "Intel" });
    expect(productVisual(products[2]).asset).toBe("gpu-nvidia");
    expect(productVisual(products[3]).asset).toBe("gpu-amd");
    expect(productVisual(products[4]).asset).toBe("gpu-intel");
    const unknown: Product = { ...products[0], model: "Bilinmeyen İşlemci", source: null };
    expect(productVisual(unknown)).toMatchObject({ asset: "cpu", cpuVendor: undefined });
    expect(productVisual(products[5]).asset).toBe("motherboard");
    expect(productVisual(undefined, "PSU").asset).toBeUndefined();
  });

  it("RAM neslini yalnız kayıtlı değerden gösterir; bilinmeyen marka görünümü bozmaz", () => {
    wrap(<HardwareProductVisual product={products[6]} />);
    expect(screen.getByText("DDR5")).toBeVisible();
    expect(screen.getByText("Corsair")).toBeVisible();
  });

  it("eksik marka ve teknik özelliklerde genelleme yapmaz", () => {
    const product: Product = { ...products[8], brands: [], source: null, specifications: { ...products[8].specifications, formFactor: null } };
    expect(productVisual(product)).toMatchObject({ kind: "STORAGE", manufacturer: undefined });
    wrap(<HardwareProductVisual product={product} />);
    expect(screen.getByText(tr.Hardware.catalog.brandIndependent)).toBeVisible();
  });

  it("İngilizce temsilî görsel ve GPU etiketi kullanır", () => {
    wrap(<HardwareProductVisual product={products[4]} />, "en");
    expect(screen.getByText("Representative illustration")).toBeVisible();
    expect(screen.getByLabelText("GPU manufacturer: Intel")).toBeVisible();
  });

  it("gelecekte onaylı yerel görseli önceler, yüklenemezse kategori çizimine döner", () => {
    const approvedImage = { src: "/assets/hardware/products/approved-example.webp", alt: "Approved product image" };
    const { container } = wrap(<HardwareProductVisual product={products[0]} approvedImage={approvedImage} />);
    const image = screen.getByAltText(approvedImage.alt);
    expect(new URL(image.getAttribute("src")!, "http://localhost").pathname).toBe(approvedImage.src);
    expect(screen.queryByText("Temsilî görsel")).toBeNull();
    fireEvent.error(image);
    expect(new URL(container.querySelector("img")!.getAttribute("src")!, "http://localhost").pathname).toBe("/assets/hardware/illustrations/cpu-amd.svg");
    expect(screen.getByText("Temsilî görsel")).toBeVisible();
    expect(approvedImagePath({ src: "https://example.com/photo.jpg", alt: "external" })).toBeUndefined();
    expect(approvedImagePath({ src: "/assets/hardware/products/../private.png", alt: "traversal" })).toBeUndefined();
  });
});
