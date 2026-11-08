import { readSpec } from "./spec-profile";
import { productVisual, type CpuVendor, type GpuVendor } from "./product-visual";
import type { CaseColor, Component, Product, Profile } from "../types/hardware";

/**
 * LTZ Atölyesi: kullanıcının seçtiği beş parça. Sıra montaj sırasıdır (anakart önce takılır).
 * Soğutma, fanlar, RGB, güç kaynağı ve kablolama sahnede sistem tarafından tamamlanır; profile kaydedilmez.
 */
export const workshopSlots = ["MOTHERBOARD", "CPU", "RAM", "STORAGE", "GPU"] as const;
export type WorkshopSlot = (typeof workshopSlots)[number];
/** Formda gösterim sırası. */
export const builderSlots: readonly WorkshopSlot[] = ["CPU", "MOTHERBOARD", "RAM", "GPU", "STORAGE"];
export const maxRamQuantity = 4;

export type DraftSlot = { productId: string | null; product: Product; quantity: number };
export type WorkshopDraft = { name: string; caseColor: CaseColor; slots: Partial<Record<WorkshopSlot, DraftSlot>> };
export type WorkshopBuild = {
  caseColor: CaseColor;
  parts: Record<WorkshopSlot, boolean>;
  /** Sahnede görünen bellek modülü sayısı (0–4). */
  ramSticks: number;
  cpuVendor?: CpuVendor;
  gpuVendor?: GpuVendor;
};

export const assemblySteps = ["motherboard", "cpu", "ram", "ssd", "gpu", "aio", "fans", "rgb", "power"] as const;
export type AssemblyStep = (typeof assemblySteps)[number];
/** Adımı oynatmak için gereken kullanıcı parçası; `undefined` olan adımları sistem tamamlar. */
export const stepSlot: Record<AssemblyStep, WorkshopSlot | undefined> = {
  motherboard: "MOTHERBOARD", cpu: "CPU", ram: "RAM", ssd: "STORAGE", gpu: "GPU", aio: undefined, fans: undefined, rgb: undefined, power: undefined
};

/** Yayımlanmış Blender çıktıları (docs-hardware/workshop/ltz_workshop_case.py). */
export const workshopModelPath = "/assets/hardware/workshop/ltz-workshop-case.glb";
export function workshopFallbackPath(color: CaseColor) {
  return `/assets/hardware/workshop/ltz-workshop-fallback-${color.toLowerCase()}.webp`;
}

/** Bellek modülleri ortadan dışarı doğru, çift kanal yuvalarına (A2/B2) öncelik verilerek dizilir. */
const ramLayouts: Record<number, string[]> = {
  0: [], 1: ["RAM_2"], 2: ["RAM_2", "RAM_4"], 3: ["RAM_1", "RAM_2", "RAM_3"], 4: ["RAM_1", "RAM_2", "RAM_3", "RAM_4"]
};
export function ramNodes(sticks: number): string[] {
  return ramLayouts[Math.max(0, Math.min(4, Math.trunc(sticks)))];
}

/** Kit kaydı (ör. 2 × 16 GB) adet ile çarpılır; dört yuvadan fazlası sahnede gösterilmez. */
export function ramSticks(slot?: DraftSlot): number {
  if (!slot) return 0;
  const modules = readSpec(slot.product, "moduleCount")?.number;
  const perKit = modules && modules > 0 ? Math.trunc(modules) : 1;
  return Math.max(1, Math.min(4, slot.quantity * perKit));
}

/** Katalog bağlantısı olmayan (manuel) bileşen için yalnız görsel/metin amaçlı ürün görünümü. */
export function componentProduct(component: Component): Product {
  return {
    id: component.productId ?? component.id,
    category: component.category,
    model: component.modelName,
    brands: component.brandName ? [{ id: component.brandId ?? "", name: component.brandName, status: "ACTIVE", version: 0 }] : [],
    specifications: component.specifications,
    status: "ACTIVE",
    version: component.version,
    source: component.brandName ? { brand: component.brandName } : null
  };
}

