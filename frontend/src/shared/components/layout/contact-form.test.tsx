import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { ContactSubmitAdapter } from "@/shared/lib/contact/contact-submit";
import { ContactForm } from "./contact-form";

const DEMO_NOTICE =
  "Bu iletişim formu şu anda demo aşamasındadır. Gönderilen bilgiler herhangi bir sunucuya iletilmez.";
const SUCCESS =
  "Form doğrulaması başarılı. Bu bir demo olduğu için mesajınız gönderilmedi.";

function renderForm(props: { submitAdapter?: ContactSubmitAdapter; locale?: "tr" | "en" } = {}) {
  const { locale = "tr", submitAdapter } = props;
  return render(
    <NextIntlClientProvider
      locale={locale}
      messages={locale === "tr" ? tr : en}
      timeZone="Europe/Istanbul"
    >
      <ContactForm submitAdapter={submitAdapter} />
    </NextIntlClientProvider>
  );
}

const name = () => screen.getByLabelText(/Ad Soyad/);
const email = () => screen.getByLabelText(/E-posta/);
const subject = () => screen.getByLabelText(/Konu/);
const message = () => screen.getByLabelText(/Mesaj/);
const submit = () => screen.getByRole("button", { name: "Formu Kontrol Et" });

function fillValid() {
  fireEvent.change(name(), { target: { value: "Ada Lovelace" } });
  fireEvent.change(email(), { target: { value: "ada@example.com" } });
  fireEvent.change(subject(), { target: { value: "feedback" } });
  fireEvent.change(message(), { target: { value: "Bu bir deneme mesajıdır." } });
}

const fetchSpy = vi.fn();
const logSpy = vi.spyOn(console, "log").mockImplementation(() => undefined);

