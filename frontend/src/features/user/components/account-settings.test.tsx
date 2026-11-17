import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Consents, Preferences, PrivacySettings } from "../types/settings.types";

const session = { status: "authenticated" as "authenticated" | "guest" | "loading" };
vi.mock("@/features/auth", () => ({
  useRequireSession: () => ({ status: session.status }),
  AccountDeletionSection: () => null,
  AccountSecuritySection: () => null,
  AccountAgeSection: () => <div data-testid="age-section" />
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/profil/ayarlar",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

const changeTheme = vi.fn();
vi.mock("@/shared/lib/theme/theme-transition", () => ({ changeTheme: (...args: unknown[]) => changeTheme(...args) }));

const api = {
  getPrivacy: vi.fn(),
  savePrivacy: vi.fn(),
  getPreferences: vi.fn(),
  savePreferences: vi.fn(),
  getConsents: vi.fn(),
  saveConsents: vi.fn(),
  getLinkedSteam: vi.fn(),
  saveSteamVisibility: vi.fn()
};
vi.mock("../services/settings-api", () => ({
  getPrivacy: (...args: unknown[]) => api.getPrivacy(...args),
  savePrivacy: (...args: unknown[]) => api.savePrivacy(...args),
  getPreferences: (...args: unknown[]) => api.getPreferences(...args),
  savePreferences: (...args: unknown[]) => api.savePreferences(...args),
  getConsents: (...args: unknown[]) => api.getConsents(...args),
  saveConsents: (...args: unknown[]) => api.saveConsents(...args),
  getLinkedSteam: (...args: unknown[]) => api.getLinkedSteam(...args),
  saveSteamVisibility: (...args: unknown[]) => api.saveSteamVisibility(...args)
}));

import { AccountSettings } from "./account-settings";

const privacy: PrivacySettings = {
  friendsList: "FRIENDS",
  library: "PUBLIC",
  hardware: "FRIENDS",
  performance: "PRIVATE",
  guestProfileAccess: "BASIC",
  version: 2
};
const preferences: Preferences = { locale: "tr", theme: "SYSTEM", reducedMotion: true, compactLayout: false, version: 1 };
const consents: Consents = { recommendation: false, recommendationChangedAt: null, version: 0 };

function renderPage() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <AccountSettings />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  session.status = "authenticated";
  for (const mock of Object.values(api)) mock.mockReset();
  changeTheme.mockReset();
  api.getPrivacy.mockResolvedValue(privacy);
  api.getPreferences.mockResolvedValue(preferences);
  api.getConsents.mockResolvedValue(consents);
  api.getLinkedSteam.mockResolvedValue({ linked: false, steamId: null, showOnProfile: false, version: 0 });
});
afterEach(cleanup);

const radios = (group: string) => within(screen.getByRole("radiogroup", { name: group })).getAllByRole("radio") as HTMLInputElement[];
const picked = (group: string) => radios(group).find((radio) => radio.checked)?.value;
const choose = (group: string, value: string) => fireEvent.click(radios(group).find((radio) => radio.value === value) as HTMLInputElement);

describe("AccountSettings", () => {
  it("yaş doğrulaması bölümünü oturumlu kullanıcıya gösterir", async () => {
    renderPage();
    expect(await screen.findByRole("heading", { name: "Yaş doğrulaması" })).toBeInTheDocument();
    expect(screen.getByTestId("age-section")).toBeInTheDocument();
  });

  it("oturum doğrulanmadan hiçbir uca istek atmaz", () => {
    session.status = "guest";
    renderPage();
    expect(api.getPrivacy).not.toHaveBeenCalled();
    expect(api.getPreferences).not.toHaveBeenCalled();
    expect(api.getConsents).not.toHaveBeenCalled();
  });

  it("üç bölümü sunucudaki değerlerle gösterir; rıza varsayılan olarak kapalıdır", async () => {
    renderPage();

    await screen.findByRole("radiogroup", { name: "Arkadaş listesi" });
    expect(picked("Arkadaş listesi")).toBe("FRIENDS");
    expect(picked("Oyun kütüphanesi")).toBe("PUBLIC");
    expect(picked("Performans")).toBe("PRIVATE");
    expect(picked("Giriş yapmamış ziyaretçiler")).toBe("BASIC");
    await screen.findByRole("radiogroup", { name: "Tema" });
    expect(picked("Tema")).toBe("SYSTEM");
    expect(await screen.findByLabelText("Kişiselleştirilmiş oyun önerileri")).not.toBeChecked();
  });

  it("bir bölümün hatası diğerlerini bozmaz ve tekrar deneme sunar", async () => {
    api.getPreferences.mockRejectedValue(new ApiHttpError(503, {}));
    renderPage();

    expect(await screen.findByLabelText("Arkadaş listesi")).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(await screen.findByLabelText("Kişiselleştirilmiş oyun önerileri")).toBeInTheDocument();
    api.getPreferences.mockResolvedValue(preferences);
    fireEvent.click(screen.getByRole("button", { name: /Tekrar dene/ }));
    expect(await screen.findByLabelText("Tema")).toBeInTheDocument();
  });

  it("gizlilik kaydı tüm ayarları son görülen sürümle gönderir; 'Kaydedildi' yalnız yanıt gelince görünür", async () => {
    api.savePrivacy.mockResolvedValue({ ...privacy, library: "PRIVATE", version: 3 });
    renderPage();

    await screen.findByRole("radiogroup", { name: "Oyun kütüphanesi" });
    choose("Oyun kütüphanesi", "PRIVATE");
    expect(screen.queryByText("Kaydedildi.")).toBeNull();
    fireEvent.click(screen.getAllByRole("button", { name: "Kaydet" })[0]);

    await waitFor(() => expect(api.savePrivacy).toHaveBeenCalledWith({ ...privacy, library: "PRIVATE", version: 2 }));
    expect((await screen.findAllByText("Kaydedildi.")).length).toBeGreaterThan(0);
  });

  it("sürüm çakışmasında (409) açıklama ve 'Yenile' gösterir, girdiyi silmez", async () => {
    api.savePrivacy.mockRejectedValue(new ApiHttpError(409, { code: "CONCURRENT_MODIFICATION" }));
    renderPage();

    await screen.findByRole("radiogroup", { name: "Oyun kütüphanesi" });
    choose("Oyun kütüphanesi", "FRIENDS");
    fireEvent.click(screen.getAllByRole("button", { name: "Kaydet" })[0]);

    expect(await screen.findByText(/başka bir yerde değişti/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Yenile" })).toBeInTheDocument();
    expect(picked("Oyun kütüphanesi")).toBe("FRIENDS");
  });

  it("tercih kaydı sunucudaki hareket/yerleşim değerlerini olduğu gibi geri gönderir ve temayı cihaza uygular", async () => {
    api.savePreferences.mockResolvedValue({ ...preferences, theme: "DARK", version: 2 });
    renderPage();

    await screen.findByRole("radiogroup", { name: "Tema" });
    choose("Tema", "DARK");
    fireEvent.click(screen.getAllByRole("button", { name: "Kaydet" })[1]);

    await waitFor(() =>
      expect(api.savePreferences).toHaveBeenCalledWith({ ...preferences, theme: "DARK", version: 1 })
    );
    await waitFor(() => expect(changeTheme).toHaveBeenCalledWith("dark"));
  });

  it("rıza açılınca sunucuya yazılır ve durum yanıtla güncellenir; geri çekilebilir", async () => {
    api.saveConsents.mockResolvedValueOnce({ recommendation: true, recommendationChangedAt: "2026-11-09T10:00:00Z", version: 0 });
    renderPage();

    const box = await screen.findByLabelText("Kişiselleştirilmiş oyun önerileri");
    fireEvent.click(box);

    await waitFor(() =>
      expect(api.saveConsents).toHaveBeenCalledWith({ recommendation: true, recommendationChangedAt: null, version: 0 })
    );
    await waitFor(() => expect(screen.getByLabelText("Kişiselleştirilmiş oyun önerileri")).toBeChecked());
    expect(screen.getByText(/^Açık · .*tarihinden beri$/)).toBeInTheDocument();

    api.saveConsents.mockResolvedValueOnce({ recommendation: false, recommendationChangedAt: "2026-11-09T11:00:00Z", version: 1 });
    fireEvent.click(screen.getByLabelText("Kişiselleştirilmiş oyun önerileri"));
    await waitFor(() => expect(screen.getByLabelText("Kişiselleştirilmiş oyun önerileri")).not.toBeChecked());
  });

  it("rıza kaydı başarısız olursa kutu eski durumunda kalır ve hata gösterilir", async () => {
    api.saveConsents.mockRejectedValue(new ApiHttpError(503, {}));
    renderPage();

    fireEvent.click(await screen.findByLabelText("Kişiselleştirilmiş oyun önerileri"));

    expect(await screen.findByText(/Servis şu an meşgul/)).toBeInTheDocument();
    expect(screen.getByLabelText("Kişiselleştirilmiş oyun önerileri")).not.toBeChecked();
  });

  it("Steam bağlı değilse seçim sunulmaz, bağlıysa seçim yanıt gelince güncellenir", async () => {
    renderPage();
    expect(await screen.findByText(/Bağlı bir Steam hesabın yok/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Steam bağlantımı profilimde göster")).not.toBeInTheDocument();
  });

  it("bağlı Steam görünürlüğü sunucuya yazılır; hata olursa kutu eski durumunda kalır", async () => {
    const linked = { linked: true, steamId: "76561198000000000", showOnProfile: false, version: 2 };
    api.getLinkedSteam.mockResolvedValue(linked);
    api.saveSteamVisibility.mockResolvedValueOnce({ ...linked, showOnProfile: true, version: 3 });
    renderPage();

    fireEvent.click(await screen.findByLabelText("Steam bağlantımı profilimde göster"));
    await waitFor(() => expect(api.saveSteamVisibility).toHaveBeenCalledWith(linked, true));
    await waitFor(() => expect(screen.getByLabelText("Steam bağlantımı profilimde göster")).toBeChecked());

    api.saveSteamVisibility.mockRejectedValueOnce(new ApiHttpError(503, {}));
    fireEvent.click(screen.getByLabelText("Steam bağlantımı profilimde göster"));
    expect(await screen.findByText(/Servis şu an meşgul/)).toBeInTheDocument();
    expect(screen.getByLabelText("Steam bağlantımı profilimde göster")).toBeChecked();
  });

  it("sol menü bölümlere bağlanır; silme bölümü tehlikeli bölge olarak ayrılır", async () => {
    renderPage();
    const nav = await screen.findByRole("navigation", { name: "Ayar bölümleri" });
    const hrefs = Array.from(nav.querySelectorAll("a")).map((link) => link.getAttribute("href"));
    expect(hrefs).toEqual(["#privacy", "#consents", "#preferences", "#steam", "#security", "#age", "#deletion"]);
    expect(nav).toHaveTextContent("Tehlikeli bölge");
    for (const href of hrefs) {
      expect(document.getElementById((href ?? "").slice(1))).not.toBeNull();
    }
  });
});
