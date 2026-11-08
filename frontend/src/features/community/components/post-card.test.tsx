import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Viewer } from "../hooks/use-viewer";
import * as api from "../services/community-api";
import type { PostView } from "../types/community.types";
import { PostCard } from "./post-card";
import type { Lookups } from "./post-parts";

const push = vi.fn();
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
  usePathname: () => "/community",
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));
vi.mock("next/navigation", () => ({ usePathname: () => "/tr/topluluk" }));
vi.mock("../services/community-api", () => ({
  likePost: vi.fn(),
  unlikePost: vi.fn(),
  deletePost: vi.fn(),
  repostPost: vi.fn(),
  reportPost: vi.fn(),
  reportComment: vi.fn(),
  imageSrc: (image: { id: string }) => `http://gateway.test/img/${image.id}`
}));

const ME = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";
const lookups: Lookups = {
  author: (id) => (id === OTHER ? { userId: OTHER, username: "kaan", displayName: "Kaan A.", avatarUrl: null } : null),
  game: () => null
};
const member: Viewer = { status: "authenticated", accountId: ME, canModerate: false };
const guest: Viewer = { status: "guest", accountId: null, canModerate: false };

function post(patch: Partial<PostView> = {}): PostView {
  return {
    id: "33333333-3333-4333-8333-333333333333",
    authorId: OTHER,
    gameId: null,
    forumId: null,
    category: "GENERAL",
    body: "Yeni haritada orta kontrol çok iyi.",
    status: "VISIBLE",
    createdAt: "2026-11-07T10:00:00Z",
    updatedAt: "2026-11-07T10:00:00Z",
    likeCount: 4,
    commentCount: 2,
    likedByMe: false,
    images: [],
    repostOf: null,
    contentType: null,
    title: null,
    ...patch
  };
}

function mount(props: { post?: PostView; viewer?: Viewer } = {}) {
  const onChange = vi.fn<(next: PostView) => void>();
  const onRemoved = vi.fn<(id: string) => void>();
  render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PostCard post={props.post ?? post()} viewer={props.viewer ?? member} lookups={lookups} onChange={onChange} onRemoved={onRemoved} />
    </NextIntlClientProvider>
  );
  return { onChange, onRemoved };
}

const openMenu = () => fireEvent.click(screen.getByRole("button", { name: "Gönderi işlemleri" }));

beforeEach(() => {
  vi.resetAllMocks();
});

