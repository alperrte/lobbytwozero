import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { reviewMutations } from "../services/review-api";
import type { ReviewReport } from "../types/review";
import { ReportReview } from "./report-review";

vi.mock("next/navigation", () => ({ usePathname: () => "/tr/incelemeler/x", useSearchParams: () => new URLSearchParams() }));
vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ href, children, ...props }: { href: string; children: ReactNode }) => <a href={String(href)} {...props}>{children}</a> }));
vi.mock("../services/review-api", () => ({ reviewMutations: { reportReview: vi.fn() } }));

const reviewId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66";
const report = vi.mocked(reviewMutations.reportReview);
const saved: ReviewReport = { id: "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66", reviewId, reason: "SPAM", detail: null, status: "RECEIVED", createdAt: "2026-11-01T12:00:00Z" };

function wrap(children: ReactNode, locale: "tr" | "en" = "tr") {
  return <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en}>{children}</NextIntlClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
});
afterEach(() => {
  vi.useRealTimers();
});

function openDialog() {
  render(wrap(<ReportReview reviewId={reviewId} ownedByActor={false} />));
  fireEvent.click(screen.getByRole("button", { name: "Raporla" }));
  return screen.getByRole("dialog");
}

describe("İnceleme raporlama", () => {
  it("kendi incelemende hiçbir şey göstermez", () => {
    render(wrap(<ReportReview reviewId={reviewId} ownedByActor />));
    expect(screen.queryByRole("button", { name: "Raporla" })).toBeNull();
  });

  it("başkasının incelemesinde düğme sunar ve diyaloğu gerekçe alanına odaklı açar", () => {
    const dialog = openDialog();
    expect(within(dialog).getByRole("heading", { name: "İncelemeyi raporla" })).toBeVisible();
    expect(within(dialog).getByLabelText("Gerekçe")).toHaveFocus();
    expect(within(dialog).getAllByRole("option")).toHaveLength(8);
    expect(report).not.toHaveBeenCalled();
  });

  it("raporun yalnızca kaydedildiğini söyler; Admin'e iletildi/karar verildi iddiası kurmaz", () => {
    const dialog = openDialog();
    const text = dialog.textContent ?? "";
    expect(text).toContain("moderasyon ekibine iletim henüz etkin değildir");
    expect(text).toContain("kimin raporladığını öğrenmez");
    expect(text).not.toMatch(/iletildi|karara bağlandı|incelendi/);
  });

  it("gerekçe seçilmeden göndermez, hatayı alana bağlar", () => {
    const dialog = openDialog();
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(report).not.toHaveBeenCalled();
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Bir gerekçe seçmelisin.");
    expect(within(dialog).getByLabelText("Gerekçe")).toHaveAttribute("aria-invalid", "true");
    expect(within(dialog).getByLabelText("Gerekçe")).toHaveFocus();
  });

  it("açıklama sınırını ve sayacı uygular", () => {
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "SPAM" } });
    const field = within(dialog).getByLabelText("Açıklama (isteğe bağlı)");
    fireEvent.change(field, { target: { value: "x".repeat(501) } });
    expect(within(dialog).getByText("501 / 500 karakter")).toBeVisible();
    expect(within(dialog).getByRole("alert")).toHaveTextContent("en fazla 500 karakter");
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(report).not.toHaveBeenCalled();
  });

  it("gerekçe ve açıklamayla gönderir; başarıda diyaloğu kapatır ve 'kaydedildi' gösterir", async () => {
    report.mockResolvedValue(saved);
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "SPAM" } });
    fireEvent.change(within(dialog).getByLabelText("Açıklama (isteğe bağlı)"), { target: { value: "Reklam içeriyor" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await screen.findByText(/Raporladın ·/)).toBeVisible();
    expect(report).toHaveBeenCalledExactlyOnceWith(reviewId, "SPAM", "Reklam içeriyor", expect.any(AbortSignal));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.queryByRole("button", { name: "Raporla" })).toBeNull();
  });

  it("gönderim sırasında düğmeler kapanır; çift gönderim olmaz", async () => {
    let finish: (value: ReviewReport) => void = () => undefined;
    report.mockImplementation(() => new Promise<ReviewReport>((resolve) => { finish = resolve; }));
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "OTHER" } });
    const submit = within(dialog).getByRole("button", { name: "Raporu gönder" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(await within(dialog).findByRole("button", { name: "Kaydediliyor…" })).toBeDisabled();
    expect(within(dialog).getByRole("button", { name: "Vazgeç" })).toBeDisabled();
    expect(report).toHaveBeenCalledTimes(1);
    await act(async () => finish(saved));
  });

  it("mükerrer rapor (409) 'zaten raporladın' olarak ele alınır", async () => {
    report.mockRejectedValue(new ApiHttpError(409));
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "HARASSMENT" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await screen.findByText("Bu incelemeyi zaten raporladın.")).toBeVisible();
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("hatada diyaloğu açık tutar, girdiyi korur ve gerçek nedeni gösterir", async () => {
    report.mockRejectedValue(new ApiHttpError(503));
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "SPOILER" } });
    fireEvent.change(within(dialog).getByLabelText("Açıklama (isteğe bağlı)"), { target: { value: "Final açıklanmış" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await within(dialog).findByText("Servis geçici olarak kullanılamıyor. Biraz sonra tekrar dene.")).toBeVisible();
    expect(within(dialog).getByLabelText("Gerekçe")).toHaveValue("SPOILER");
    expect(within(dialog).getByLabelText("Açıklama (isteğe bağlı)")).toHaveValue("Final açıklanmış");
    expect(report).toHaveBeenCalledTimes(1);
  });

  it("403'te (kendi incelemesi/engel) ve 404'te uygun mesajı gösterir", async () => {
    report.mockRejectedValueOnce(new ApiHttpError(403));
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "SPAM" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await within(dialog).findByText(/Kendi incelemeni raporlayamazsın/)).toBeVisible();
    report.mockRejectedValueOnce(new ApiHttpError(404));
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await within(dialog).findByText("İnceleme bulunamadı veya silinmiş.")).toBeVisible();
  });

  it("429'da Retry-After süresince gönderimi kapatır ve otomatik tekrar denemez", async () => {
    report.mockRejectedValue(new ApiHttpError(429, { retryAfterSeconds: 90 }));
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "SPAM" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await within(dialog).findByText(/90 saniye sonra tekrar deneyebilirsin/)).toBeVisible();
    expect(within(dialog).getByRole("button", { name: "Raporu gönder" })).toBeDisabled();
    expect(report).toHaveBeenCalledTimes(1);
  });

  it("geri sayım bitince gönderim yeniden açılır", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    report.mockRejectedValueOnce(new ApiHttpError(429, { retryAfterSeconds: 2 }));
    const dialog = openDialog();
    fireEvent.change(within(dialog).getByLabelText("Gerekçe"), { target: { value: "SPAM" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Raporu gönder" }));
    expect(await within(dialog).findByText(/2 saniye sonra/)).toBeVisible();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(within(dialog).getByRole("button", { name: "Raporu gönder" })).toBeEnabled();
  });

  it("vazgeçilirse rapor göndermez ve odağı düğmeye geri verir", async () => {
    const dialog = openDialog();
    fireEvent.click(within(dialog).getByRole("button", { name: "Vazgeç" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(report).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Raporla" })).toBeVisible();
  });

  it("sunucudan okunan mevcut raporu gösterir; tekrar rapor düğmesi sunmaz", () => {
    render(wrap(<ReportReview reviewId={reviewId} ownedByActor={false} initialReport={saved} />));
    expect(screen.getByText(/Raporladın ·/)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Raporla" })).toBeNull();
  });

  it("raporlama yoksa (null) düğme sunar", () => {
    render(wrap(<ReportReview reviewId={reviewId} ownedByActor={false} initialReport={null} />));
    expect(screen.getByRole("button", { name: "Raporla" })).toBeVisible();
  });

  it("İngilizce metinleri kullanır", () => {
    render(wrap(<ReportReview reviewId={reviewId} ownedByActor={false} />, "en"));
    fireEvent.click(screen.getByRole("button", { name: "Report" }));
    expect(screen.getByRole("heading", { name: "Report this review" })).toBeVisible();
    expect(screen.getByLabelText("Reason")).toBeVisible();
  });
});