/** Her kategorinin ilk bileşeni atölye parçasıdır; ek kayıtlar Gelişmiş yönetimde kalır. */
export function slotComponents(components: Component[]): Partial<Record<WorkshopSlot, Component>> {
  const result: Partial<Record<WorkshopSlot, Component>> = {};
  for (const component of components) {
    const slot = component.category as WorkshopSlot;
    if ((workshopSlots as readonly string[]).includes(slot) && !result[slot]) result[slot] = component;
  }
  return result;
}

export function draftFromProfile(profile: Profile | undefined, components: Component[], products: Record<string, Product | undefined>): WorkshopDraft {
  const slots: WorkshopDraft["slots"] = {};
  for (const [slot, component] of Object.entries(slotComponents(components)) as Array<[WorkshopSlot, Component]>) {
    const product = component.productId ? products[component.productId] : undefined;
    slots[slot] = { productId: component.productId, product: product ?? componentProduct(component), quantity: component.quantity };
  }
  return { name: profile?.name ?? "", caseColor: profile?.caseColor ?? "BLACK", slots };
}

export function missingSlots(draft: Pick<WorkshopDraft, "slots">): WorkshopSlot[] {
  return builderSlots.filter((slot) => !draft.slots[slot]);
}

export function buildFromDraft(draft: WorkshopDraft): WorkshopBuild {
  const parts = Object.fromEntries(workshopSlots.map((slot) => [slot, Boolean(draft.slots[slot])])) as Record<WorkshopSlot, boolean>;
  const cpu = draft.slots.CPU ? productVisual(draft.slots.CPU.product) : undefined;
  const gpu = draft.slots.GPU ? productVisual(draft.slots.GPU.product) : undefined;
  return { caseColor: draft.caseColor, parts, ramSticks: ramSticks(draft.slots.RAM), cpuVendor: cpu?.cpuVendor, gpuVendor: gpu?.vendor };
}

export function isCompleteBuild(build: WorkshopBuild): boolean {
  return workshopSlots.every((slot) => build.parts[slot]);
}

/** Effect bağımlılığı için kararlı imza. */
export function buildSignature(build: WorkshopBuild): string {
  return [build.caseColor, ...workshopSlots.map((slot) => (build.parts[slot] ? "1" : "0")), build.ramSticks, build.cpuVendor ?? "-", build.gpuVendor ?? "-"].join("|");
}

/** Oynatılacak adımlar: parçası olmayan adım atlanır; güç yalnız tam kurulumda verilir. */
export function playableSteps(build: WorkshopBuild): AssemblyStep[] {
  const complete = isCompleteBuild(build);
  return assemblySteps.filter((step) => {
    const slot = stepSlot[step];
    if (slot) return build.parts[slot];
    return step === "rgb" || step === "power" ? complete : true;
  });
}

export type ComponentChange = { slot: WorkshopSlot; componentId?: string; productId: string; quantity: number };
/**
 * Kaydedilecek bileşenler: yalnız katalogdan seçilmiş ve kayıttan farklı olan parçalar. Formda boş bırakılan parça
 * mevcut kaydı silmez (kaldırma ayrı ve onaylı bir eylemdir); manuel kayıtlar değiştirilmedikçe korunur.
 */
export function componentChanges(draft: WorkshopDraft, saved: Partial<Record<WorkshopSlot, Component>>): ComponentChange[] {
  const changes: ComponentChange[] = [];
  for (const slot of workshopSlots) {
    const next = draft.slots[slot];
    if (!next?.productId) continue;
    const current = saved[slot];
    if (current && current.productId === next.productId && current.quantity === next.quantity) continue;
    changes.push({ slot, componentId: current?.id, productId: next.productId, quantity: next.quantity });
  }
  return changes;
}

