import { useState } from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { hardwareApi } from "../services/hardware-api";
import type { Review } from "../types/hardware";
import { ProductReviews } from "./product-reviews";
import { ReviewForm } from "./review-form";

vi.mock("../services/hardware-api", () => ({
  hardwareApi: { saveReview: vi.fn(), deleteReview: vi.fn(), reviews: vi.fn(), myReviews: vi.fn(), reportReview: vi.fn() }
}));

const api = vi.mocked(hardwareApi);
const mine: Review = { id: "r1", productId: "p1", body: "Sessiz ve serin çalışıyor.", rating: 4, status: "VISIBLE", version: 2, ownedByActor: true, createdAt: "2026-11-01T10:00:00Z", updatedAt: "2026-11-01T10:00:00Z" };
const wrap = (node: React.ReactNode) => render(<NextIntlClientProvider locale="tr" messages={tr}>{node}</NextIntlClientProvider>);

function trackOf(slider: HTMLElement) {
  slider.getBoundingClientRect = () => ({ left: 0, right: 500, top: 0, bottom: 40, width: 500, height: 40, x: 0, y: 0, toJSON: () => ({}) });
}
function rate(clientX: number) {
  const slider = screen.getByRole("slider", { name: "Puan" });
  trackOf(slider);
  fireEvent.pointerDown(slider, { clientX, pointerId: 1 });
  fireEvent.pointerUp(slider, { clientX, pointerId: 1 });
}

