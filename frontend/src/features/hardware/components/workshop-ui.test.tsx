import { useState } from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import en from "@/shared/lib/i18n/messages/en.json";
import { hardwareApi } from "../services/hardware-api";
import { buildFromDraft, type WorkshopDraft } from "../schemas/workshop";
import type { Category, Component, Product, ProfileDetail, Profile } from "../types/hardware";
import { ProfilesScreen } from "./profiles-screen";
import { WorkshopBuilder } from "./workshop-builder";
import { WorkshopStage } from "./workshop-stage";
import { WorkshopSummary } from "./workshop-summary";

vi.mock("../services/hardware-api", async (original) => ({
  ...(await original<typeof import("../services/hardware-api")>()),
  hardwareApi: {
    profiles: vi.fn(), statistics: vi.fn(), profile: vi.fn(), product: vi.fn(), products: vi.fn(), privacy: vi.fn(), compareProfiles: vi.fn(),
    createProfile: vi.fn(), updateProfile: vi.fn(), deleteProfile: vi.fn(), saveComponent: vi.fn(), deleteComponent: vi.fn()
  }
}));
const api = vi.mocked(hardwareApi);
const w = tr.Hardware.workshop;
const cat = tr.Hardware.categories;

const specs = { capacityGb: null, clockMhz: null, coreCount: null, powerWatts: null, memoryType: null, socket: null, interfaceType: null, formFactor: null };
const product = (id: string, category: Category, model: string): Product => ({ id, category, model, brands: [], specifications: specs, status: "ACTIVE", version: 0 });
const component = (id: string, category: Category, productId: string, modelName: string): Component => ({ id, category, productId, brandId: null, modelName, brandName: null, quantity: 1, source: "MANUAL", version: 1, specifications: specs });
const cpu = product("cpu1", "CPU", "Ryzen 7 7800X3D");
const gpu = product("gpu1", "GPU", "GeForce RTX 4070 Super");
const parts = { CPU: cpu, GPU: gpu, MOTHERBOARD: product("mb1", "MOTHERBOARD", "B650 Tomahawk"), RAM: product("ram1", "RAM", "Vengeance 32 GB"), STORAGE: product("ssd1", "STORAGE", "990 Pro 2 TB") };
const full: WorkshopDraft = { name: "Oyun bilgisayarı", caseColor: "BLACK", slots: Object.fromEntries(Object.entries(parts).map(([slot, item]) => [slot, { productId: item.id, product: item, quantity: 1 }])) };
const profile: Profile = { id: "pc1", name: "Oyun bilgisayarı", deviceType: "DESKTOP", caseColor: "GRAPHITE", source: "MANUAL", version: 3, createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-01T10:00:00Z" };
const statistics = { totalProfiles: 0, profilesByDeviceType: {}, totalComponentEntries: 0, totalComponentUnits: 0, componentsByCategory: {}, recordedRamCapacityGb: 0, ramUnitsWithoutCapacity: 0, recordedStorageCapacityGb: 0, storageUnitsWithoutCapacity: 0 };
const page = <T,>(items: T[]) => ({ items, page: 0, size: 12, totalElements: items.length, totalPages: items.length ? 1 : 0 });

function wrap(node: React.ReactNode, locale: "tr" | "en" = "tr") {
  return render(<NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>{node}</NextIntlClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  // jsdom WebGL ve matchMedia sunmaz: sahne dürüstçe Blender önizleme görseline düşmelidir.
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);
  vi.stubGlobal("matchMedia", (query: string) => ({ matches: false, media: query, addEventListener: () => undefined, removeEventListener: () => undefined }));
  window.sessionStorage.clear();
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe("Atölye sahnesi", () => {
  it("WebGL yoksa görsel moduna düşer; tam kurulumda üretici ve kasa rengi rozetleri ile hafif görünüm notu gösterilir", async () => {
    wrap(<WorkshopStage build={buildFromDraft({ ...full, caseColor: "RED" })} replayToken={0} profileKey="pc1" missing={[]} onPickParts={() => undefined} />);
    const stage = screen.getByTestId("workshop-stage");
    await waitFor(() => expect(stage).toHaveAttribute("data-stage-mode", "image"));
    expect(stage).toHaveAttribute("data-case-color", "RED");
    expect(stage).toHaveAttribute("data-cpu-vendor", "AMD");
    expect(stage).toHaveAttribute("data-gpu-vendor", "NVIDIA");
    expect(screen.getByText(w.stage.imageNotice)).toBeInTheDocument();
    expect(screen.getByRole("img", { name: w.stage.imageAlt.replace("{color}", w.colors.RED) })).toBeInTheDocument();
    expect(screen.queryByTestId("workshop-missing")).toBeNull();
  });

  it("eksik parçada montaj yerine rehber: eksikler listelenir, adımlar atlanmış görünür, seç düğmesi çalışır", async () => {
    const onPickParts = vi.fn();
    const draft: WorkshopDraft = { ...full, slots: { CPU: full.slots.CPU } };
    wrap(<WorkshopStage build={buildFromDraft(draft)} replayToken={0} profileKey="pc1" missing={["MOTHERBOARD", "RAM", "GPU", "STORAGE"]} onPickParts={onPickParts} />);
    const stage = screen.getByTestId("workshop-stage");
    expect(stage).toHaveAttribute("data-assembly", "incomplete");
    const guide = screen.getByTestId("workshop-missing");
    expect(within(guide).getByText(w.missing.title)).toBeInTheDocument();
    for (const slot of ["MOTHERBOARD", "RAM", "GPU", "STORAGE"] as const) expect(within(guide).getByText(cat[slot])).toBeInTheDocument();
    expect(screen.getByTestId("workshop-status")).toHaveTextContent("4");
    const steps = screen.getByRole("list", { name: w.stepsTitle });
    expect(within(steps).getByText(w.steps.gpu).closest("li")).toHaveAttribute("data-state", "skipped");
    fireEvent.click(within(guide).getByRole("button", { name: w.missing.pick }));
    expect(onPickParts).toHaveBeenCalledTimes(1);
  });
});

describe("Atölye kurulum formu", () => {
  function Harness({ initial, mode, onSave }: { initial: WorkshopDraft; mode: "create" | "edit"; onSave: () => void }) {
    const [draft, setDraft] = useState(initial);
    return <WorkshopBuilder mode={mode} draft={draft} initial={initial} onChange={setDraft} onPick={() => undefined} onSave={onSave} onReset={() => setDraft(initial)} pending={false} error={undefined} saved={false} />;
  }

  it("yeni kurulumda ad zorunlu; ad girilince kaydedilebilir", () => {
    const onSave = vi.fn();
    wrap(<Harness mode="create" initial={{ name: "", caseColor: "BLACK", slots: {} }} onSave={onSave} />);
    const save = screen.getByRole("button", { name: w.builder.create });
    expect(save).toBeDisabled();
    for (const slot of ["CPU", "MOTHERBOARD", "RAM", "GPU", "STORAGE"] as const) expect(within(screen.getByTestId(`workshop-slot-${slot}`)).getByText(w.builder.notSelected)).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: tr.Hardware.profiles.name }), { target: { value: "Yeni kasa" } });
    expect(save).toBeEnabled();
    fireEvent.click(save);
    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it("düzenlemede kasa rengi ve bellek adedi değişince kaydedilmemiş değişiklik görünür, geri alınabilir", () => {
    wrap(<Harness mode="edit" initial={full} onSave={() => undefined} />);
    expect(screen.getByRole("button", { name: w.builder.save })).toBeDisabled();
    expect(within(screen.getByTestId("workshop-slot-CPU")).getByText("AMD")).toBeInTheDocument();
    expect(within(screen.getByTestId("workshop-slot-GPU")).getByText("NVIDIA")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", { name: w.colors.WHITE }));
    expect(screen.getByRole("radio", { name: w.colors.WHITE })).toBeChecked();
    fireEvent.change(screen.getByRole("combobox", { name: w.builder.ramQuantity }), { target: { value: "2" } });
    expect(screen.getByText(w.builder.dirty)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: w.builder.save })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: w.builder.reset }));
    expect(screen.getByRole("radio", { name: w.colors.BLACK })).toBeChecked();
    expect(screen.queryByText(w.builder.dirty)).toBeNull();
  });

  it("İngilizce metinlerle çalışır", () => {
    wrap(<Harness mode="create" initial={{ name: "", caseColor: "BLACK", slots: {} }} onSave={() => undefined} />, "en");
    expect(screen.getByRole("heading", { name: en.Hardware.workshop.builder.createTitle })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: `Select ${en.Hardware.categories.CPU}` })).toBeInTheDocument();
  });
});

