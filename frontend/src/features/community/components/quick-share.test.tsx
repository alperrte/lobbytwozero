import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Viewer } from "../hooks/use-viewer";
import * as api from "../services/community-api";
import { getPublishState, resetPublishFlow } from "../services/publish-flow";
import type { PostView } from "../types/community.types";
import { QuickShare } from "./quick-share";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));
vi.mock("../services/community-api", () => ({
  createPost: vi.fn(),
  fetchAuthorCards: vi.fn().mockResolvedValue([])
}));

const member: Viewer = { status: "authenticated", accountId: "11111111-1111-4111-8111-111111111111", canModerate: false };
const guest: Viewer = { status: "guest", accountId: null, canModerate: false };

function mount(viewer: Viewer, onPublished = vi.fn()) {
  render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <QuickShare viewer={viewer} onPublished={onPublished} />
    </NextIntlClientProvider>
  );
  return onPublished;
}
const field = () => screen.getByRole("textbox", { name: "Gönderi metni" });
const submit = () => screen.getByRole("button", { name: "Paylaş" });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.fetchAuthorCards).mockResolvedValue([]);
});
afterEach(() => {
  act(() => resetPublishFlow());
});

describe("Akıştan hızlı paylaşım", () => {
  it("misafire alan yerine giriş bağlantısı gösterir", () => {
    mount(guest);
    expect(screen.queryByRole("textbox")).toBeNull();
    expect(screen.getByRole("link")).toHaveAttribute("href", "/login");
  });

  it("klavye kısayolu açıklaması gösterilmez; Ctrl+Enter yine paylaşır", async () => {
    vi.mocked(api.createPost).mockResolvedValueOnce({ id: "k1" } as PostView);
    const onPublished = mount(member);
    expect(screen.queryByText(/Ctrl/)).toBeNull();
    fireEvent.change(field(), { target: { value: "Kısayol" } });
    await act(async () => {
      fireEvent.keyDown(field(), { key: "Enter", ctrlKey: true });
    });
    await waitFor(() => expect(onPublished).toHaveBeenCalledWith("k1"));
  });

  it("boş ya da yalnız boşluk içeren metin paylaşılamaz", () => {
    mount(member);
    expect(submit()).toBeDisabled();
    fireEvent.change(field(), { target: { value: "   \n  " } });
    expect(submit()).toBeDisabled();
    fireEvent.submit(field().closest("form")!);
    expect(api.createPost).not.toHaveBeenCalled();
    expect(getPublishState().phase).toBe("idle");
  });

  it("yalnız metinle (başlıksız, görselsiz) oluşturur, katmanı açar, başarıda temizler ve kimliği bildirir", async () => {
    let resolve!: (post: PostView) => void;
    vi.mocked(api.createPost).mockReturnValueOnce(new Promise<PostView>((r) => (resolve = r)));
    const onPublished = mount(member);
    fireEvent.change(field(), { target: { value: "  Selam topluluk  " } });
    expect(submit()).toBeEnabled();
    fireEvent.click(submit());
    expect(api.createPost).toHaveBeenCalledWith({ body: "Selam topluluk" });
    expect(getPublishState().phase).toBe("publishing");

    // İkinci gönderim (çift tıklama) engellenir.
    fireEvent.submit(field().closest("form")!);
    expect(api.createPost).toHaveBeenCalledTimes(1);

    await act(async () => resolve({ id: "new-post" } as PostView));
    await waitFor(() => expect(onPublished).toHaveBeenCalledWith("new-post"));
    expect(field()).toHaveValue("");
  });

  it("hatada metin korunur, hata gösterilir ve katman kapanmaya bırakılır", async () => {
    vi.mocked(api.createPost).mockRejectedValueOnce(new Error("sunucu hatası"));
    const onPublished = mount(member);
    fireEvent.change(field(), { target: { value: "Kaybolmasın" } });
    await act(async () => {
      fireEvent.click(submit());
    });
    expect(field()).toHaveValue("Kaybolmasın");
    expect(onPublished).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
