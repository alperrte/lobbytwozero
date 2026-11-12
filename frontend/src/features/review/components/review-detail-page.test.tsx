import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { loadReviewResource, reviewApi, reviewMutations } from "../services/review-api";
import type { AuthorCard, ReviewLikeResponse, ReviewResource, ReviewResponse } from "../types/review";
import { ReviewDetailPage } from "./review-detail-page";

const mocks = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: "authenticated", me: { accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66" } }) }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(), usePathname: () => "/tr/incelemeler/x", useRouter: () => mocks }));
vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => mocks,
  Link: ({ href, children, ...props }: { href: string | { pathname: string; params?: Record<string, string> }; children: ReactNode }) => {
    const target = typeof href === "string" ? href : Object.entries(href.params ?? {}).reduce((path, [key, value]) => path.replace(`[${key}]`, value), href.pathname);
    return <a href={target} {...props}>{children}</a>;
  }
}));
vi.mock("next/image", () => ({ default: () => <span data-testid="cover-image" /> }));
vi.mock("../services/review-api", () => ({
  loadReviewResource: vi.fn(),
  reviewApi: { review: vi.fn() },
  reviewMutations: { like: vi.fn(), unlike: vi.fn(), updateReview: vi.fn(), deleteReview: vi.fn() },
  findOwnReview: vi.fn(),
  OWN_REVIEW_SCAN_PAGES: 20
}));

const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const reviewId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66";
const load = vi.mocked(loadReviewResource);
const reloadReview = vi.mocked(reviewApi.review);
const update = vi.mocked(reviewMutations.updateReview);
const remove = vi.mocked(reviewMutations.deleteReview);
const like = vi.mocked(reviewMutations.like);
const unlike = vi.mocked(reviewMutations.unlike);

const mine: ReviewResponse = { id: reviewId, gameId, body: "İlk metin\nikinci satır", authorUserId: "0198a3c2-0000-7d44-9a55-3f1c2b4d5e01", ownedByActor: true, status: "VISIBLE", version: 4, createdAt: "2026-11-01T12:00:00Z", updatedAt: "2026-11-01T12:00:00Z" };
const others: ReviewResponse = { ...mine, ownedByActor: false, body: "<img src=x onerror=alert(1)> yabancı" };

let review: ReviewResponse | Error;
let ownReport: unknown = null;
let authors: AuthorCard[] = [];
let likeState: ReviewLikeResponse | Error;

function wrap(children: ReactNode) {
  return <NextIntlClientProvider locale="tr" messages={tr}>{children}</NextIntlClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  review = mine;
  ownReport = null;
  authors = [];
  likeState = { reviewId, likeCount: 3, likedByActor: false };
  load.mockImplementation(async (resource: ReviewResource) => {
    if (resource.kind === "review") {
      if (review instanceof Error) throw review;
      return review;
    }
    if (resource.kind === "like") {
      if (likeState instanceof Error) throw likeState;
      return likeState;
    }
    if (resource.kind === "ownReport") return ownReport;
    if (resource.kind === "authorCards") return authors;
    if (resource.kind === "gameCard") return { gameId, title: "Test Oyunu", coverUrl: null };
    throw new Error("beklenmeyen kaynak");
  });
});

async function open() {
  render(wrap(<ReviewDetailPage reviewId={reviewId} />));
  await screen.findByRole("heading", { level: 1 });
}