beforeEach(() => {
  fetchSpy.mockReset();
  logSpy.mockClear();
  vi.stubGlobal("fetch", fetchSpy);
  window.localStorage.clear();
  window.sessionStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ContactForm", () => {
  it("dört alanı, demo bilgisini ve gönder düğmesini hata göstermeden sunar", () => {
    renderForm();

    expect(screen.getByText(DEMO_NOTICE)).toBeInTheDocument();
    expect(name()).toHaveAttribute("aria-invalid", "false");
    expect(subject().tagName).toBe("SELECT");
    expect(message().tagName).toBe("TEXTAREA");
    expect(
      Array.from((subject() as HTMLSelectElement).options).map((option) => option.textContent)
    ).toEqual(["Bir konu seç", "Genel Soru", "Geri Bildirim", "Hata Bildirimi", "İş Birliği", "Diğer"]);
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByText(SUCCESS)).toBeNull();
    expect(submit()).toBeInTheDocument();
  });

  it("boş gönderimde dört alan için hata gösterir, aria bağlar ve ilk alana odaklanır", () => {
    renderForm();

    fireEvent.click(submit());

    for (const field of [name(), email(), subject(), message()]) {
      expect(field).toHaveAttribute("aria-invalid", "true");
      const describedBy = field.getAttribute("aria-describedby") as string;
      const errorId = describedBy.split(" ").find((id) => id.endsWith("-error")) as string;
      expect(document.getElementById(errorId)).toHaveTextContent(/\S/);
    }
    expect(screen.getByText("Ad soyadını yaz.")).toBeInTheDocument();
    expect(screen.getByText("E-posta adresini yaz.")).toBeInTheDocument();
    expect(screen.getByText("Bir konu seç.")).toBeInTheDocument();
    expect(screen.getByText("Mesajını yaz.")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Formda düzeltmen gereken 4 alan var.");
    expect(name()).toHaveFocus();
    expect(screen.queryByText(SUCCESS)).toBeNull();
  });

  it("hata mesajı yalnız renkle değil, simge ve metinle de bildirilir", () => {
    renderForm();
    fireEvent.click(submit());

    const errorId = name().getAttribute("aria-describedby") as string;
    const error = document.getElementById(errorId) as HTMLElement;
    expect(error.querySelector("svg")).not.toBeNull();
    expect(error.textContent).toBe("Ad soyadını yaz.");
  });

  it("geçersiz e-postada yalnız e-posta hatası verir ve ona odaklanır", () => {
    renderForm();
    fillValid();
    fireEvent.change(email(), { target: { value: "ada-at-example" } });

    fireEvent.click(submit());

    expect(email()).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText(/Geçerli bir e-posta adresi yaz/)).toBeInTheDocument();
    expect(name()).toHaveAttribute("aria-invalid", "false");
    expect(message()).toHaveAttribute("aria-invalid", "false");
    expect(email()).toHaveFocus();
    expect(screen.getByRole("alert")).toHaveTextContent("1 alan");
    expect(screen.queryByText(SUCCESS)).toBeNull();
  });

  it("geçerli girişte doğrulama bilgisini gösterir, gönderildi demez ve adaptörü çağırır", async () => {
    const adapter = vi.fn<ContactSubmitAdapter>().mockResolvedValue({ status: "demo" });
    renderForm({ submitAdapter: adapter });
    fillValid();

    fireEvent.click(submit());

    expect(await screen.findByText(SUCCESS)).toBeInTheDocument();
    expect(adapter).toHaveBeenCalledTimes(1);
    expect(adapter).toHaveBeenCalledWith({
      name: "Ada Lovelace",
      email: "ada@example.com",
      subject: "feedback",
      message: "Bu bir deneme mesajıdır."
    });
    expect(screen.queryByRole("alert")).toBeNull();
    expect(document.body.textContent).not.toMatch(
      /başarıyla gönderildi|ekibimiz size dönecek|talebiniz oluşturuldu/i
    );
  });

  it("varsayılan adaptörle de çalışır (yan etkisiz demo)", async () => {
    renderForm();
    fillValid();

    fireEvent.click(submit());

    expect(await screen.findByText(SUCCESS)).toBeInTheDocument();
  });

  it("hiçbir ağ isteği, depolama yazması veya günlük üretmez", async () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem");
    const beacon = vi.fn();
    vi.stubGlobal("navigator", { ...navigator, sendBeacon: beacon });
    renderForm();
    fillValid();

    fireEvent.click(submit());
    await screen.findByText(SUCCESS);

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(beacon).not.toHaveBeenCalled();
    expect(setItem).not.toHaveBeenCalled();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).toBe("");
    expect(logSpy).not.toHaveBeenCalled();
    setItem.mockRestore();
  });

  it("karakter sayacı yazıldıkça güncellenir ve üst sınırı gösterir", () => {
    renderForm();

    expect(screen.getByTestId("message-counter")).toHaveTextContent("0 / 1000");
    fireEvent.change(message(), { target: { value: "merhaba" } });
    expect(screen.getByTestId("message-counter")).toHaveTextContent("7 / 1000");
    expect(message()).toHaveAttribute("maxlength", "1000");
    expect(name()).toHaveAttribute("maxlength", "80");
    expect(email()).toHaveAttribute("maxlength", "254");
  });

  it("çok kısa ve çok uzun mesajı reddeder", () => {
    renderForm();
    fillValid();

    fireEvent.change(message(), { target: { value: "kısa" } });
    fireEvent.click(submit());
    expect(screen.getByText("Mesajın en az 10 karakter olmalı.")).toBeInTheDocument();
    expect(message()).toHaveFocus();

    fireEvent.change(message(), { target: { value: "a".repeat(1001) } });
    expect(screen.getByText("Mesaj en fazla 1000 karakter olabilir.")).toBeInTheDocument();
    expect(screen.getByTestId("message-counter")).toHaveTextContent("1001 / 1000");
  });

  it("hatalar düzeltildikçe kalkar ve düzenleme doğrulama bilgisini siler", async () => {
    renderForm();
    fireEvent.click(submit());
    expect(screen.getByRole("alert")).toBeInTheDocument();

    fillValid();
    expect(screen.queryByRole("alert")).toBeNull();
    expect(name()).toHaveAttribute("aria-invalid", "false");

    fireEvent.click(submit());
    await screen.findByText(SUCCESS);
    fireEvent.change(name(), { target: { value: "Ada" } });
    await waitFor(() => expect(screen.queryByText(SUCCESS)).toBeNull());
  });

  it("İngilizce metinleri gösterir", () => {
    renderForm({ locale: "en" });

    expect(
      screen.getByText(
        "This contact form is currently in demo. The information you enter is not sent to any server."
      )
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Check the form" }));
    expect(screen.getByRole("alert")).toHaveTextContent("4 fields need your attention.");
  });
});