export function isDraftDirty(draft: WorkshopDraft, initial: WorkshopDraft): boolean {
  if (draft.name.trim() !== initial.name.trim() || draft.caseColor !== initial.caseColor) return true;
  return workshopSlots.some((slot) => {
    const a = draft.slots[slot];
    const b = initial.slots[slot];
    return (a?.productId ?? a?.product.id) !== (b?.productId ?? b?.product.id) || a?.quantity !== b?.quantity;
  });
}

/** Montaj rotası: ev konumuna göre glTF ofsetleri (metre). Son nokta her zaman ev konumudur (0,0,0). */
export type RoutePoint = readonly [number, number, number];
const routeLimit = 2;

/**
 * Blender betiğinin her düğüme yazdığı `ltz_entry` (glTF extras) JSON rotasını doğrular. Geçersiz, sınır dışı veya
 * ev konumunda bitmeyen rota reddedilir; çağıran güvenli varsayılan rotayı kullanır.
 */
export function parseRoute(raw: unknown): RoutePoint[] | undefined {
  let value: unknown = raw;
  if (typeof raw === "string") {
    try { value = JSON.parse(raw); } catch { return undefined; }
  }
  if (!Array.isArray(value) || value.length < 2 || value.length > 8) return undefined;
  const points: RoutePoint[] = [];
  for (const item of value) {
    if (!Array.isArray(item) || item.length !== 3 || !item.every((n) => typeof n === "number" && Number.isFinite(n) && Math.abs(n) < routeLimit)) return undefined;
    points.push([item[0], item[1], item[2]]);
  }
  return points.at(-1)!.every((n) => n === 0) ? points : undefined;
}

/** Rotanın toplam uzunluğuna göre 0..1 ilerlemedeki nokta (her bölüm uzunluğu oranında süre alır). */
export function routePoint(points: readonly RoutePoint[], progress: number): RoutePoint {
  const lengths = points.slice(1).map((point, index) => Math.hypot(point[0] - points[index][0], point[1] - points[index][1], point[2] - points[index][2]));
  const total = lengths.reduce((sum, length) => sum + length, 0);
  if (total === 0 || progress <= 0) return points[0];
  let distance = Math.min(1, progress) * total;
  for (const [index, length] of lengths.entries()) {
    if (distance <= length || index === lengths.length - 1) {
      const t = length ? Math.min(1, distance / length) : 1;
      const [a, b] = [points[index], points[index + 1]];
      return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
    }
    distance -= length;
  }
  return points.at(-1)!;
}

export const sceneQualities = ["high", "balanced", "low"] as const;
export type SceneQuality = (typeof sceneQualities)[number];
export type QualityPreference = "auto" | SceneQuality;
export const qualityPreferences: readonly QualityPreference[] = ["auto", ...sceneQualities];

/** Kalite profilleri: piksel oranı tavanı, bloom (yalnız High), kare sınırı ve uyarlamalı düşüşte sonraki profil. */
export const qualityProfiles: Record<SceneQuality, { pixelRatio: number; bloom: boolean; fps: number; next?: SceneQuality }> = {
  high: { pixelRatio: 2, bloom: true, fps: 60, next: "balanced" },
  balanced: { pixelRatio: 1.5, bloom: false, fps: 60, next: "low" },
  low: { pixelRatio: 1, bloom: false, fps: 30 }
};

export type DeviceHints = { majorPerformanceCaveat: boolean; deviceMemory?: number; cores?: number; coarse: boolean; narrow: boolean };

/** Otomatik kalite: yazılım render/zayıf cihaz Low, dokunmatik veya dar ekran Balanced, diğerleri High. */
export function pickQuality(hints: DeviceHints): SceneQuality {
  if (hints.majorPerformanceCaveat) return "low";
  if ((hints.deviceMemory !== undefined && hints.deviceMemory <= 4) || (hints.cores !== undefined && hints.cores <= 4)) return "low";
  return hints.coarse || hints.narrow ? "balanced" : "high";
}
