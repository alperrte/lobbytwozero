import { describe, expect, it } from "vitest";
import type { Category, Component, Product, Profile, TypedValue } from "../types/hardware";
import {
  buildFromDraft, buildSignature, componentChanges, draftFromProfile, isCompleteBuild, isDraftDirty, missingSlots, playableSteps,
  parseRoute, pickQuality, qualityProfiles, ramNodes, ramSticks, routePoint, slotComponents, workshopFallbackPath, type WorkshopDraft
} from "./workshop";

const specs = { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null };
function product(id: string, category: Category, model: string, typed: Record<string, TypedValue> = {}): Product {
  return { id, category, model, brands: [], specifications: specs, status: "ACTIVE", version: 0, source: { typedSpecifications: typed } };
}
function component(id: string, category: Category, productId: string | null, quantity = 1, modelName = `${category} model`): Component {
  return { id, category, productId, brandId: null, modelName, brandName: null, quantity, source: "MANUAL", version: 1, specifications: specs };
}
const profile: Profile = { id: "pc1", name: "Oyun bilgisayarı", deviceType: "DESKTOP", caseColor: "WHITE", source: "MANUAL", version: 3, createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-01T10:00:00Z" };
const cpu = product("cpu1", "CPU", "Ryzen 7 7800X3D");
const gpu = product("gpu1", "GPU", "GeForce RTX 4070 Super");
const board = product("mb1", "MOTHERBOARD", "B650 Tomahawk");
const ram = product("ram1", "RAM", "Vengeance 32 GB", { moduleCount: { number: 2, text: null, unit: null } });
const ssd = product("ssd1", "STORAGE", "990 Pro 2 TB");
const full: WorkshopDraft = {
  name: "Oyun bilgisayarı", caseColor: "BLACK",
  slots: {
    CPU: { productId: "cpu1", product: cpu, quantity: 1 }, GPU: { productId: "gpu1", product: gpu, quantity: 1 },
    MOTHERBOARD: { productId: "mb1", product: board, quantity: 1 }, RAM: { productId: "ram1", product: ram, quantity: 1 },
    STORAGE: { productId: "ssd1", product: ssd, quantity: 1 }
  }
};

describe("LTZ Atölyesi şeması", () => {
  it("tam kurulum: beş parça, üretici ve bellek modülü sayısı sahneye aktarılır", () => {
    const build = buildFromDraft(full);
    expect(isCompleteBuild(build)).toBe(true);
    expect(build).toMatchObject({ caseColor: "BLACK", ramSticks: 2, cpuVendor: "AMD", gpuVendor: "NVIDIA" });
    expect(missingSlots(full)).toEqual([]);
    expect(playableSteps(build)).toEqual(["motherboard", "cpu", "ram", "ssd", "gpu", "aio", "fans", "rgb", "power"]);
  });

  it("eksik parçalar form sırasıyla listelenir; eksik parçanın adımı atlanır, RGB ve güç verilmez", () => {
    const draft: WorkshopDraft = { ...full, slots: { CPU: full.slots.CPU, RAM: full.slots.RAM } };
    expect(missingSlots(draft)).toEqual(["MOTHERBOARD", "GPU", "STORAGE"]);
    const build = buildFromDraft(draft);
    expect(isCompleteBuild(build)).toBe(false);
    expect(build.gpuVendor).toBeUndefined();
    expect(playableSteps(build)).toEqual(["cpu", "ram", "aio", "fans"]);
  });

  it("bellek: kit × adet, en az 1 en fazla 4 modül; yuvalar çift kanal önceliğiyle dolar", () => {
    expect(ramSticks(undefined)).toBe(0);
    expect(ramSticks({ productId: "ram1", product: ram, quantity: 1 })).toBe(2);
    expect(ramSticks({ productId: "ram1", product: ram, quantity: 3 })).toBe(4);
    expect(ramSticks({ productId: "x", product: product("x", "RAM", "Tek modül"), quantity: 1 })).toBe(1);
    expect(ramNodes(1)).toEqual(["RAM_2"]);
    expect(ramNodes(2)).toEqual(["RAM_2", "RAM_4"]);
    expect(ramNodes(9)).toHaveLength(4);
    expect(ramNodes(-1)).toEqual([]);
  });

  it("Intel işlemci ve AMD/Intel ekran kartı üreticisi okunur", () => {
    const build = buildFromDraft({ ...full, slots: { ...full.slots, CPU: { productId: "c", product: product("c", "CPU", "Core i7-14700K"), quantity: 1 }, GPU: { productId: "g", product: product("g", "GPU", "Radeon RX 7800 XT"), quantity: 1 } } });
    expect(build.cpuVendor).toBe("Intel");
    expect(build.gpuVendor).toBe("AMD");
    expect(buildFromDraft({ ...full, slots: { ...full.slots, GPU: { productId: "g", product: product("g", "GPU", "Arc B580"), quantity: 1 } } }).gpuVendor).toBe("Intel");
  });

  it("imza kasa rengi, parça, bellek ve üretici değişince değişir", () => {
    const base = buildSignature(buildFromDraft(full));
    expect(buildSignature(buildFromDraft({ ...full, caseColor: "RED" }))).not.toBe(base);
    expect(buildSignature(buildFromDraft({ ...full, slots: { ...full.slots, RAM: { productId: "ram1", product: ram, quantity: 2 } } }))).not.toBe(base);
    expect(buildSignature(buildFromDraft({ ...full, name: "Başka ad" }))).toBe(base);
  });

  it("profilden taslak: kategori başına ilk kayıt; katalog ürünü yoksa bileşen kaydı gösterilir", () => {
    const components = [component("k1", "CPU", "cpu1"), component("k2", "CPU", "cpu2"), component("k3", "RAM", null, 2, "Elle girilen bellek"), component("k4", "PSU", null)];
    expect(Object.keys(slotComponents(components))).toEqual(["CPU", "RAM"]);
    const draft = draftFromProfile(profile, components, { cpu1: cpu });
    expect(draft.name).toBe("Oyun bilgisayarı");
    expect(draft.caseColor).toBe("WHITE");
    expect(draft.slots.CPU?.product).toBe(cpu);
    expect(draft.slots.RAM).toMatchObject({ productId: null, quantity: 2, product: { model: "Elle girilen bellek", category: "RAM" } });
    expect(draftFromProfile(undefined, [], {})).toEqual({ name: "", caseColor: "BLACK", slots: {} });
  });

  it("kaydedilecek değişiklikler: yalnız katalogdan seçilmiş ve kayıttan farklı parçalar", () => {
    const saved = slotComponents([component("k1", "CPU", "cpu1"), component("k2", "RAM", "ram1", 1), component("k3", "GPU", null)]);
    const draft: WorkshopDraft = { ...full, slots: { ...full.slots, RAM: { productId: "ram1", product: ram, quantity: 2 } } };
    expect(componentChanges(draft, saved)).toEqual([
      { slot: "MOTHERBOARD", componentId: undefined, productId: "mb1", quantity: 1 },
      { slot: "RAM", componentId: "k2", productId: "ram1", quantity: 2 },
      { slot: "STORAGE", componentId: undefined, productId: "ssd1", quantity: 1 },
      { slot: "GPU", componentId: "k3", productId: "gpu1", quantity: 1 }
    ]);
    // Formda boş bırakılan veya elle girilmiş parça kaydı değiştirmez.
    const manual: WorkshopDraft = { ...full, slots: { GPU: { productId: null, product: gpu, quantity: 1 } } };
    expect(componentChanges(manual, saved)).toEqual([]);
  });

  it("taslak değişikliği: ad (boşluk hariç), kasa rengi, parça veya adet", () => {
    expect(isDraftDirty(full, full)).toBe(false);
    expect(isDraftDirty({ ...full, name: " Oyun bilgisayarı " }, full)).toBe(false);
    expect(isDraftDirty({ ...full, caseColor: "GRAPHITE" }, full)).toBe(true);
    expect(isDraftDirty({ ...full, slots: { ...full.slots, CPU: undefined } }, full)).toBe(true);
    expect(isDraftDirty({ ...full, slots: { ...full.slots, RAM: { productId: "ram1", product: ram, quantity: 4 } } }, full)).toBe(true);
  });

  it("kasa rengine göre Blender önizlemesi seçilir", () => {
    expect(workshopFallbackPath("GRAPHITE")).toBe("/assets/hardware/workshop/ltz-workshop-fallback-graphite.webp");
  });
});

describe("Atölye montaj rotası (GLB ltz_entry)", () => {
  it("JSON rotayı ayrıştırır; ev konumunda bitmeyen, sınır dışı veya bozuk rotayı reddeder", () => {
    expect(parseRoute("[[-0.008,0,0.2],[-0.008,0,0],[0,0,0]]")).toEqual([[-0.008, 0, 0.2], [-0.008, 0, 0], [0, 0, 0]]);
    expect(parseRoute([[0, 0, 0.1], [0, 0, 0]])).toEqual([[0, 0, 0.1], [0, 0, 0]]);
    expect(parseRoute("[[0,0,0.1],[0,0,0.01]]")).toBeUndefined();
    expect(parseRoute("[[0,0,5],[0,0,0]]")).toBeUndefined();
    expect(parseRoute("[[0,0],[0,0,0]]")).toBeUndefined();
    expect(parseRoute("[[0,0,0]]")).toBeUndefined();
    expect(parseRoute("{bozuk")).toBeUndefined();
    expect(parseRoute(undefined)).toBeUndefined();
  });

  it("ilerleme rota uzunluğuna göre bölünür: önce cam yönünden iner, sonra yuvaya kayar", () => {
    const route = [[0.1, 0, 0.3], [0.1, 0, 0], [0, 0, 0]] as const;
    expect(routePoint(route, 0)).toEqual([0.1, 0, 0.3]);
    const middle = routePoint(route, 0.5);
    expect(middle[0]).toBeCloseTo(0.1);
    expect(middle[2]).toBeCloseTo(0.1);
    const late = routePoint(route, 0.875);
    expect(late[0]).toBeCloseTo(0.05);
    expect(late[2]).toBeCloseTo(0);
    expect(routePoint(route, 1)).toEqual([0, 0, 0]);
  });
});

describe("Atölye kalite profili", () => {
  const desktop = { majorPerformanceCaveat: false, deviceMemory: 16, cores: 12, coarse: false, narrow: false };
  it("masaüstü High, dokunmatik/dar ekran Balanced, yazılım render veya zayıf cihaz Low", () => {
    expect(pickQuality(desktop)).toBe("high");
    expect(pickQuality({ ...desktop, coarse: true })).toBe("balanced");
    expect(pickQuality({ ...desktop, narrow: true })).toBe("balanced");
    expect(pickQuality({ ...desktop, majorPerformanceCaveat: true })).toBe("low");
    expect(pickQuality({ ...desktop, deviceMemory: 4 })).toBe("low");
    expect(pickQuality({ ...desktop, cores: 4 })).toBe("low");
    expect(pickQuality({ ...desktop, deviceMemory: undefined, cores: undefined })).toBe("high");
  });

  it("bloom yalnız High profilde; uyarlamalı düşüş High → Balanced → Low", () => {
    expect(qualityProfiles.high).toMatchObject({ bloom: true, next: "balanced" });
    expect(qualityProfiles.balanced).toMatchObject({ bloom: false, next: "low" });
    expect(qualityProfiles.low).toMatchObject({ bloom: false, fps: 30 });
    expect(qualityProfiles.low.next).toBeUndefined();
  });
});
