import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Viewer } from "../hooks/use-viewer";
import * as api from "../services/community-api";
import type { CommentView } from "../types/community.types";
import { CommentsSection } from "./comments-section";

const push = vi.fn();
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ push }),
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));
vi.mock("next/navigation", () => ({ usePathname: () => "/tr/topluluk/gonderi/p" }));
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

function mount(viewer: Viewer, onCountChange = vi.fn()) {
  render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <CommentsSection postId="p" viewer={viewer} onCountChange={onCountChange} />
    </NextIntlClientProvider>
  );
  return onCountChange;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.fetchAuthorCards).mockResolvedValue([]);
});

describe("Community yorumları", () => {
  it("yorum yokken dürüst boş durum gösterir", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [], nextCursor: null });
    mount(member);
    expect(await screen.findByText("Henüz yorum yok")).toBeInTheDocument();
  });

  it("misafire yazma alanı yerine giriş çağrısı gösterir; tıklayınca girişe götürür", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [comment("a")], nextCursor: null });
    mount(guest);
    expect(await screen.findByText("Yorum a")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Yorum yazmak için giriş yap" }));
    expect(push).toHaveBeenCalledWith({ pathname: "/login", query: { next: "/tr/topluluk/gonderi/p" } });
  });

  it("boş yorum gönderilmez; geçerli yorum sunucu yanıtıyla listeye eklenir ve sayaç artar", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [], nextCursor: null });
    vi.mocked(api.createComment).mockResolvedValueOnce(comment("new", { authorId: ME, body: "Merhaba" }));
    const onCount = mount(member);
    await screen.findByText("Henüz yorum yok");

    fireEvent.click(screen.getByRole("button", { name: "Gönder" }));
    expect(api.createComment).not.toHaveBeenCalled();
    expect(screen.getByText("Bu alan zorunlu.")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Yorum yaz"), { target: { value: "  Merhaba  " } });
    fireEvent.click(screen.getByRole("button", { name: "Gönder" }));
    expect(await screen.findByText("Merhaba")).toBeInTheDocument();
    expect(api.createComment).toHaveBeenCalledWith("p", "Merhaba");
    expect(onCount).toHaveBeenCalledWith(1);
  });

  it("başarısız yorum listeye eklenmez, metni korur ve hata gösterir", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [], nextCursor: null });
    vi.mocked(api.createComment).mockRejectedValueOnce(new ApiHttpError(429, { retryAfterSeconds: 600 }));
    const onCount = mount(member);
    await screen.findByText("Henüz yorum yok");
    fireEvent.change(screen.getByLabelText("Yorum yaz"), { target: { value: "Selam" } });
    fireEvent.click(screen.getByRole("button", { name: "Gönder" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Çok fazla deneme yaptın. Yaklaşık 10 dk sonra tekrar dene.");
    expect(screen.getByLabelText("Yorum yaz")).toHaveValue("Selam");
    expect(onCount).not.toHaveBeenCalled();
  });

  it("silinmiş yorum yer tutucu olarak gösterilir ve yazar/içerik sızdırılmaz", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({
      items: [comment("d", { status: "DELETED", body: null, authorId: null, replyCount: 1 })],
      nextCursor: null
    });
    mount(member);
    expect(await screen.findByText("Bu yorum silindi.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Yanıtla" })).toBeNull();
  });

  it("yanıtlar istenince yüklenir; sahip kendi yanıtını silince sayaç düşer", async () => {
    vi.mocked(api.fetchComments).mockResolvedValueOnce({ items: [comment("top", { replyCount: 1 })], nextCursor: null });
    vi.mocked(api.fetchReplies).mockResolvedValueOnce({
      items: [comment("r1", { authorId: ME, parentCommentId: "top", body: "Benim yanıtım" })],
      nextCursor: null
    });
    vi.mocked(api.deleteComment).mockResolvedValueOnce(undefined);
    const onCount = mount(member);
    await screen.findByText("Yorum top");
    expect(api.fetchReplies).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "1 yanıtı göster" }));
    expect(await screen.findByText("Benim yanıtım")).toBeInTheDocument();

    const reply = screen.getByText("Benim yanıtım").closest("li") as HTMLElement;
    fireEvent.click(within(reply).getByRole("button", { name: "Sil" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "Sil" }));
    await waitFor(() => expect(screen.queryByText("Benim yanıtım")).toBeNull());
    expect(onCount).toHaveBeenCalledWith(-1);
  });
});
