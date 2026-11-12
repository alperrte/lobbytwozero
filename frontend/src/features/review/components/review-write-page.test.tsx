import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { loadReviewResource, reviewMutations } from "../services/review-api";
import type { ReviewResource, ReviewResponse } from "../types/review";
import { ReviewWritePage } from "./review-write-page";

const mocks = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: "authenticated", me: { accountId: "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66" } }) }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(), usePathname: () => "/tr/incelemeler/oyun/x/yaz", useRouter: () => mocks }));
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
  reviewMutations: { createReview: vi.fn() },
  findOwnReview: vi.fn(),
  OWN_REVIEW_SCAN_PAGES: 20
}));

const gameId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
const reviewId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66";
const created: ReviewResponse = { id: reviewId, gameId, body: "Güzel", authorUserId: "0198a3c2-0000-7d44-9a55-3f1c2b4d5e01", ownedByActor: true, status: "VISIBLE", version: 0, createdAt: "2026-11-01T12:00:00Z", updatedAt: "2026-11-01T12:00:00Z" };
const create = vi.mocked(reviewMutations.createReview);
let existing: ReviewResponse | null = null;
const load = vi.mocked(loadReviewResource);

function wrap(children: ReactNode) {
  return <NextIntlClientProvider locale="tr" messages={tr}>{children}</NextIntlClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  existing = null;
  load.mockImplementation(async (resource: ReviewResource) => {
    if (resource.kind === "gameCard") return { gameId, title: "Test Oyunu", coverUrl: null };
    if (resource.kind === "ownReview") return existing;
    if (resource.kind === "ratingSummary") return { gameId, averageRating: 7.5, ratingCount: 12 };
    throw new Error("beklenmeyen kaynak");
  });
});
afterEach(() => {
  vi.useRealTimers();
});

async function open() {
  render(wrap(<ReviewWritePage gameId={gameId} />));
  await screen.findByRole("heading", { level: 1, name: "İnceleme yaz" });
  // Başlık hemen çizilir, form ise "kendi incelemem var mı" isteği dönene kadar gizlidir; alanı da beklemek gerekir.
  return (await screen.findByLabelText("İnceleme metni")) as HTMLTextAreaElement;
}