describe("Değerlendirme formu", () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it("puan açılır liste değil yıldızdır; seçilen puan sağda yazılır", () => {
    wrap(<ReviewForm productId="p1" onClose={() => undefined} onSaved={() => undefined} />);
    expect(screen.queryByRole("combobox")).toBeNull();
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("Puan verilmedi");
    rate(330);
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("3,5 / 5");
    expect(screen.getByRole("slider", { name: "Puan" })).toHaveAttribute("aria-valuenow", "3.5");
  });

  it("yarım yıldız verilebilir: yıldızın sol yarısı .5, sağ yarısı tam puan; klavye 0,5 adımla ilerler", () => {
    wrap(<ReviewForm productId="p1" onClose={() => undefined} onSaved={() => undefined} />);
    rate(210);
    expect(screen.getByRole("slider", { name: "Puan" })).toHaveAttribute("aria-valuenow", "2.5");
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("2,5 / 5");
    rate(280);
    expect(screen.getByRole("slider", { name: "Puan" })).toHaveAttribute("aria-valuenow", "3");
    fireEvent.keyDown(screen.getByRole("slider", { name: "Puan" }), { key: "ArrowRight" });
    expect(screen.getByRole("slider", { name: "Puan" })).toHaveAttribute("aria-valuenow", "3.5");
  });

  it("Kaydet doğrudan göndermez; önce 'Emin misiniz?' penceresi açılır, vazgeçince form açık kalır", () => {
    wrap(<ReviewForm productId="p1" onClose={() => undefined} onSaved={() => undefined} />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "  Harika bir ürün  " } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    const confirm = screen.getByRole("dialog", { name: "Emin misiniz?" });
    expect(api.saveReview).not.toHaveBeenCalled();
    fireEvent.click(within(confirm).getByRole("button", { name: "Vazgeç" }));
    expect(screen.queryByRole("dialog", { name: "Emin misiniz?" })).toBeNull();
    expect(screen.getByRole("textbox")).toHaveValue("  Harika bir ürün  ");
    expect(api.saveReview).not.toHaveBeenCalled();
  });

  it("onaylayınca puan ve kırpılmış metinle kaydeder, başarıda formu kapatır ve listeyi yeniler", async () => {
    api.saveReview.mockResolvedValue(mine);
    const onClose = vi.fn();
    const onSaved = vi.fn();
    wrap(<ReviewForm productId="p1" onClose={onClose} onSaved={onSaved} />);
    rate(130);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "  Harika bir ürün  " } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    fireEvent.click(within(screen.getByRole("dialog", { name: "Emin misiniz?" })).getByRole("button", { name: "Evet, kaydet" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(api.saveReview).toHaveBeenCalledWith("p1", { body: "Harika bir ürün", rating: 1.5 }, undefined);
    expect(onSaved).toHaveBeenCalled();
  });

  it("kayıttan sonra form tamamen kapanır, sayfa kaydırması kilitli kalmaz (iç içe modal)", async () => {
    api.saveReview.mockResolvedValue(mine);
    function Host() {
      const [open, setOpen] = useState(true);
      return open ? <ReviewForm productId="p1" onClose={() => setOpen(false)} onSaved={() => undefined} /> : <p>kapandı</p>;
    }
    wrap(<Host />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Güzel" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    expect(document.documentElement.style.overflow).toBe("hidden");
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));
    await screen.findByText("kapandı");
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(document.documentElement.style.overflow).toBe("");
  });

  it("düzenlemede mevcut puanla başlar ve sürümü gönderir", async () => {
    api.saveReview.mockResolvedValue(mine);
    wrap(<ReviewForm productId="p1" review={mine} onClose={() => undefined} onSaved={() => undefined} />);
    expect(screen.getByRole("slider", { name: "Puan" })).toHaveAttribute("aria-valuenow", "4");
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    expect(screen.getByRole("dialog", { name: "Emin misiniz?" })).toHaveAccessibleDescription(tr.Hardware.reviews.confirmUpdate);
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));
    await waitFor(() => expect(api.saveReview).toHaveBeenCalledWith("p1", { body: mine.body, rating: 4, reviewVersion: 2 }, "r1"));
  });

  it("puansız yorum null puanla gönderilir", async () => {
    api.saveReview.mockResolvedValue(mine);
    wrap(<ReviewForm productId="p1" onClose={() => undefined} onSaved={() => undefined} />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Sadece metin" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));
    await waitFor(() => expect(api.saveReview).toHaveBeenCalledWith("p1", { body: "Sadece metin", rating: null }, undefined));
  });

  it("sunucu hatasında onay penceresi kapanır, hata formda görünür ve form açık kalır", async () => {
    api.saveReview.mockRejectedValue(new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" }));
    const onClose = vi.fn();
    wrap(<ReviewForm productId="p1" onClose={onClose} onSaved={() => undefined} />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Deneme" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(tr.Hardware.errors["403"].title));
    expect(screen.queryByRole("dialog", { name: "Emin misiniz?" })).toBeNull();
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe("Yinelenen değerlendirme", () => {
  it("yeni yorumda 409 gelirse genel çakışma metni yerine 'zaten değerlendirdin' açıklaması gösterilir", async () => {
    vi.clearAllMocks();
    api.saveReview.mockRejectedValue(new ApiHttpError(409, { code: "DATA_CONFLICT" }));
    wrap(<ReviewForm productId="p1" onClose={() => undefined} onSaved={() => undefined} />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Aynı ürün" } });
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(tr.Hardware.reviews.duplicateTitle));
    expect(screen.getByRole("alert")).not.toHaveTextContent(tr.Hardware.errors["409"].title);
  });
});

describe("Değerlendirme listesi", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.reviews.mockResolvedValue({ items: [mine], page: 0, size: 10, totalElements: 1, totalPages: 1, summary: { ratedReviews: 1, averageRating: 4 } });
    api.myReviews.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it("başlığın altında ortalama puanı yıldızla, kaç kişinin değerlendirdiğini yazıyla gösterir", async () => {
    api.reviews.mockResolvedValue({ items: [mine], page: 0, size: 10, totalElements: 1, totalPages: 1, summary: { ratedReviews: 12, averageRating: 3.46 } });
    wrap(<ProductReviews productId="p1" />);
    expect(await screen.findByRole("img", { name: "Ortalama puan 3,5 / 5" })).toBeInTheDocument();
    expect(screen.getByText("3,5 / 5")).toBeInTheDocument();
    expect(screen.getByText("12 kişi tarafından değerlendirildi")).toBeInTheDocument();
  });

  it("ürünü zaten değerlendirdiysen ana düğme düzenlemeye götürür, yeni yorum açmaz", async () => {
    api.myReviews.mockResolvedValue({ items: [mine], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    wrap(<ProductReviews productId="p1" />);
    fireEvent.click(await screen.findByRole("button", { name: "Değerlendirmeni düzenle" }));
    expect(screen.getByRole("dialog", { name: tr.Hardware.reviews.edit })).toBeInTheDocument();
    expect(screen.getByRole("slider", { name: "Puan" })).toHaveAttribute("aria-valuenow", "4");
  });

  it("puanı yıldız olarak gösterir", async () => {
    wrap(<ProductReviews productId="p1" />);
    expect(await screen.findByRole("img", { name: "4 / 5" })).toBeInTheDocument();
  });

  it("silme ortak onay penceresiyle yapılır: vazgeçince silmez, onaylayınca siler", async () => {
    api.deleteReview.mockResolvedValue(undefined);
    wrap(<ProductReviews productId="p1" />);
    fireEvent.click(await screen.findByRole("button", { name: "Sil" }));
    const dialog = screen.getByRole("dialog", { name: tr.Hardware.reviews.deleteTitle });
    fireEvent.click(within(dialog).getByRole("button", { name: "Vazgeç" }));
    expect(api.deleteReview).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Sil" }));
    fireEvent.click(screen.getByRole("button", { name: "Silmeyi onayla" }));
    await waitFor(() => expect(api.deleteReview).toHaveBeenCalledWith(mine));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: tr.Hardware.reviews.deleteTitle })).toBeNull());
  });
});
