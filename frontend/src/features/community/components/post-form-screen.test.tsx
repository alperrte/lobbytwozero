import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import * as api from "../services/community-api";
import { getPublishState, resetPublishFlow } from "../services/publish-flow";
import type { PostView } from "../types/community.types";
import { PostFormScreen } from "./post-form-screen";

const push = vi.fn();
vi.mock("@/features/auth", () => ({
  useRequireSession: () => ({ status: "authenticated", me: { accountId: "11111111-1111-4111-8111-111111111111", roles: [] } })
}));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams() }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push }),
  usePathname: () => "/community/new",
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));
vi.mock("../services/community-api", () => ({
  createPost: vi.fn(),
  uploadPostImage: vi.fn(),
  fetchForum: vi.fn(),
  searchGames: vi.fn(),
  fetchAuthorCards: vi.fn().mockResolvedValue([]),
  fetchGameCards: vi.fn().mockResolvedValue([])
}));

const GAME = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";

function mount() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PostFormScreen />
    </NextIntlClientProvider>
  );
}
const field = () => screen.getByLabelText(/^Metin/);
const submit = () => screen.getByRole("button", { name: "Paylaş" });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.fetchAuthorCards).mockResolvedValue([]);
  vi.mocked(api.fetchGameCards).mockResolvedValue([]);
});
afterEach(() => {
  act(() => resetPublishFlow());
});

// Form ağır modülleri ilk çizimde yükler; soğuk başlangıçta varsayılan 5 sn sınırı yetmeyebilir.
vi.setConfig({ testTimeout: 20_000 });

