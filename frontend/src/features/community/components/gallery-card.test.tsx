import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import * as api from "../services/community-api";
import type { PostView } from "../types/community.types";
import { PostGalleryCard } from "./gallery-card";
import type { Lookups } from "./post-parts";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));
vi.mock("../services/community-api", () => ({
  deletePost: vi.fn(),
  imageSrc: (image: { id: string }) => `http://gateway.test/img/${image.id}`
}));

const ME = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";
const lookups: Lookups = {
  author: (id) => (id === OTHER ? { userId: OTHER, username: "kaan", displayName: "Kaan A.", avatarUrl: null } : null),
  game: () => null
};

const image = (n: number, postId = "p1") => ({ id: `i${n}`, postId, mimeType: "image/png" as const, sizeBytes: 1, width: 800, height: 600, sortOrder: n, contentUrl: "/x", createdAt: "2026-11-07T10:00:00Z" });

function post(patch: Partial<PostView> = {}): PostView {
  return {
    id: "p1",
    authorId: ME,
    gameId: null,
    forumId: null,
    category: "GENERAL",
    body: "Yeni haritada orta kontrol çok iyi.",
    status: "VISIBLE",
    createdAt: new Date(Date.now() - 3600_000).toISOString(),
    updatedAt: new Date(Date.now() - 3600_000).toISOString(),
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

function mount(props: { post?: PostView; view?: "posts" | "reposts"; onRemoved?: (id: string) => void } = {}) {
  const onRemoved = props.onRemoved ?? vi.fn();
  render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PostGalleryCard post={props.post ?? post()} view={props.view ?? "posts"} lookups={lookups} onRemoved={onRemoved} />
    </NextIntlClientProvider>
  );
  return { onRemoved };
}

const openMenu = () => fireEvent.click(screen.getByRole("button", { name: "Gönderi işlemleri" }));

beforeEach(() => {
  vi.clearAllMocks();
});

describe("PostGalleryCard", () => {
  it("görselsiz kartta boş fotoğraf alanı ya da sahte kapak yoktur; metin içerik bağlantısıdır", () => {
    mount();
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.queryByRole("region")).toBeNull();
    const link = screen.getByRole("link", { name: /Yeni haritada orta kontrol çok iyi\./ });
    expect(link).toHaveAttribute("href", expect.stringContaining("p1"));
  });

  it("görselli kartta üstte carousel, altında özet bağlantısı bulunur; çoklu görselde sayaç görünür", () => {
    mount({ post: post({ images: [image(0), image(1), image(2)] }) });
    expect(screen.getByRole("region", { name: "3 görselli galeri" })).toBeInTheDocument();
    expect(screen.getByText("1/3")).toBeInTheDocument();
    const summary = screen.getByRole("heading", { level: 3 });
    expect(within(summary).getByRole("link")).toHaveTextContent("Yeni haritada orta kontrol çok iyi.");
  });

  it("başlıklı (oyun içeriği) kartta başlık öne çıkar ve tür rozeti görünür", () => {
    mount({ post: post({ title: "Başlangıç rehberi", contentType: "GUIDES", gameId: "g1" }) });
    expect(screen.getByText("Başlangıç rehberi")).toBeInTheDocument();
    expect(screen.getByText("Rehber")).toBeInTheDocument();
    expect(screen.getByText("Bağlı oyun")).toBeInTheDocument();
  });

  it("etkileşim sayıları yalnız API'den gelen değerlerdir ve erişilebilir adlıdır", () => {
    mount();
    const counts = screen.getByText("Beğeni").closest("dl") as HTMLElement;
    expect(within(counts).getByText("4")).toBeInTheDocument();
    expect(within(counts).getByText("2")).toBeInTheDocument();
  });

  it("hiçbir etkileşimli öğe başka bir bağlantı/düğmenin içinde değildir", () => {
    mount({ post: post({ images: [image(0), image(1)] }) });
    openMenu();
    const nested = [...document.querySelectorAll("article a, article button")].filter((element) => element.parentElement?.closest("a, button"));
    expect(nested).toHaveLength(0);
  });

  it("menü: düğme aria-haspopup/expanded taşır, açılınca ilk öğeye odaklanır, oklar gezinir, Escape kapatıp odağı geri verir", async () => {
    mount();
    const trigger = screen.getByRole("button", { name: "Gönderi işlemleri" });
    expect(trigger).toHaveAttribute("aria-haspopup", "menu");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    fireEvent.keyDown(trigger, { key: "ArrowDown" });
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    const items = await screen.findAllByRole("menuitem");
    expect(items.map((item) => item.textContent)).toEqual(["Düzenle", "Sil"]);
    await waitFor(() => expect(items[0]).toHaveFocus());
    fireEvent.keyDown(screen.getByRole("menu"), { key: "ArrowDown" });
    expect(items[1]).toHaveFocus();
    fireEvent.keyDown(document, { key: "Escape" });
    await waitFor(() => expect(trigger).toHaveAttribute("aria-expanded", "false"));
    expect(trigger).toHaveFocus();
  });

  it("menüdeki Düzenle bağlantısı düzenleme moduyla detayı açar", () => {
    mount();
    openMenu();
    expect(screen.getByRole("menuitem", { name: "Düzenle" })).toHaveAttribute("href", expect.stringContaining("edit"));
  });

  it("gizlenmiş gönderide yalnız sil vardır", () => {
    mount({ post: post({ status: "HIDDEN" }) });
    openMenu();
    expect(screen.queryByRole("menuitem", { name: "Düzenle" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Sil" })).toBeInTheDocument();
    expect(screen.getByText("Gizlendi")).toBeInTheDocument();
  });

  it("silme onay ister; hata kartı kaybettirmez, başarıda liste bilgilendirilir", async () => {
    vi.mocked(api.deletePost).mockRejectedValueOnce(new ApiHttpError(500)).mockResolvedValueOnce(undefined);
    const { onRemoved } = mount();
    openMenu();
    fireEvent.click(screen.getByRole("menuitem", { name: "Sil" }));
    const dialog = await screen.findByRole("dialog");
    expect(api.deletePost).not.toHaveBeenCalled();

    fireEvent.click(within(dialog).getByRole("button", { name: "Sil" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Beklenmeyen bir sunucu hatası");
    expect(onRemoved).not.toHaveBeenCalled();
    expect(screen.getByRole("article")).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole("button", { name: "Sil" }));
    await waitFor(() => expect(onRemoved).toHaveBeenCalledWith("p1"));
    expect(api.deletePost).toHaveBeenCalledTimes(2);
  });

  it("yeniden paylaşım kartı: orijinal yazar atfı korunur, not gösterilir, 'geri al' vardır, düzenle yoktur", async () => {
    vi.mocked(api.deletePost).mockResolvedValueOnce(undefined);
    const reposted = post({
      id: "r1",
      body: "Bunu kaçırmayın.",
      repostOf: { postId: "o1", available: true, authorId: OTHER, gameId: null, category: "GENERAL", body: "Orijinal gönderi metni", createdAt: "2026-11-06T10:00:00Z", images: [] }
    });
    const { onRemoved } = mount({ post: reposted, view: "reposts" });
    expect(screen.getByText("Kaan A.")).toBeInTheDocument();
    expect(screen.getByText("Orijinal yazar")).toBeInTheDocument();
    expect(screen.getByText("“Bunu kaçırmayın.”")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Orijinal gönderi metni/ })).toBeInTheDocument();
    openMenu();
    expect(screen.queryByRole("menuitem", { name: "Düzenle" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Orijinali aç" })).toHaveAttribute("href", expect.stringContaining("o1"));
    fireEvent.click(screen.getByRole("menuitem", { name: "Yeniden paylaşımı geri al" }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("orijinal gönderi etkilenmez");
    fireEvent.click(within(dialog).getByRole("button", { name: "Geri al" }));
    await waitFor(() => expect(onRemoved).toHaveBeenCalledWith("r1"));
  });

  it("orijinali artık görünmeyen yeniden paylaşımda dürüst bir yer tutucu gösterilir, yazar uydurulmaz", () => {
    mount({ post: post({ id: "r2", body: null, repostOf: { postId: "o2", available: false } }), view: "reposts" });
    expect(screen.getByText("Orijinal gönderi artık görüntülenemiyor.")).toBeInTheDocument();
    expect(screen.queryByText("Orijinal yazar")).toBeNull();
  });
});