describe("İnceleme detayı", () => {
  it("gerçek metni, oyunu ve geri bağlantılarını gösterir; yazar bilgisi uydurmaz", async () => {
    review = others;
    await open();
    expect(screen.getByRole("heading", { level: 1, name: "İnceleme" })).toBeVisible();
    expect(await screen.findByText("Test Oyunu")).toBeVisible();
    const links = screen.getAllByRole("link", { name: "Oyun incelemeleri" });
    expect(links[0]).toHaveAttribute("href", `/reviews/games/${gameId}`);
    expect(screen.queryByText(/yazar|avatar|profil/i)).toBeNull();
  });

  it("kullanıcı metnini HTML olarak çalıştırmaz", async () => {
    review = others;
    await open();
    const text = screen.getByText("<img src=x onerror=alert(1)> yabancı");
    expect(text.querySelector("img")).toBeNull();
    expect(document.querySelector("img[onerror]")).toBeNull();
  });

  it("başkasının incelemesinde düzenle/sil göstermez ve beğenmeye izin verir", async () => {
    review = others;
    await open();
    expect(screen.queryByRole("button", { name: "Düzenle" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Sil" })).toBeNull();
    expect(await screen.findByRole("button", { name: /Beğen/ })).toBeVisible();
    expect(screen.queryByText("Senin incelemen")).toBeNull();
  });

  it("başkasının incelemesinde yazarı User kartından gösterir; kartı yoksa 'Bir oyuncu' der ve bağlantı vermez", async () => {
    review = others;
    authors = [{ userId: others.authorUserId, username: "oyuncu", displayName: "Yazar Adı", avatarPath: null }];
    await open();
    expect(await screen.findByRole("link", { name: "Yazar Adı" })).toHaveAttribute("href", `/players/${others.authorUserId}`);
    expect(load.mock.calls.find(([resource]) => resource.kind === "authorCards")?.[0]).toEqual({ kind: "authorCards", userIds: [others.authorUserId] });
  });

  it("yazar kartı yoksa 'Bir oyuncu' gösterir; kendi incelemende yazar kartı istenmez", async () => {
    review = others;
    authors = [];
    await open();
    expect(await screen.findByText("Bir oyuncu")).toBeVisible();
    expect(screen.queryByRole("link", { name: "Bir oyuncu" })).toBeNull();
  });

  it("kendi incelemende yazar kartı istenmez", async () => {
    await open();
    await screen.findByRole("button", { name: "Düzenle" });
    expect(load.mock.calls.some(([resource]) => resource.kind === "authorCards")).toBe(false);
  });

  it("başkasının incelemesinde 'Raporla' sunar; kendi incelemende sunmaz", async () => {
    review = others;
    await open();
    expect(await screen.findByRole("button", { name: "Raporla" })).toBeVisible();
  });

  it("zaten raporlanmış incelemede mevcut raporu gösterir ve tekrar rapor düğmesi sunmaz", async () => {
    review = others;
    ownReport = { id: "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66", reviewId, reason: "SPAM", detail: null, status: "RECEIVED", createdAt: "2026-11-01T12:00:00Z" };
    await open();
    expect(await screen.findByText(/Raporladın ·/)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Raporla" })).toBeNull();
  });

  it("kendi incelemende rapor kaynağı hiç istenmez", async () => {
    await open();
    await screen.findByRole("button", { name: "Düzenle" });
    expect(screen.queryByRole("button", { name: "Raporla" })).toBeNull();
    expect(load.mock.calls.some(([resource]) => resource.kind === "ownReport")).toBe(false);
  });

  it("kendi incelemende düzenle/sil gösterir ama beğeni düğmesi yoktur", async () => {
    await open();
    expect(screen.getByRole("heading", { level: 1, name: "Senin incelemen" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Düzenle" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Sil" })).toBeVisible();
    expect(await screen.findByText(/kendi incelemeni beğenemezsin/)).toBeVisible();
    expect(screen.queryByRole("button", { name: /Beğen/ })).toBeNull();
  });

  it("beğeni durumunu sunucudan okur: beğenilmişse kaldırma sunar, sayıyı yanıttan günceller", async () => {
    review = others;
    likeState = { reviewId, likeCount: 8, likedByActor: true };
    unlike.mockResolvedValue({ reviewId, likeCount: 7, likedByActor: false });
    await open();
    const button = await screen.findByRole("button", { name: /Beğeniyi kaldır/ });
    expect(button).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(button);
    const after = await screen.findByRole("button", { name: /^Beğen/ });
    expect(after).toHaveAttribute("aria-pressed", "false");
    expect(within(after).getByText("7")).toBeVisible();
    expect(unlike).toHaveBeenCalledExactlyOnceWith(reviewId, expect.any(AbortSignal));
    expect(like).not.toHaveBeenCalled();
  });

  it("beğeni durumu okunamazsa yine de metin görünür ve tekrar dene sunulur", async () => {
    review = others;
    likeState = new ApiHttpError(503);
    await open();
    expect(await screen.findByText("Servis geçici olarak kullanılamıyor. Biraz sonra tekrar dene.")).toBeVisible();
    expect(screen.getByText(/yabancı/)).toBeVisible();
  });

  it("404'te bulunamadı durumunu gösterir (sahte içerik yok)", async () => {
    review = new ApiHttpError(404);
    render(wrap(<ReviewDetailPage reviewId={reviewId} />));
    expect(await screen.findByText("İnceleme bulunamadı")).toBeVisible();
    expect(screen.getByRole("link", { name: "İncelemeler" })).toHaveAttribute("href", "/reviews");
  });

  it("403'te yetki mesajı gösterir", async () => {
    review = new ApiHttpError(403);
    render(wrap(<ReviewDetailPage reviewId={reviewId} />));
    expect(await screen.findByText("Bu işlem için erişim iznin yok.")).toBeVisible();
  });
});

describe("Moderasyon durumu", () => {
  it("gizlenen incelemede durumu açıklar; düzenle gizlenir, sil kalır ve beğeni kaynağı istenmez", async () => {
    review = { ...mine, status: "HIDDEN" };
    await open();
    expect(await screen.findByText("Bu inceleme moderatörler tarafından gizlendi")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Düzenle" })).toBeNull();
    expect(screen.getByRole("button", { name: "Sil" })).toBeVisible();
    expect(load.mock.calls.some(([resource]) => resource.kind === "like")).toBe(false);
    expect(screen.queryByRole("button", { name: /Beğen/ })).toBeNull();
  });

  it("arşivlenen incelemede ne düzenleme ne silme sunar", async () => {
    review = { ...mine, status: "ARCHIVED" };
    await open();
    expect(await screen.findByText("Bu inceleme arşivlendi")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Düzenle" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Sil" })).toBeNull();
  });

  it("görünür incelemede moderasyon uyarısı yoktur", async () => {
    await open();
    expect(screen.queryByText(/moderatörler tarafından gizlendi|arşivlendi/)).toBeNull();
  });

  it("moderasyon kilidi hatasını yetki hatası değil açıklayıcı mesajla gösterir", async () => {
    review = { ...mine, status: "HIDDEN" };
    remove.mockRejectedValue(new ApiHttpError(403, { code: "REVIEW_MODERATED" }));
    await open();
    fireEvent.click(await screen.findByRole("button", { name: "Sil" }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "İncelemeyi sil" }));
    expect(await within(dialog).findByText("Bu inceleme moderasyon nedeniyle değiştirilemez.")).toBeVisible();
  });
});

describe("İnceleme düzenleme", () => {
  async function startEdit() {
    await open();
    fireEvent.click(screen.getByRole("button", { name: "Düzenle" }));
    return screen.getByLabelText("İnceleme metni") as HTMLTextAreaElement;
  }

  it("mevcut metinle açar ve gerçek version ile kaydeder", async () => {
    update.mockResolvedValue({ ...mine, body: "Yeni metin", version: 5, updatedAt: "2026-11-02T12:00:00Z" });
    const field = await startEdit();
    expect(field).toHaveValue("İlk metin\nikinci satır");
    fireEvent.change(field, { target: { value: "Yeni metin" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    expect(await screen.findByText("İncelemen güncellendi.")).toBeVisible();
    expect(update).toHaveBeenCalledExactlyOnceWith(reviewId, 4, "Yeni metin", expect.any(AbortSignal));
    expect(screen.getByText("Yeni metin")).toBeVisible();
    expect(screen.getByText(/Düzenlendi/)).toBeVisible();
    // Sonraki düzenleme güncellenmiş version ile gider.
    fireEvent.click(screen.getByRole("button", { name: "Düzenle" }));
    update.mockResolvedValue({ ...mine, body: "Üçüncü", version: 6 });
    fireEvent.change(screen.getByLabelText("İnceleme metni"), { target: { value: "Üçüncü" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    await waitFor(() => expect(update).toHaveBeenLastCalledWith(reviewId, 5, "Üçüncü", expect.any(AbortSignal)));
  });

  it("boş metinle kaydetmez", async () => {
    const field = await startEdit();
    fireEvent.change(field, { target: { value: "  " } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    expect(await screen.findByText("İnceleme boş olamaz.")).toBeVisible();
    expect(update).not.toHaveBeenCalled();
  });

  it("vazgeçince düzenlemeyi kapatır ve metni değiştirmez", async () => {
    await startEdit();
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    expect(screen.queryByLabelText("İnceleme metni")).toBeNull();
    expect(update).not.toHaveBeenCalled();
  });

  it("sürüm çakışmasında (409) taslağı korur, sessizce ezmez ve güncel hali yüklemeyi sunar", async () => {
    update.mockRejectedValue(new ApiHttpError(409));
    const field = await startEdit();
    fireEvent.change(field, { target: { value: "Benim taslağım" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    expect(await screen.findByText(/İnceleme başka bir yerde değişti/)).toBeVisible();
    expect(screen.getByText("İnceleme başka bir yerde değişmiş")).toBeVisible();
    expect(field).toHaveValue("Benim taslağım");
    expect(update).toHaveBeenCalledTimes(1);

    reloadReview.mockResolvedValue({ ...mine, body: "Başka sekmede yazılan", version: 7 });
    update.mockResolvedValue({ ...mine, body: "Benim taslağım", version: 8 });
    fireEvent.click(screen.getAllByRole("button", { name: "Güncel halini yükle" })[0]);
    expect(await screen.findByText("Başka sekmede yazılan")).toBeVisible();
    // Taslak hâlâ korunur; kullanıcı kaydetmeden önce güncel metni görür.
    expect(screen.getByLabelText("İnceleme metni")).toHaveValue("Benim taslağım");
    expect(update).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    await waitFor(() => expect(update).toHaveBeenLastCalledWith(reviewId, 7, "Benim taslağım", expect.any(AbortSignal)));
  });

  it("403'te sahiplik mesajı gösterir ve metni korur", async () => {
    update.mockRejectedValue(new ApiHttpError(403));
    const field = await startEdit();
    fireEvent.change(field, { target: { value: "Taslak" } });
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));
    expect(await screen.findByText("Bu işlemi yalnız incelemenin sahibi yapabilir.")).toBeVisible();
    expect(field).toHaveValue("Taslak");
  });
});

describe("İnceleme silme", () => {
  async function askDelete() {
    await open();
    fireEvent.click(screen.getByRole("button", { name: "Sil" }));
    return await screen.findByRole("dialog");
  }

  it("açıklayıcı onay ister; puan ve tavsiyenin korunduğunu söyler", async () => {
    const dialog = await askDelete();
    expect(within(dialog).getByText("İnceleme silinsin mi?")).toBeVisible();
    expect(within(dialog).getByText(/puan ve tavsiye korunur/)).toBeVisible();
    expect(within(dialog).getByRole("button", { name: "Vazgeç" })).toHaveFocus();
    expect(remove).not.toHaveBeenCalled();
  });

  it("vazgeçilirse silmez", async () => {
    const dialog = await askDelete();
    fireEvent.click(within(dialog).getByRole("button", { name: "Vazgeç" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(remove).not.toHaveBeenCalled();
  });

  it("onayda gerçek version ile siler ve oyunun inceleme sayfasına yönlendirir", async () => {
    remove.mockResolvedValue(undefined);
    const dialog = await askDelete();
    fireEvent.click(within(dialog).getByRole("button", { name: "İncelemeyi sil" }));
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith({ pathname: "/reviews/games/[gameId]", params: { gameId } }));
    expect(remove).toHaveBeenCalledExactlyOnceWith(reviewId, 4, expect.any(AbortSignal));
  });

  it("sürüm çakışmasında silmez, nedenini açıklar ve güncel hali yüklemeyi sunar", async () => {
    remove.mockRejectedValue(new ApiHttpError(409));
    const dialog = await askDelete();
    fireEvent.click(within(dialog).getByRole("button", { name: "İncelemeyi sil" }));
    expect(await within(dialog).findByText(/İnceleme başka bir yerde değişti/)).toBeVisible();
    expect(mocks.replace).not.toHaveBeenCalled();
    reloadReview.mockResolvedValue({ ...mine, version: 9, body: "Değişmiş" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Güncel halini yükle" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(await screen.findByText("Değişmiş")).toBeVisible();
  });

  it("silme hatasında yönlendirmez", async () => {
    remove.mockRejectedValue(new ApiHttpError(503));
    const dialog = await askDelete();
    fireEvent.click(within(dialog).getByRole("button", { name: "İncelemeyi sil" }));
    expect(await within(dialog).findByText("Servis geçici olarak kullanılamıyor. Biraz sonra tekrar dene.")).toBeVisible();
    expect(mocks.replace).not.toHaveBeenCalled();
  });
});
