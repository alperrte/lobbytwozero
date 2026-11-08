import { act, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import * as api from "../services/community-api";
import type { MyPostsView } from "../services/my-posts-query";
import { invalidateMyPosts } from "../services/my-posts-snapshot";
import { emptyFilters, type PostView } from "../types/community.types";
import { useMyPosts } from "./use-my-posts";

vi.mock("../services/community-api", () => ({ fetchPostPage: vi.fn() }));
const fetchPage = vi.mocked(api.fetchPostPage);

const ME = "11111111-1111-4111-8111-111111111111";
const original = (n: number): PostView => ({ id: `p${n}`, authorId: ME, repostOf: null, images: [] }) as unknown as PostView;
const repost = (n: number): PostView => ({ id: `r${n}`, authorId: ME, repostOf: { postId: `o${n}`, available: true }, images: [] }) as unknown as PostView;
const range = (count: number, make: (n: number) => PostView, from = 1) => Array.from({ length: count }, (_, index) => make(from + index));

const probe: { current: ReturnType<typeof useMyPosts> | null } = { current: null };
const latest = new Proxy({} as ReturnType<typeof useMyPosts>, {
  get: (_, property) => (probe.current as unknown as Record<string | symbol, unknown>)[property]
});
function Harness({ view = "posts", userId = ME }: { view?: MyPostsView; userId?: string | null }) {
  const state = useMyPosts({ userId, view, filters: emptyFilters });
  useEffect(() => {
    probe.current = state;
  });
  return <output data-testid="ids">{state.items.map((item) => item.id).join(",")}</output>;
}
const ids = () => screen.getByTestId("ids").textContent;

beforeEach(() => {
  invalidateMyPosts();
  fetchPage.mockReset();
  window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
});
afterEach(() => invalidateMyPosts());

describe("useMyPosts (karma imleçli liste → görünüm)", () => {
  it("gönderilerim yalnız kendi oluşturduklarımı gösterir; yeniden paylaşımlar listede yer almaz", async () => {
    fetchPage.mockResolvedValueOnce({ items: [original(1), repost(1), original(2)], nextCursor: null });
    render(<Harness />);
    await waitFor(() => expect(ids()).toBe("p1,p2"));
    expect(latest.hasMore).toBe(false);
  });

  it("görünüme uyan kayıt çıkmayan sayfalarda imleci sürdürür (eksik sonucu tam gibi sunmaz)", async () => {
    fetchPage
      .mockResolvedValueOnce({ items: range(24, repost), nextCursor: "c1" })
      .mockResolvedValueOnce({ items: range(24, repost, 100), nextCursor: "c2" })
      .mockResolvedValueOnce({ items: [original(7), original(8)], nextCursor: null });
    render(<Harness />);
    await waitFor(() => expect(ids()).toBe("p7,p8"));
    expect(fetchPage).toHaveBeenCalledTimes(3);
    expect(fetchPage.mock.calls.map((call) => new URLSearchParams(call[0]).get("cursor"))).toEqual([null, "c1", "c2"]);
    expect(latest.hasMore).toBe(false);
  });

  it("yeterli kayıt birikince durur ve hasMore sunucu imlecini yansıtır; 'daha fazla' ekler, yinelenenleri eler", async () => {
    fetchPage
      .mockResolvedValueOnce({ items: range(14, original), nextCursor: "c1" })
      .mockResolvedValueOnce({ items: [original(14), original(15)], nextCursor: null });
    render(<Harness />);
    await waitFor(() => expect(latest.items).toHaveLength(14));
    expect(fetchPage).toHaveBeenCalledTimes(1);
    expect(latest.hasMore).toBe(true);
    act(() => latest.loadMore());
    await waitFor(() => expect(latest.items).toHaveLength(15));
    expect(latest.hasMore).toBe(false);
  });

  it("yeniden paylaşımlar görünümü yalnız yeniden paylaşımları toplar", async () => {
    fetchPage.mockResolvedValueOnce({ items: [original(1), repost(1), repost(2)], nextCursor: null });
    render(<Harness view="reposts" />);
    await waitFor(() => expect(ids()).toBe("r1,r2"));
  });

  it("boş ve biten liste 'boş' sayılır; imleç sürerken boş sonuç boş denmez", async () => {
    fetchPage.mockResolvedValueOnce({ items: [], nextCursor: null });
    render(<Harness />);
    await waitFor(() => expect(latest.loading).toBe(false));
    expect(latest.items).toHaveLength(0);
    expect(latest.hasMore).toBe(false);
  });

  it("silme listeyi yerinde günceller; kayıt anlık görüntüye yazılır ve aynı süzgeçle yeniden açılınca ağa gitmeden gelir", async () => {
    fetchPage.mockResolvedValueOnce({ items: [original(1), original(2), original(3)], nextCursor: null });
    const first = render(<Harness />);
    await waitFor(() => expect(ids()).toBe("p1,p2,p3"));
    act(() => latest.remove("p2"));
    expect(ids()).toBe("p1,p3");
    first.unmount();

    render(<Harness />);
    expect(ids()).toBe("p1,p3");
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it("veri değiştiren istek sonrası anlık görüntü geçersizdir: yeniden açılınca sunucudan okunur", async () => {
    fetchPage.mockResolvedValueOnce({ items: [original(1)], nextCursor: null });
    const first = render(<Harness />);
    await waitFor(() => expect(ids()).toBe("p1"));
    first.unmount();
    invalidateMyPosts();
    fetchPage.mockResolvedValueOnce({ items: [original(1), original(9)], nextCursor: null });
    render(<Harness />);
    await waitFor(() => expect(ids()).toBe("p1,p9"));
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it("hata durumunu bildirir ve yeniden denemede yeniden yükler", async () => {
    fetchPage.mockRejectedValueOnce(new ApiHttpError(500)).mockResolvedValueOnce({ items: [original(1)], nextCursor: null });
    render(<Harness />);
    await waitFor(() => expect(latest.error).toBeInstanceOf(ApiHttpError));
    act(() => latest.reload());
    await waitFor(() => expect(ids()).toBe("p1"));
    expect(latest.error).toBeUndefined();
  });

  it("oturum bilinmiyorsa (kullanıcı kimliği yok) istek atmaz", () => {
    render(<Harness userId={null} />);
    expect(fetchPage).not.toHaveBeenCalled();
    expect(latest.loading).toBe(true);
  });
});
