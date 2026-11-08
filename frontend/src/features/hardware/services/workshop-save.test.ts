import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import type { Category, Component, Product, Profile } from "../types/hardware";
import type { WorkshopDraft } from "../schemas/workshop";
import { hardwareApi } from "./hardware-api";
import { saveWorkshop } from "./workshop-save";

vi.mock("./hardware-api", () => ({ hardwareApi: { createProfile: vi.fn(), updateProfile: vi.fn(), saveComponent: vi.fn() } }));
const api = vi.mocked(hardwareApi);

const specs = { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null };
const product = (id: string, category: Category): Product => ({ id, category, model: id, brands: [], specifications: specs, status: "ACTIVE", version: 0 });
const component = (id: string, category: Category, productId: string): Component => ({ id, category, productId, brandId: null, modelName: productId, brandName: null, quantity: 1, source: "MANUAL", version: 1, specifications: specs });
const profile: Profile = { id: "pc1", name: "Masaüstü", deviceType: "DESKTOP", caseColor: "BLACK", source: "MANUAL", version: 4, createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-01T10:00:00Z" };
const draft: WorkshopDraft = { name: "  Masaüstü  ", caseColor: "BLACK", slots: { CPU: { productId: "cpu1", product: product("cpu1", "CPU"), quantity: 1 }, GPU: { productId: "gpu1", product: product("gpu1", "GPU"), quantity: 1 } } };

describe("Atölye kaydı", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    let version = 10;
    api.saveComponent.mockImplementation(async (_profileId, body) => ({ profileVersion: ++version, component: component("new", body.category, body.productId!) }));
  });

  it("yeni bilgisayar: önce masaüstü profil, sonra her parça bir önceki sürümle sırayla kaydedilir", async () => {
    api.createProfile.mockResolvedValue({ ...profile, version: 0 });
    const onProfile = vi.fn();
    const result = await saveWorkshop({ draft: { ...draft, caseColor: "RED" }, saved: {}, onProfile });
    expect(api.createProfile).toHaveBeenCalledWith({ name: "Masaüstü", deviceType: "DESKTOP", caseColor: "RED" });
    expect(onProfile).toHaveBeenCalledWith(expect.objectContaining({ id: "pc1" }));
    expect(api.saveComponent.mock.calls.map(([, body, componentId]) => [body.category, body.profileVersion, componentId])).toEqual([["CPU", 0, undefined], ["GPU", 11, undefined]]);
    expect(result).toEqual({ profile: { ...profile, version: 0 }, version: 12, changedComponents: 2 });
  });

  it("ad ve renk aynıysa profil güncellenmez; yalnız değişen parça mevcut kaydın üzerine yazılır", async () => {
    const result = await saveWorkshop({ profile, draft, saved: { CPU: component("k1", "CPU", "cpu1"), GPU: component("k2", "GPU", "gpu-old") } });
    expect(api.updateProfile).not.toHaveBeenCalled();
    expect(api.saveComponent).toHaveBeenCalledTimes(1);
    expect(api.saveComponent).toHaveBeenCalledWith("pc1", { profileVersion: 4, category: "GPU", quantity: 1, productId: "gpu1" }, "k2");
    expect(result.changedComponents).toBe(1);
  });

  it("kasa rengi değişince profil cihaz türü korunarak güncellenir", async () => {
    api.updateProfile.mockResolvedValue({ ...profile, caseColor: "WHITE", version: 5 });
    await saveWorkshop({ profile, draft: { ...draft, caseColor: "WHITE", slots: {} }, saved: {} });
    expect(api.updateProfile).toHaveBeenCalledWith("pc1", { name: "Masaüstü", deviceType: "DESKTOP", caseColor: "WHITE", profileVersion: 4 });
    expect(api.saveComponent).not.toHaveBeenCalled();
  });

  it("çakışmada (409) zincir durur ve hata çağırana iletilir", async () => {
    api.saveComponent.mockRejectedValueOnce(new ApiHttpError(409, { code: "DATA_CONFLICT" }));
    await expect(saveWorkshop({ profile, draft, saved: {} })).rejects.toBeInstanceOf(ApiHttpError);
    expect(api.saveComponent).toHaveBeenCalledTimes(1);
  });
});
