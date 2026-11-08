import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Viewer } from "../hooks/use-viewer";
import * as api from "../services/community-api";
import type { CommentView } from "../types/community.types";
import { CommentsPane } from "./comments-pane";

const media = vi.hoisted(() => ({ wide: true }));
const view = vi.hoisted(() => ({ seen: true }));
vi.mock("../hooks/use-media-query", () => ({ useMediaQuery: () => media.wide }));
vi.mock("../hooks/use-in-view", () => ({ useInView: () => view.seen }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));
vi.mock("next/navigation", () => ({ usePathname: () => "/tr/topluluk" }));
vi.mock("../services/community-api", () => ({
  fetchComments: vi.fn(),
  fetchReplies: vi.fn(),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
  reportComment: vi.fn(),
  fetchAuthorCards: vi.fn().mockResolvedValue([])
}));

const ME = "11111111-1111-4111-8111-111111111111";
const OTHER = "22222222-2222-4222-8222-222222222222";
const member: Viewer = { status: "authenticated", accountId: ME, canModerate: false };
const guest: Viewer = { status: "guest", accountId: null, canModerate: false };

function comment(id: string, patch: Partial<CommentView> = {}): CommentView {
  return {
    id,
    postId: "p",
    authorId: OTHER,
    parentCommentId: null,
    body: `Yorum ${id}`,
    status: "VISIBLE",
    createdAt: "2026-11-07T10:00:00Z",
    updatedAt: "2026-11-07T10:00:00Z",
    replyCount: 0,
    ...patch
  };
}

function mount(viewer: Viewer, commentCount: number, onCountChange = vi.fn()) {
  render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <CommentsPane postId="p" commentCount={commentCount} viewer={viewer} onCountChange={onCountChange} />
    </NextIntlClientProvider>
  );
  return onCountChange;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.fetchAuthorCards).mockResolvedValue([]);
  media.wide = true;
  view.seen = true;
});

describe("Kart içi yorum bölmesi", () => {
  it("yorumu olmayan gönderi için hiç istek atmaz; küçük boş durum ve yazma alanı gösterir", () => {
    mount(member, 0);
    expect(screen.getByText("Henüz yorum yok.")).toBeInTheDocument();
    expect(screen.getByRole("textbox")).toBeInTheDocument();
    expect(api.fetchComments).not.toHaveBeenCalled();
  });

  it("geniş ekranda kart görünür olunca küçük bir sayfa yükler; liste kaydırılabilir bölgedir, yazma alanı altındadır", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [comment("a"), comment("b")], nextCursor: null });
    mount(member, 2);
    expect(await screen.findByText("Yorum a")).toBeInTheDocument();
    expect(api.fetchComments).toHaveBeenCalledTimes(1);
    expect(api.fetchComments).toHaveBeenCalledWith("p", null, expect.anything(), 5);
    const region = screen.getByRole("region", { name: "Yorum listesi" });
    expect(region.className).toContain("overflow-y-auto");
    const composer = screen.getByRole("textbox");
    expect(region.compareDocumentPosition(composer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("kart henüz görünür değilken yorumlar yüklenmez", () => {
    view.seen = false;
    mount(member, 3);
    expect(api.fetchComments).not.toHaveBeenCalled();
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("dar ekranda bölüm kapalı gelir ve istek atılmaz; açılınca yüklenir, tekrar kapatılabilir", async () => {
    media.wide = false;
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [comment("a")], nextCursor: null });
    mount(member, 1);
    const toggle = screen.getByRole("button", { name: /Yorumlar/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("textbox")).toBeNull();
    expect(api.fetchComments).not.toHaveBeenCalled();

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(await screen.findByText("Yorum a")).toBeInTheDocument();
    expect(api.fetchComments).toHaveBeenCalledTimes(1);

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Yorum a")).toBeNull();
  });

  it("sonraki sayfa yalnız 'Daha fazla' ile imleçle istenir", async () => {
    vi.mocked(api.fetchComments)
      .mockResolvedValueOnce({ items: [comment("a")], nextCursor: "c1" })
      .mockResolvedValueOnce({ items: [comment("b")], nextCursor: null });
    mount(member, 2);
    await screen.findByText("Yorum a");
    expect(api.fetchComments).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: /daha fazla/i }));
    expect(await screen.findByText("Yorum b")).toBeInTheDocument();
    expect(api.fetchComments).toHaveBeenLastCalledWith("p", "c1", expect.anything(), 5);
  });

  it("misafire yazma alanı yerine giriş çağrısı gösterir", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [comment("a")], nextCursor: null });
    mount(guest, 1);
    await screen.findByText("Yorum a");
    expect(screen.queryByRole("textbox")).toBeNull();
    expect(screen.getByRole("button", { name: "Yorum yazmak için giriş yap" })).toBeInTheDocument();
  });

  it("karakter sayacı her zaman görünür: boşken 0/2000, yazarken güncel, gönderimden sonra yeniden 0/2000", async () => {
    vi.mocked(api.createComment).mockResolvedValueOnce(comment("new", { authorId: ME, body: "Selam" }));
    mount(member, 0);
    const counter = screen.getByText("0/2000");
    expect(counter.className).toContain("h-5");
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Selam" } });
    expect(screen.getByText("5/2000")).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Gönder" }));
    });
    await waitFor(() => expect(screen.getByText("0/2000")).toBeInTheDocument());
  });

  it("yorum yazılınca sunucu yanıtı yüklü listeye eklenir ve sayaç artar", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [comment("a")], nextCursor: null });
    vi.mocked(api.createComment).mockResolvedValueOnce(comment("new", { authorId: ME, body: "Merhaba" }));
    const onCount = mount(member, 1);
    await screen.findByText("Yorum a");
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "  Merhaba  " } });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Gönder" }));
    });
    await waitFor(() => expect(screen.getByText("Merhaba")).toBeInTheDocument());
    expect(api.createComment).toHaveBeenCalledWith("p", "Merhaba");
    expect(onCount).toHaveBeenCalledWith(1);
  });
});
