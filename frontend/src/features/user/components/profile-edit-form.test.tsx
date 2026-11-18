import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { MyProfile } from "../types/profile.types";

const push = vi.fn();
vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) => (
    <a href={typeof href === "string" ? href : "#"} {...rest}>
      {children}
    </a>
  ),
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/profile/edit"
}));

vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: "authenticated" }) }));

const profileRef: { current: MyProfile } = { current: undefined as unknown as MyProfile };
vi.mock("../hooks/use-my-profile", () => ({
  useMyProfile: () => ({ state: { status: "ready", profile: profileRef.current, steam: null, stats: null }, retry: vi.fn() })
}));

const api = { updateMyProfile: vi.fn(), removeMyImage: vi.fn(), uploadMyImage: vi.fn() };
vi.mock("../services/profile-api", () => ({
  updateMyProfile: (...args: unknown[]) => api.updateMyProfile(...args),
  removeMyImage: (...args: unknown[]) => api.removeMyImage(...args),
  uploadMyImage: (...args: unknown[]) => api.uploadMyImage(...args)
}));
vi.mock("../services/profile-events", () => ({ emitProfileChanged: vi.fn() }));

import { changedFields, ProfileEditForm } from "./profile-edit-form";

const base: MyProfile = {
  username: "kerem",
  displayName: "Kerem",
  bio: "Merhaba",
  country: "TR",
  playerTypes: ["CASUAL"],
  favoriteGenres: ["rpg"],
  favoritePlatforms: ["steam"],
  avatar: null,
  cover: { small: "http://x/c-small.png", large: "http://x/c-large.png" },
  memberSince: "2026-11-10T10:00:00Z",
  version: 3
};

function renderForm() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <ProfileEditForm />
    </NextIntlClientProvider>
  );
}

const nameInput = () => screen.getByLabelText("Görünen ad");

beforeEach(() => {
  profileRef.current = base;
  push.mockReset();
  for (const mock of Object.values(api)) mock.mockReset();
});
afterEach(cleanup);

describe("changedFields", () => {
  const saved = { displayName: "Kerem", bio: "", country: "TR", types: ["CASUAL"] as const, genres: ["rpg", "fps"], platforms: [] as string[] };
  const same = { ...saved, types: [...saved.types] };

  it("aynıysa boş döner; boşluk, ülke harf boyutu ve seçim sırası farkı sayılmaz", () => {
    expect(changedFields({ ...same, types: [...same.types] }, { ...same, displayName: " Kerem ", country: "tr", genres: ["fps", "rpg"], types: [...same.types] })).toEqual([]);
  });

  it("değişen alanları söyler", () => {
    expect(changedFields({ ...same, types: [...same.types] }, { ...same, bio: "yeni", platforms: ["pc"], types: ["COMPETITIVE"] })).toEqual(["bio", "playerTypes", "platforms"]);
  });
});

describe("ProfileEditForm", () => {
  it("değişiklik yokken kaydet kapalıdır ve uyarı çubuğu boştur", () => {
    renderForm();
    expect(screen.getByRole("button", { name: "Değişiklikleri kaydet" })).toBeDisabled();
    expect(screen.queryByText("Kaydedilmemiş değişikliklerin var")).not.toBeInTheDocument();
  });

  it("önizleme yardımcı teknolojiden gizlidir; sayfada tek ana başlık vardır", () => {
    renderForm();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
  });

  it("değişiklik çubuğu görünür; kaydetmeden önce onay penceresi değişen alanları listeler", () => {
    renderForm();
    fireEvent.change(nameInput(), { target: { value: "Yeni Ad" } });
    expect(screen.getByText("Kaydedilmemiş değişikliklerin var")).toBeInTheDocument();
    expect(screen.getByText(/1 alan değişti/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    const dialog = screen.getByRole("dialog", { name: "Değişiklikler kaydedilsin mi?" });
    expect(within(dialog).getByText("Görünen ad")).toBeInTheDocument();
    expect(api.updateMyProfile).not.toHaveBeenCalled();
  });

  it("onaylanınca kaydeder, pencere kapanır, başarı mesajı çıkar", async () => {
    api.updateMyProfile.mockResolvedValue({ ...base, displayName: "Yeni Ad", version: 4 });
    renderForm();
    fireEvent.change(nameInput(), { target: { value: "Yeni Ad" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));

    await waitFor(() => expect(screen.getByText("Profilin kaydedildi.")).toBeInTheDocument());
    expect(api.updateMyProfile).toHaveBeenCalledWith(expect.objectContaining({ version: 3, displayName: "Yeni Ad", country: "TR" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByText("Kaydedilmemiş değişikliklerin var")).not.toBeInTheDocument();
  });

  it("onay penceresinde Geri dön kaydetmez", () => {
    renderForm();
    fireEvent.change(nameInput(), { target: { value: "Yeni Ad" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    fireEvent.click(screen.getByRole("button", { name: "Geri dön" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(api.updateMyProfile).not.toHaveBeenCalled();
  });

  it("geçersiz ad onay penceresini açmaz, hata gösterir", () => {
    renderForm();
    fireEvent.change(nameInput(), { target: { value: "K" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("sunucu hatasında pencere kapanır ve hata mesajı gösterilir", async () => {
    api.updateMyProfile.mockRejectedValue(new Error("boom"));
    renderForm();
    fireEvent.change(nameInput(), { target: { value: "Yeni Ad" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText("Kaydedilmemiş değişikliklerin var")).toBeInTheDocument();
  });

  it("değişiklik yokken Vazgeç doğrudan profile döner", () => {
    renderForm();
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    expect(push).toHaveBeenCalledWith("/profile");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("değişiklik varken Vazgeç önce atma onayı ister", () => {
    renderForm();
    fireEvent.change(nameInput(), { target: { value: "Yeni Ad" } });
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    expect(push).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Düzenlemeye dön" }));
    expect(push).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, at" }));
    expect(push).toHaveBeenCalledWith("/profile");
  });

  it("değişiklik varken sekme kapatma uyarısı bağlanır, yokken bağlanmaz", () => {
    const add = vi.spyOn(window, "addEventListener");
    renderForm();
    expect(add.mock.calls.some(([type]) => type === "beforeunload")).toBe(false);
    fireEvent.change(nameInput(), { target: { value: "Yeni Ad" } });
    expect(add.mock.calls.some(([type]) => type === "beforeunload")).toBe(true);
    add.mockRestore();
  });

  it("görsel kaldırma onay ister; onayla kaldırır", async () => {
    api.removeMyImage.mockResolvedValue(undefined);
    renderForm();
    fireEvent.click(screen.getByRole("button", { name: "Kaldır" }));
    expect(screen.getByRole("dialog", { name: "Kapak görseli kaldırılsın mı?" })).toBeInTheDocument();
    expect(api.removeMyImage).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaldır" }));
    await waitFor(() => expect(api.removeMyImage).toHaveBeenCalledWith("cover"));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("bölüm menüsü üç bölüme bağlantı verir", () => {
    renderForm();
    const nav = screen.getByRole("navigation", { name: "Düzenleme bölümleri" });
    const hrefs = within(nav).getAllByRole("link").map((link) => link.getAttribute("href"));
    expect(hrefs).toEqual(["#images", "#info", "#prefs"]);
  });
});
