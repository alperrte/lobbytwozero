import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Viewer } from "../hooks/use-viewer";
import * as api from "../services/community-api";
import type { PostView } from "../types/community.types";
import { PostCard } from "./post-card";
import type { Lookups } from "./post-parts";

const overflow = vi.hoisted(() => ({ value: false }));
vi.mock("../hooks/use-overflow", () => ({ useOverflow: () => overflow.value }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
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
  fetchComments: vi.fn(),
  fetchReplies: vi.fn(),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
  fetchAuthorCards: vi.fn().mockResolvedValue([]),
  imageSrc: (image: { id: string }) => `http://gateway.test/img/${image.id}`
}));

const ME = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";
const lookups: Lookups = {
  author: (id) => (id === OTHER ? { userId: OTHER, username: "kaan", displayName: "Kaan A.", avatarUrl: null } : null),
  game: () => null
};
const member: Viewer = { status: "authenticated", accountId: ME, canModerate: false };

const image = (n: number, width = 800, height = 600) => ({ id: `i${n}`, postId: "p1", mimeType: "image/png" as const, sizeBytes: 1, width, height, sortOrder: n, contentUrl: "/x", createdAt: "2026-11-07T10:00:00Z" });

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

function mount(value: PostView, onChange = vi.fn()) {
  render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PostCard post={value} viewer={member} lookups={lookups} onChange={onChange} onRemoved={vi.fn()} />
    </NextIntlClientProvider>
  );
  return onChange;
}

const follows = (first: Element, second: Element) => Boolean(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING);

beforeEach(() => {
  vi.clearAllMocks();
  overflow.value = false;
  vi.mocked(api.fetchAuthorCards).mockResolvedValue([]);
});

describe("Ortak gönderi kartı (Akış ve Gönderilerim)", () => {
  it("içerik sırası: kullanıcı ve tarih → metin → görseller → etkileşim satırı", () => {
    mount(post({ images: [image(1)] }));
    const header = screen.getByText("Kaan A.");
    const text = screen.getByText("Yeni haritada orta kontrol çok iyi.");
    const media = screen.getByRole("region", { name: /görselli galeri/ });
    const footer = screen.getByRole("contentinfo", { hidden: true }) ?? document.querySelector("footer");
    expect(follows(header, text)).toBe(true);
    expect(follows(text, media)).toBe(true);
    expect(follows(media, footer)).toBe(true);
  });

  it("yalnızca metin içeren gönderide görsel alanı oluşmaz", () => {
    mount(post());
    expect(screen.queryByRole("region", { name: /görselli galeri/ })).toBeNull();
    expect(document.querySelector("article img")).toBeNull();
  });

  it("kartın sağında yorum sütunu yoktur; sabit yükseklik ve gradient/fade yoktur", () => {
    mount(post({ body: "uzun ".repeat(400) }));
    const article = screen.getByRole("article");
    expect(article.className).not.toMatch(/h-\[|xl:grid|xl:absolute/);
    expect(article.innerHTML).not.toMatch(/bg-gradient|from-surface/);
    expect(screen.queryByRole("heading", { name: "Yorumlar" })).toBeNull();
    expect(screen.queryByRole("region", { name: "Yorum listesi" })).toBeNull();
  });

  it("uzun kelime ve kesintisiz karakter kartın dışına taşmaz (kırılır)", () => {
    mount(post({ body: "a".repeat(300) }));
    const text = screen.getByText("a".repeat(300));
    expect(text.className).toContain("break-words");
    expect(text.className).toMatch(/overflow-wrap:anywhere/);
    expect(screen.getByRole("article").className).toContain("min-w-0");
  });

  it("uzun metin temiz satır sınırıyla kesilir; 'Devamını gör' açar, 'Daha az göster' geri toplar", () => {
    overflow.value = true;
    mount(post({ body: "uzun metin ".repeat(200) }));
    const text = screen.getByText(/uzun metin/);
    expect(text.className).toContain("line-clamp-6");
    fireEvent.click(screen.getByRole("button", { name: "Devamını gör" }));
    expect(text.className).not.toContain("line-clamp-6");
    expect(screen.getByRole("button", { name: "Daha az göster" })).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(screen.getByRole("button", { name: "Daha az göster" }));
    expect(text.className).toContain("line-clamp-6");
  });

  it("kısa metinde 'Devamını gör' görünmez", () => {
    mount(post());
    expect(screen.queryByRole("button", { name: "Devamını gör" })).toBeNull();
  });
});

describe("Kart altı yorum bölümü", () => {
  it("kapalıyken yer kaplamaz ve yorumlar yüklenmez; yorum ikonu açar/kapatır", async () => {
    vi.mocked(api.fetchComments).mockResolvedValue({ items: [], nextCursor: null });
    mount(post());
    expect(api.fetchComments).not.toHaveBeenCalled();
    const toggle = screen.getByRole("button", { name: /Yorumları göster/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("heading", { name: "Yorumlar" })).toBeNull();

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const panel = document.getElementById(toggle.getAttribute("aria-controls") as string) as HTMLElement;
    expect(within(panel).getByRole("heading", { name: "Yorumlar" })).toBeInTheDocument();
    await waitFor(() => expect(api.fetchComments).toHaveBeenCalledTimes(1));
    // Yorum listesi ve form açılan bölümde; sayaç her zaman görünür ve yorum sınırı 250'dir.
    expect(within(panel).getByRole("textbox")).toHaveAttribute("maxLength", "1000");
    expect(within(panel).getByText("0/1000")).toBeInTheDocument();
    // Bölüm kartın altındadır (etkileşim satırından sonra).
    expect(follows(document.querySelector("footer") as Element, panel)).toBe(true);

    fireEvent.click(toggle);
    expect(document.getElementById(panel.id)).toBeNull();
    expect(screen.queryByRole("heading", { name: "Yorumlar" })).toBeNull();
  });

  it("yorum yazılınca sunucu yanıtı eklenir ve gönderinin yorum sayacı artar; dış kart yapısı değişmez", async () => {
    vi.mocked(api.fetchComments).mockResolvedValue({ items: [], nextCursor: null });
    vi.mocked(api.createComment).mockResolvedValue({
      id: "c1",
      postId: "p",
      authorId: ME,
      parentCommentId: null,
      body: "Merhaba",
      status: "VISIBLE",
      createdAt: "2026-11-07T10:00:00Z",
      updatedAt: "2026-11-07T10:00:00Z",
      replyCount: 0
    });
    const onChange = mount(post({ commentCount: 0 }));
    fireEvent.click(screen.getByRole("button", { name: /Yorumları göster/ }));
    const box = await screen.findByRole("textbox");
    fireEvent.change(box, { target: { value: "Merhaba" } });
    expect(screen.getByText("7/1000")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Gönder" }));
    await waitFor(() => expect(api.createComment).toHaveBeenCalledWith(expect.any(String), "Merhaba"));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ commentCount: 1 })));
    expect(await screen.findByText("Merhaba")).toBeInTheDocument();
    expect(screen.getByText("0/1000")).toBeInTheDocument();
  });
});