describe("Gönderi oluşturma formu", () => {
  it("kategori alanı yoktur; yalnız 'Gönderi' ve 'Oyun içeriği' türleri seçilebilir; tür açıklaması gösterilmez", () => {
    mount();
    expect(screen.queryByText("Kategori")).toBeNull();
    const kinds = screen.getAllByRole("radio");
    expect(kinds).toHaveLength(2);
    expect(screen.getByRole("radio", { name: "Gönderi" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Oyun içeriği" })).not.toBeChecked();
    expect(screen.queryByText(/Başlık ve oyun zorunlu/)).toBeNull();
    expect(screen.queryByText(/Toplulukla serbestçe/)).toBeNull();
  });

  it("seçili türün ikon çerçevesi vurgulanır; seçenek kartı kırmızıya boyanmaz", () => {
    mount();
    const post = screen.getByRole("radio", { name: "Gönderi" });
    expect(post.className).toContain("peer");
    const frame = post.nextElementSibling as HTMLElement;
    expect(frame.className).toContain("peer-checked:border-primary");
    expect(frame.className).toContain("peer-checked:text-brand-text");
    expect(post.closest("label")?.className).not.toMatch(/bg-primary|has-\[:checked\]/);
  });

  it("form sırası: tür, oyun bağlantısı, görseller, açıklama; metin 'görünecektir' ifadesini kullanır", () => {
    mount();
    const headings = screen.getAllByRole("heading", { level: 2 }).map((node) => node.textContent);
    expect(headings.slice(0, 4)).toEqual(["Ne paylaşıyorsun?", "Oyun bağlantısı", "Görseller", "Açıklama"]);
    expect(screen.getByText(/akışında görünecektir/)).toBeInTheDocument();
    expect(screen.queryByText(/oyunun sayfasında listelenir/)).toBeNull();
    expect(screen.queryByText(/akışında görünür/)).toBeNull();
  });

  it("açıklama sayacı boşken 0/2000 gösterir, yazdıkça güncellenir ve giriş 2000 karakterle sınırlıdır", () => {
    mount();
    expect(screen.getByText("0/2000")).toBeInTheDocument();
    expect(field()).toHaveAttribute("maxLength", "2000");
    fireEvent.change(field(), { target: { value: "Merhaba" } });
    expect(screen.getByText("7/2000")).toBeInTheDocument();
  });

  it("2000 karakteri aşan metin paylaşım doğrulamasında reddedilir (onay modalı açılmaz, istek gitmez)", () => {
    mount();
    fireEvent.change(field(), { target: { value: "a".repeat(2001) } });
    fireEvent.click(submit());
    expect(screen.getByText("En çok 2000 karakter olabilir.")).toBeInTheDocument();
    expect(document.querySelector("dialog[open]")).toBeNull();
    expect(api.createPost).not.toHaveBeenCalled();
  });

  it("'İçerik türü' bölümü yoktur: başlık, Rehber / Easter egg / Oyuncu deneyimi seçenekleri ve ilgili alanlar görünmez", () => {
    mount();
    fireEvent.click(screen.getByRole("radio", { name: "Oyun içeriği" }));
    expect(screen.queryByText("İçerik türü")).toBeNull();
    expect(screen.queryByText("Rehber")).toBeNull();
    expect(screen.queryByText("Easter egg")).toBeNull();
    expect(screen.queryByText("Oyuncu deneyimi")).toBeNull();
    expect(screen.queryByLabelText(/^Başlık/)).toBeNull();
    expect(screen.getAllByRole("radio")).toHaveLength(2);
    // Önizlemede de tür etiketi yoktur.
    expect(document.querySelector("aside")?.textContent).not.toMatch(/Rehber|Easter egg|Oyuncu deneyimi/);
  });

  it("'Gönderi' türünde oyun isteğe bağlıdır; 'Oyun içeriği' türünde oyun zorunludur (oyun bağlantısı kuralları korunur)", () => {
    mount();
    expect(screen.getByLabelText("Oyun ara")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", { name: "Oyun içeriği" }));
    expect(screen.getByLabelText("Oyun ara (zorunlu)")).toBeInTheDocument();
    // "Oyun bağlantısı" bölümü doğrudan tür seçiminin ardındadır (boşluk bırakan içerik türü alanı yok).
    const headings = screen.getAllByRole("heading", { level: 2 }).map((node) => node.textContent);
    expect(headings.slice(0, 2)).toEqual(["Ne paylaşıyorsun?", "Oyun bağlantısı"]);
    fireEvent.change(field(), { target: { value: "Oyun notu" } });
    fireEvent.click(submit());
    expect(screen.getByText("Oyuna bağlı içerik için bir oyun seç.")).toBeInTheDocument();
    expect(document.querySelector("dialog[open]")).toBeNull();
    expect(api.createPost).not.toHaveBeenCalled();
  });

  it("oyun, katalog aramasından seçilir ve kimliği (gameId) ile gönderilir", async () => {
    vi.mocked(api.searchGames).mockResolvedValue([{ gameId: GAME, title: "Hades" }] as never);
    vi.mocked(api.createPost).mockResolvedValue({ id: "new" } as PostView);
    mount();
    fireEvent.change(screen.getByLabelText("Oyun ara"), { target: { value: "had" } });
    fireEvent.click(await screen.findByRole("button", { name: "Hades" }));
    fireEvent.click(screen.getByRole("radio", { name: "Oyun içeriği" }));
    fireEvent.change(field(), { target: { value: "Oyun notu" } });
    fireEvent.click(submit());
    const dialog = await screen.findByRole("dialog");
    await act(async () => {
      fireEvent.click(within(dialog).getByRole("button", { name: "Paylaş" }));
    });
    // İçerik türü alanı olmadığından contentType/title (ve rastgele bir varsayılan) gönderilmez.
    await waitFor(() => expect(api.createPost).toHaveBeenCalledWith({ body: "Oyun notu", gameId: GAME }));
  });

  it("'Paylaş' onay modalını açar; vazgeçilirse istek gitmez ve form korunur", async () => {
    mount();
    fireEvent.change(field(), { target: { value: "Korunacak metin" } });
    fireEvent.click(submit());
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Gönderiyi paylaşmak istiyor musun?")).toBeInTheDocument();
    expect(api.createPost).not.toHaveBeenCalled();
    expect(getPublishState().phase).toBe("idle");
    fireEvent.click(within(dialog).getByRole("button", { name: "Vazgeç" }));
    await waitFor(() => expect(document.querySelector("dialog[open]")).toBeNull());
    expect(api.createPost).not.toHaveBeenCalled();
    expect(field()).toHaveValue("Korunacak metin");
  });

  it("onaylanınca paylaşım başlar: katman açılır, kategori gönderilmez, başarıda akışa yönlenir", async () => {
    let resolve!: (post: PostView) => void;
    vi.mocked(api.createPost).mockReturnValueOnce(new Promise<PostView>((r) => (resolve = r)));
    mount();
    fireEvent.change(field(), { target: { value: "Onaylı gönderi" } });
    fireEvent.click(submit());
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "Paylaş" }));
    await waitFor(() => expect(api.createPost).toHaveBeenCalledTimes(1));
    expect(api.createPost).toHaveBeenCalledWith({ body: "Onaylı gönderi" });
    expect(getPublishState().phase).toBe("publishing");
    await act(async () => resolve({ id: "p9" } as PostView));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/community"));
    expect(getPublishState().phase).toBe("awaitingList");
  });

  it("istek başarısız olursa başarı bildirimi kurulmaz, form verisi korunur ve hata gösterilir", async () => {
    vi.mocked(api.createPost).mockRejectedValueOnce(new Error("sunucu hatası"));
    mount();
    fireEvent.change(field(), { target: { value: "Hata senaryosu" } });
    fireEvent.click(submit());
    const dialog = await screen.findByRole("dialog");
    await act(async () => {
      fireEvent.click(within(dialog).getByRole("button", { name: "Paylaş" }));
    });
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    expect(getPublishState().flash).toBeNull();
    expect(push).not.toHaveBeenCalled();
    expect(field()).toHaveValue("Hata senaryosu");
  });

  it("'Vazgeç' ve 'Paylaş' düğmeleri aynı boyut sınıflarını paylaşır", () => {
    mount();
    const cancel = screen.getByRole("link", { name: "Vazgeç" });
    expect(cancel.className).toContain("h-12");
    expect(submit().className).toContain("h-12");
    expect(cancel.className).toContain("w-full");
    expect(submit().className).toContain("w-full");
  });
});