describe("Bilgisayar Bilgilerim", () => {
  const components = [component("k1", "CPU", "cpu1", cpu.model), component("k2", "GPU", "gpu1", gpu.model)];
  const saved: WorkshopDraft = { ...full, slots: { CPU: full.slots.CPU, GPU: full.slots.GPU } };

  it("seçili parçalar, eksikler ve atölyenin tamamladığı parçalar listelenir", () => {
    wrap(<WorkshopSummary profile={profile} components={components} saved={saved} totalComponents={3} complete={false} onPick={() => undefined} onEdit={() => undefined} onReplay={() => undefined} onChanged={() => undefined} onDeleted={() => undefined} />);
    expect(screen.getByRole("heading", { name: w.summary.title })).toBeInTheDocument();
    expect(screen.getByText(`${profile.name} / ${w.colors.GRAPHITE} kasa`)).toBeInTheDocument();
    expect(within(screen.getByTestId("workshop-info-CPU")).getByRole("heading", { name: cpu.model })).toBeInTheDocument();
    expect(within(screen.getByTestId("workshop-info-RAM")).getByText(w.summary.missing)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: w.stage.replay })).toBeDisabled();
    expect(screen.getByText(/1 ek kayıt/)).toBeInTheDocument();
    for (const key of ["aio", "fans", "rgb", "psu", "cables"] as const) expect(screen.getByText(w.auto[key])).toBeInTheDocument();
  });

  it("parça kaldırma onaylanınca profil sürümüyle silinir", async () => {
    const onChanged = vi.fn();
    api.deleteComponent.mockResolvedValue(undefined);
    wrap(<WorkshopSummary profile={profile} components={components} saved={saved} totalComponents={2} complete={false} onPick={() => undefined} onEdit={() => undefined} onReplay={() => undefined} onChanged={onChanged} onDeleted={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: `${cat.GPU} kaldır` }));
    const dialog = screen.getByRole("dialog", { name: w.summary.removeTitle });
    expect(api.deleteComponent).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole("button", { name: w.summary.remove }));
    await waitFor(() => expect(api.deleteComponent).toHaveBeenCalledWith("pc1", "k2", 3));
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
  });
});

