import { readSpec } from "./spec-profile";
import type { Category, Product } from "../types/hardware";

export type VisualKind = "CPU" | "GPU" | "MOTHERBOARD" | "RAM" | "STORAGE_M2" | "STORAGE_SATA" | "STORAGE_HDD" | "STORAGE" | "OTHER";
export type GpuVendor = "NVIDIA" | "AMD" | "Intel";
export const visualAssets: Record<Exclude<VisualKind, "OTHER">, string> = {
  CPU: "cpu", GPU: "gpu", MOTHERBOARD: "motherboard", RAM: "ram",
  STORAGE_M2: "storage-nvme", STORAGE_SATA: "storage-sata", STORAGE_HDD: "storage-hdd", STORAGE: "storage"
};

/** Görsel seçimidir; ürün kaydını veya teknik değerlerini değiştirmez. */
export function productVisual(product?: Product, category: Category = "OTHER") {
  const kind = product?.category ?? category;
  const text = (key: string) => readSpec(product!, key)?.text ?? "";
  let visual: VisualKind = ["CPU", "GPU", "MOTHERBOARD", "RAM", "STORAGE"].includes(kind) ? kind as VisualKind : "OTHER";
  let vendor: GpuVendor | undefined;
  if (product && kind === "STORAGE") {
    const form = text("formFactor");
    const bus = text("interfaceType");
    const type = text("storageType");
    if (/HDD/i.test(type) || readSpec(product, "rotationRpm")?.number) visual = "STORAGE_HDD";
    else if (/M[.\s-]?2\b/i.test(form)) visual = "STORAGE_M2";
    else if (/SSD/i.test(type) && /SATA/i.test(bus) && /^2[.,]5(?:\D|$)/.test(form)) visual = "STORAGE_SATA";
    // PCIe tek başına M.2/NVMe kanıtı değildir; belirsiz kayıt genel görsel kullanır.
  }
  if (product && kind === "GPU") {
    const chipset = text("chipset") || product.model;
    if (/\b(NVIDIA|GeForce|Quadro|TITAN)\b/i.test(chipset)) vendor = "NVIDIA";
    else if (/\b(AMD|ATI|Radeon|FirePro)\b/i.test(chipset)) vendor = "AMD";
    else if (/\bIntel\b|\bArc\s+[AB]\d/i.test(chipset)) vendor = "Intel";
  }
  const brands = product?.brands.filter((brand) => brand.status === "ACTIVE") ?? [];
  const manufacturer = product?.source?.brand?.trim() || (brands.length === 1 ? brands[0].name : undefined);
  const badgeKeys = kind === "CPU" ? ["microarchitecture", "socket"] : kind === "GPU" ? ["capacityGb", "boostClockMhz"]
    : kind === "RAM" ? ["memoryType", "capacityGb"] : kind === "MOTHERBOARD" ? ["socket", "formFactor"] : ["capacityGb", "interfaceType"];
  return { kind: visual, vendor, manufacturer, badgeKeys: product ? badgeKeys.filter((key) => readSpec(product, key)).slice(0, 2) : [] };
}

/** Gelecekte yalnız incelenip yerel varlık olarak eklenmiş ürün fotoğrafları alınır. */
export type ApprovedHardwareImage = { src: string; alt: string };
export function approvedImagePath(image?: ApprovedHardwareImage): string | undefined {
  return image?.alt.trim() && /^\/assets\/hardware\/products\/[a-zA-Z0-9_-]+\.(?:png|jpe?g|webp|avif)$/.test(image.src) ? image.src : undefined;
}