describe("Community gönderi kartı", () => {
  it("yazarı çözülmüş adla, düz metin gövdeyi HTML yorumlamadan gösterir", () => {
    mount({ post: post({ body: "<b>kalın</b> <img src=x onerror=alert(1)>" }) });
    expect(screen.getByText("Kaan A.")).toBeInTheDocument();
    expect(screen.getByText("<b>kalın</b> <img src=x onerror=alert(1)>")).toBeInTheDocument();
    expect(document.querySelector("article b, article img[src='x']")).toBeNull();
  });

  it("yazar çözülemezse ad uydurmaz; yedek etiket gösterir", () => {
    mount({ post: post({ authorId: "44444444-4444-4444-8444-444444444444" }) });
    expect(screen.getByText("Oyuncu")).toBeInTheDocument();
  });

  it("beğeni yalnız sunucu yanıtıyla güncellenir", async () => {
    vi.mocked(api.likePost).mockResolvedValueOnce({ postId: "x", liked: true, likeCount: 9 });
    const { onChange } = mount();
    fireEvent.click(screen.getByRole("button", { name: /Beğen/ }));
    await waitFor(() => expect(onChange).toHaveBeenCalledTimes(1));
    expect(onChange.mock.calls[0]![0]).toMatchObject({ likedByMe: true, likeCount: 9 });
  });

  it("başarısız beğeni sayacı oynatmaz ve hata gösterir", async () => {
    vi.mocked(api.likePost).mockRejectedValueOnce(new ApiHttpError(404));
    const { onChange } = mount();
    fireEvent.click(screen.getByRole("button", { name: /Beğen/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("İçerik bulunamadı");
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /Beğen/ })).toHaveAttribute("aria-pressed", "false");
  });

  it("aynı beğeni isteği sürerken ikinci tıklama istek atmaz", async () => {
    let finish: (value: { postId: string; liked: boolean; likeCount: number }) => void = () => {};
    vi.mocked(api.likePost).mockImplementationOnce(() => new Promise((resolve) => (finish = resolve)));
    mount();
    const button = screen.getByRole("button", { name: /Beğen/ });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(api.likePost).toHaveBeenCalledTimes(1);
    finish({ postId: "x", liked: true, likeCount: 5 });
    await waitFor(() => expect(screen.getByRole("button", { name: /Beğen/ })).not.toBeDisabled());
  });

  it("beğenilmiş gönderide beğeniyi geri alır", async () => {
    vi.mocked(api.unlikePost).mockResolvedValueOnce({ postId: "x", liked: false, likeCount: 3 });
    const { onChange } = mount({ post: post({ likedByMe: true }) });
    fireEvent.click(screen.getByRole("button", { name: /Beğeniyi geri al/ }));
    await waitFor(() => expect(onChange).toHaveBeenCalled());
    expect(api.likePost).not.toHaveBeenCalled();
    expect(onChange.mock.calls[0]![0]).toMatchObject({ likedByMe: false, likeCount: 3 });
  });

  it("misafir eylemi API'ye gitmez, girişe yönlendirir", () => {
    mount({ viewer: guest });
    fireEvent.click(screen.getByRole("button", { name: /Beğen/ }));
    fireEvent.click(screen.getByRole("button", { name: "Yeniden paylaş" }));
    expect(api.likePost).not.toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith({ pathname: "/login", query: { next: "/tr/topluluk" } });
  });

  it("işlemler sağ üstteki 'Gönderi işlemleri' menüsündedir; alt satırda yalnız beğeni, yorum ve yeniden paylaşım kalır", () => {
    mount({ post: post({ authorId: ME }) });
    const footer = document.querySelector("footer") as HTMLElement;
    expect(within(footer).queryByRole("button", { name: /Düzenle|Sil|Raporla/ })).toBeNull();
    expect(within(footer).queryByRole("link", { name: /Düzenle/ })).toBeNull();
    const trigger = screen.getByRole("button", { name: "Gönderi işlemleri" });
    const header = document.querySelector("article header") as HTMLElement;
    expect(header.contains(trigger)).toBe(true);
    expect(trigger).toHaveAttribute("aria-haspopup", "menu");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    // Kullanıcı bilgisi ile aynı başlık satırında, en sağdadır.
    expect(header.firstElementChild?.contains(screen.getByText("Yeni haritada orta kontrol çok iyi."))).toBe(false);
    expect(within(footer).getByRole("button", { name: /Beğen/ })).toBeInTheDocument();
    expect(within(footer).getByRole("button", { name: "Yeniden paylaş" })).toBeInTheDocument();
  });

  it("sahip: menüde Düzenle ve Sil görünür, Şikayet et görünmez; Düzenle gerçek düzenleme adresine gider", () => {
    mount({ post: post({ authorId: ME }) });
    openMenu();
    const menu = screen.getByRole("menu", { name: "Gönderi işlemleri" });
    expect(within(menu).getAllByRole("menuitem").map((item) => item.textContent)).toEqual(["Düzenle", "Sil"]);
    expect(within(menu).getByRole("menuitem", { name: "Düzenle" })).toHaveAttribute("href", expect.stringContaining("edit"));
    expect(within(menu).queryByRole("menuitem", { name: "Şikayet et" })).toBeNull();
  });

  it("başkasının gönderisinde menüde yalnız tek bir 'Şikayet et' vardır (Düzenle/Sil yok)", () => {
    mount();
    openMenu();
    const items = within(screen.getByRole("menu")).getAllByRole("menuitem");
    expect(items.map((item) => item.textContent)).toEqual(["Şikayet et"]);
  });

  it("yetkisi olmayan kullanıcıya işlem menüsü hiç gösterilmez (başkasının gizli gönderisi)", () => {
    mount({ post: post({ status: "HIDDEN" }) });
    expect(screen.queryByRole("button", { name: "Gönderi işlemleri" })).toBeNull();
  });

  it("menü Escape ve dışarı tıklamayla kapanır; Escape odağı tetikleyiciye döndürür", () => {
    mount({ post: post({ authorId: ME }) });
    const trigger = screen.getByRole("button", { name: "Gönderi işlemleri" });
    openMenu();
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("menu")).toBeNull();
    expect(trigger).toHaveFocus();
    openMenu();
    fireEvent.pointerDown(document.body);
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("silme menüden açılır, ortak onay ister; hata diyalogda kalır, başarıda liste bilgilendirilir", async () => {
    vi.mocked(api.deletePost).mockRejectedValueOnce(new ApiHttpError(409)).mockResolvedValueOnce(undefined);
    const { onRemoved } = mount({ post: post({ authorId: ME }) });
    openMenu();
    fireEvent.click(screen.getByRole("menuitem", { name: "Sil" }));
    const dialog = await screen.findByRole("dialog");
    expect(screen.queryByRole("menu")).toBeNull();
    fireEvent.click(within(dialog).getByRole("button", { name: "Sil" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("başka bir yerden değişmiş");
    expect(onRemoved).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole("button", { name: "Sil" }));
    await waitFor(() => expect(onRemoved).toHaveBeenCalledWith("33333333-3333-4333-8333-333333333333"));
  });

  it("sahibin yeniden paylaşımında menüdeki seçenek 'Yeniden paylaşımı geri al' olarak silme akışını kullanır", () => {
    mount({ post: post({ authorId: ME, repostOf: { postId: "55555555-5555-4555-8555-555555555555", available: true, authorId: OTHER, body: "x" } }) });
    openMenu();
    const items = within(screen.getByRole("menu")).getAllByRole("menuitem").map((item) => item.textContent);
    expect(items).toEqual(["Yeniden paylaşımı geri al"]);
  });

  it("kart ortak containerın tam genişliğidir (yatay margin yok) ve köşeleri sidebar ile aynı yumuşaklıktadır", () => {
    mount();
    const article = screen.getByRole("article");
    expect(article.className).toContain("w-full");
    expect(article.className).not.toMatch(/mx-auto|w-\[9\d%\]/);
    expect(article.className).toContain("rounded-2xl");
  });

  it("yeniden paylaşım zaten paylaşılmışsa 409'u anlaşılır gösterir", async () => {
    vi.mocked(api.repostPost).mockRejectedValueOnce(new ApiHttpError(409));
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Yeniden paylaş" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "Yeniden paylaş" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("zaten yeniden paylaştın");
  });

  it("rapor: gerekçe seçilmeden gönderilmez; başarı yalnız sunucu kabulünden sonra yazılır", async () => {
    vi.mocked(api.reportPost).mockRejectedValueOnce(new ApiHttpError(409)).mockResolvedValueOnce({});
    mount();
    openMenu();
    fireEvent.click(screen.getByRole("menuitem", { name: "Şikayet et" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(api.reportPost).not.toHaveBeenCalled();
    expect(within(dialog).getByText("Bir rapor nedeni seç.")).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole("radio", { name: "Spam" }));
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await within(dialog).findByText("Bu içeriği zaten raporladın.")).toBeInTheDocument();
    expect(within(dialog).queryByText(/Raporun alındı/)).toBeNull();

    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await within(dialog).findByText(/Raporun alındı/)).toBeInTheDocument();
    expect(api.reportPost).toHaveBeenLastCalledWith("33333333-3333-4333-8333-333333333333", "SPAM", "");
  });

  it("erişilemeyen repost kaynağı için yer tutucu gösterir, sahibi uydurmaz", () => {
    mount({ post: post({ body: null, repostOf: { postId: "55555555-5555-4555-8555-555555555555", available: false } }) });
    expect(screen.getByText("Orijinal gönderi artık görüntülenemiyor.")).toBeInTheDocument();
  });

  it("görsel yüklenemezse yer tutucu gösterir", () => {
    mount({
      post: post({
        images: [{ id: "i1", postId: "p", mimeType: "image/png", sizeBytes: 1, width: 10, height: 10, sortOrder: 0, contentUrl: "/x", createdAt: "2026-11-07T10:00:00Z" }]
      })
    });
    const image = screen.getByRole("img", { name: "Gönderi görseli 1" });
    expect(image).toHaveAttribute("src", "http://gateway.test/img/i1");
    fireEvent.error(image);
    expect(screen.getByText("Görsel yüklenemedi")).toBeInTheDocument();
  });
});