describe("Bilgisayarlarım ekranı", () => {
  it("bilgisayar yokken boş durum ve yeni kurulum formu; parça seçilip kaydedilince profil ve parça oluşturulur, profil açılır", async () => {
    const created: Profile = { ...profile, id: "pc9", name: "İlk kasa", caseColor: "WHITE", version: 0 };
    let profiles: Profile[] = [];
    api.profiles.mockImplementation(async () => page(profiles));
    api.statistics.mockResolvedValue(statistics);
    api.products.mockResolvedValue({ ...page([cpu]), size: 8 });
    api.product.mockResolvedValue(cpu);
    api.createProfile.mockImplementation(async () => { profiles = [created]; return created; });
    api.saveComponent.mockResolvedValue({ profileVersion: 1, component: component("k1", "CPU", "cpu1", cpu.model) });
    const detail: ProfileDetail = { profile: { ...created, version: 1 }, components: [component("k1", "CPU", "cpu1", cpu.model)], page: 0, size: 20, totalComponents: 1, totalPages: 1 };
    api.profile.mockResolvedValue(detail);

    wrap(<ProfilesScreen />);
    expect(await screen.findByRole("heading", { name: tr.Hardware.profiles.emptyTitle })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: w.builder.createTitle })).toBeInTheDocument();
    expect(screen.getByTestId("workshop-stage")).toHaveAttribute("data-assembly", "incomplete");

    fireEvent.change(screen.getByRole("textbox", { name: tr.Hardware.profiles.name }), { target: { value: "İlk kasa" } });
    fireEvent.click(screen.getByRole("radio", { name: w.colors.WHITE }));
    fireEvent.click(screen.getByRole("button", { name: `${cat.CPU} seç` }));
    const picker = await screen.findByRole("dialog");
    fireEvent.click(await within(picker).findByRole("button", { name: new RegExp(cpu.model) }));
    expect(api.products).toHaveBeenCalledWith(expect.stringContaining("category=CPU"), expect.anything());
    expect(within(screen.getByTestId("workshop-slot-CPU")).getByText(cpu.model)).toBeInTheDocument();
    expect(screen.getByTestId("workshop-stage")).toHaveAttribute("data-case-color", "WHITE");

    fireEvent.click(screen.getByRole("button", { name: w.builder.create }));
    await waitFor(() => expect(api.createProfile).toHaveBeenCalledWith({ name: "İlk kasa", deviceType: "DESKTOP", caseColor: "WHITE" }));
    await waitFor(() => expect(api.saveComponent).toHaveBeenCalledWith("pc9", { profileVersion: 0, category: "CPU", quantity: 1, productId: "cpu1" }, undefined));
    expect(await screen.findByTestId("workshop-summary")).toBeInTheDocument();
    expect(api.profile).toHaveBeenCalledWith("pc9", 0, expect.anything());
    expect(screen.getByRole("heading", { name: w.builder.editTitle })).toBeInTheDocument();
    expect(await screen.findByText(w.builder.saved)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "İlk kasa", pressed: true })).toBeInTheDocument();
    expect(screen.queryByTestId("workshop-first-run")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: tr.Hardware.profiles.create })).not.toBeInTheDocument();
  });
});