describe("İnceleme yazma", () => {
  it("oyun özetini ve puan/tavsiyenin ayrı olduğunu açıklar", async () => {
    await open();
    expect(await screen.findByText("Test Oyunu")).toBeVisible();
    expect(await screen.findByText("LTZ puanı 7,5 / 10 · 12 puan")).toBeVisible();
    expect(screen.getByText(/Puanın ve tavsiyen bu metinden bağımsızdır/)).toBeVisible();
    expect(screen.getByText(/yalnız metin kalkar/)).toBeVisible();
    // Puan veya tavsiye alanı bu formda YOKTUR.
    expect(screen.queryByRole("radiogroup")).toBeNull();
  });

  it("karakter sayacını ve sınırı gösterir", async () => {
    const field = await open();
    expect(screen.getByText("0 / 5000 karakter")).toBeVisible();
    fireEvent.change(field, { target: { value: "Merhaba" } });
    expect(screen.getByText("7 / 5000 karakter")).toBeVisible();
    fireEvent.change(field, { target: { value: "a".repeat(5001) } });
    expect(screen.getByText("5001 / 5000 karakter")).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent("en fazla 5000 karakter");
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(create).not.toHaveBeenCalled();
  });

  it("boş metni göndermez, hatayı alana bağlar ve odaklar", async () => {
    const field = await open();
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(create).not.toHaveBeenCalled();
    expect(await screen.findByText("İnceleme boş olamaz.")).toBeVisible();
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(field.getAttribute("aria-describedby")).toContain(screen.getByText("İnceleme boş olamaz.").id);
    expect(field).toHaveFocus();
  });

  it("yalnız boşluk metnini boş sayar", async () => {
    const field = await open();
    fireEvent.change(field, { target: { value: "   \n  " } });
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(await screen.findByText("İnceleme boş olamaz.")).toBeVisible();
    expect(create).not.toHaveBeenCalled();
  });

  it("başarıda gerçek inceleme detayına yönlendirir", async () => {
    create.mockResolvedValue(created);
    const field = await open();
    fireEvent.change(field, { target: { value: "  Güzel  " } });
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    await waitFor(() => expect(mocks.push).toHaveBeenCalledWith({ pathname: "/reviews/[reviewId]", params: { reviewId } }));
    expect(create).toHaveBeenCalledExactlyOnceWith(gameId, "  Güzel  ", expect.any(AbortSignal));
  });

  it("gönderim sırasında düğme kapanır; çift gönderim yapılmaz", async () => {
    let finish: (value: ReviewResponse) => void = () => undefined;
    create.mockImplementation(() => new Promise<ReviewResponse>((resolve) => { finish = resolve; }));
    const field = await open();
    fireEvent.change(field, { target: { value: "Metin" } });
    const submit = screen.getByRole("button", { name: "İncelemeyi yayınla" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    const busy = await screen.findByRole("button", { name: "Kaydediliyor…" });
    expect(busy).toBeDisabled();
    expect(field).toBeDisabled();
    expect(create).toHaveBeenCalledTimes(1);
    await act(async () => finish(created));
  });

  it("hata durumunda girilen metni korur", async () => {
    create.mockRejectedValue(new ApiHttpError(503));
    const field = await open();
    fireEvent.change(field, { target: { value: "Uzun uzun yazdığım metin" } });
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(await screen.findByText("Servis geçici olarak kullanılamıyor. Biraz sonra tekrar dene.")).toBeVisible();
    expect(field).toHaveValue("Uzun uzun yazdığım metin");
    expect(mocks.push).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "İncelemeyi yayınla" })).toBeEnabled();
  });

  it("ağ hatasında sonucun bilinmediğini söyler ve OTOMATİK tekrar denemez", async () => {
    create.mockRejectedValue(new TypeError("Failed to fetch"));
    const field = await open();
    fireEvent.change(field, { target: { value: "Metin" } });
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(await screen.findByText(/tekrar göndermeden önce güncel durumu kontrol et/)).toBeVisible();
    expect(create).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("button", { name: "Tekrar dene" })).toBeNull();
  });

  it("zaten incelemen varsa formu açmaz, düzenleme bağlantısı sunar", async () => {
    existing = created;
    render(wrap(<ReviewWritePage gameId={gameId} />));
    expect(await screen.findByText("Bu oyun için zaten bir incelemen var.")).toBeVisible();
    expect(screen.getByRole("link", { name: "İncelemeni aç ve düzenle" })).toHaveAttribute("href", `/reviews/${reviewId}`);
    expect(screen.queryByLabelText("İnceleme metni")).toBeNull();
    expect(create).not.toHaveBeenCalled();
  });

  it("yarışta 409 gelirse mevcut incelemeyi sunucudan okuyup bağlantı gösterir; taslağı korur", async () => {
    create.mockRejectedValue(new ApiHttpError(409));
    const field = await open();
    fireEvent.change(field, { target: { value: "İkinci inceleme" } });
    existing = created;
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    const link = await screen.findByRole("link", { name: "İncelemeni aç ve düzenle" });
    expect(link).toHaveAttribute("href", `/reviews/${reviewId}`);
    expect(screen.getByText(/Yazdığın taslak bu sayfada duruyor/)).toBeVisible();
    expect(create).toHaveBeenCalledTimes(1);
  });

  it("409 gelip inceleme bulunamazsa formu ve hatayı korur", async () => {
    create.mockRejectedValue(new ApiHttpError(409));
    const field = await open();
    fireEvent.change(field, { target: { value: "Metin" } });
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Bu oyun için zaten bir incelemen var.");
    expect(field).toHaveValue("Metin");
  });

  it("429'da Retry-After geri sayımı gösterir, gönderimi kapatır ve metni korur", async () => {
    create.mockRejectedValue(new ApiHttpError(429, { retryAfterSeconds: 45 }));
    const field = await open();
    fireEvent.change(field, { target: { value: "Metin" } });
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(await screen.findByText(/45 saniye sonra tekrar deneyebilirsin/)).toBeVisible();
    expect(screen.getByRole("button", { name: "İncelemeyi yayınla" })).toBeDisabled();
    expect(field).toHaveValue("Metin");
    expect(create).toHaveBeenCalledTimes(1);
  });

  it("geri sayım bitince gönderim yeniden açılır", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    create.mockRejectedValueOnce(new ApiHttpError(429, { retryAfterSeconds: 2 }));
    const field = await open();
    fireEvent.change(field, { target: { value: "Metin" } });
    fireEvent.click(screen.getByRole("button", { name: "İncelemeyi yayınla" }));
    expect(await screen.findByText(/2 saniye sonra/)).toBeVisible();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(screen.getByRole("button", { name: "İncelemeyi yayınla" })).toBeEnabled();
    expect(create).toHaveBeenCalledTimes(1);
  });
});
