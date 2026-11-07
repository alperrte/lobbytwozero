import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { RegistrationApi } from "../services/auth-api";
import { clearPendingEmail, rememberPendingEmail } from "../services/pending-verification";
import { VerifyEmailForm } from "./verify-email-form";

const replace = vi.fn();

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ push: vi.fn(), replace }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});

const PASSWORD = "dogru-parola-123456";

function renderForm(overrides: Partial<RegistrationApi> = {}) {
  const api: RegistrationApi = {
    register: vi.fn(),
    confirmEmail: vi.fn().mockResolvedValue(undefined),
    resendEmailCode: vi.fn().mockResolvedValue(undefined),
    ...overrides
  };
  render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <VerifyEmailForm api={api} />
    </NextIntlClientProvider>
  );
  return api;
}

const email = () => screen.getByLabelText("E-posta") as HTMLInputElement;
const code = () => screen.getByLabelText("Doğrulama kodu") as HTMLInputElement;
const password = () => screen.getByLabelText("Kayıt olurken belirlediğin şifre") as HTMLInputElement;
const submit = () => screen.getByRole("button", { name: /E-postayı doğrula/ });
const resend = () => screen.getByRole("button", { name: /yeniden gönder|bekle/i });

function fillAll(codeValue = "012345") {
  fireEvent.change(email(), { target: { value: "ada@example.com" } });
  fireEvent.change(code(), { target: { value: codeValue } });
  fireEvent.change(password(), { target: { value: PASSWORD } });
}

beforeEach(() => {
  replace.mockReset();
  clearPendingEmail();
});

describe("VerifyEmailForm", () => {
  it("kayıttan gelen e-postayı bellekten doldurur; düzenlenebilir", async () => {
    rememberPendingEmail("ada@example.com");
    renderForm();

    await waitFor(() => expect(email().value).toBe("ada@example.com"));
    fireEvent.change(email(), { target: { value: "baska@example.com" } });
    expect(email().value).toBe("baska@example.com");
  });

  it("boş gönderimde hataları gösterir ve ilk alana odaklanır; API çağrılmaz", async () => {
    const api = renderForm();

    fireEvent.click(submit());

    expect(await screen.findByText("E-posta adresini gir.")).toBeInTheDocument();
    expect(screen.getByText("Doğrulama kodunu gir.")).toBeInTheDocument();
    expect(screen.getByText("Kayıt olurken belirlediğin şifreyi gir.")).toBeInTheDocument();
    expect(email()).toHaveFocus();
    expect(api.confirmEmail).not.toHaveBeenCalled();
  });

  it("kod alanı yapıştırılan metinden yalnız rakamları alır ve başındaki sıfırı korur", () => {
    renderForm();
    fireEvent.change(code(), { target: { value: "012 345 abc" } });
    expect(code().value).toBe("012345");
    expect(code()).toHaveAttribute("autocomplete", "one-time-code");
    expect(code()).toHaveAttribute("inputmode", "numeric");
  });

  it("başarıda bellekteki e-postayı siler ve giriş sayfasına verified bildirimiyle yönlendirir", async () => {
    rememberPendingEmail("ada@example.com");
    const api = renderForm();
    fireEvent.change(code(), { target: { value: "012345" } });
    fireEvent.change(password(), { target: { value: PASSWORD } });

    fireEvent.click(submit());

    await waitFor(() => expect(replace).toHaveBeenCalledWith({ pathname: "/login", query: { verified: "1" } }));
    expect(api.confirmEmail).toHaveBeenCalledWith("ada@example.com", "012345", PASSWORD);
    expect(password().value).toBe("");
  });

  it("VERIFICATION_FAILED tüm nedenler için aynı mesajı kod alanında gösterir; parola silinir", async () => {
    renderForm({ confirmEmail: vi.fn().mockRejectedValue(new ApiHttpError(400, { code: "VERIFICATION_FAILED" })) });
    fillAll();

    fireEvent.click(submit());

    expect(await screen.findByText(/Kod geçersiz, süresi dolmuş ya da deneme hakkı bitmiş/)).toBeInTheDocument();
    expect(code()).toHaveAttribute("aria-invalid", "true");
    expect(password().value).toBe("");
    expect(replace).not.toHaveBeenCalled();
  });

  it("429'da geri sayım gösterir ve gönder düğmesini pasif yapar", async () => {
    renderForm({
      confirmEmail: vi.fn().mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 120 }))
    });
    fillAll();

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("2 dakika sonra tekrar dene");
    expect(screen.getByRole("button", { name: "2 dakika sonra tekrar dene" })).toBeDisabled();
  });

  it("kodu yeniden gönderir, bilgi mesajı gösterir ve 60 sn bekleme başlatır", async () => {
    const api = renderForm();
    fireEvent.change(email(), { target: { value: "ada@example.com" } });

    fireEvent.click(resend());

    expect(await screen.findByText(/Yeni kod gönderildi/)).toBeInTheDocument();
    expect(api.resendEmailCode).toHaveBeenCalledWith("ada@example.com");
    await waitFor(() => expect(resend()).toBeDisabled());
    expect(resend()).toHaveTextContent("1 dakika bekle");
  });

  it("e-posta geçersizken yeniden gönderme istek atmaz ve e-posta alanına odaklanır", async () => {
    const api = renderForm();

    fireEvent.click(resend());

    expect(await screen.findByText("E-posta adresini gir.")).toBeInTheDocument();
    expect(email()).toHaveFocus();
    expect(api.resendEmailCode).not.toHaveBeenCalled();
  });

  it("yeniden gönderme ağ hatasında hata kutusu gösterir ve bekleme başlatmaz", async () => {
    renderForm({ resendEmailCode: vi.fn().mockRejectedValue(new ApiHttpError(0, { code: "NETWORK" })) });
    fireEvent.change(email(), { target: { value: "ada@example.com" } });

    fireEvent.click(resend());

    expect(await screen.findByRole("alert")).toHaveTextContent("Sunucuya ulaşılamadı");
    expect(resend()).not.toBeDisabled();
  });

  it("giriş sayfasına dönüş bağlantısı vardır", () => {
    renderForm();
    expect(screen.getByRole("link", { name: "Giriş sayfasına dön" })).toHaveAttribute("href", "/login");
  });

  it("gönderim sürerken çift gönderimi engeller", async () => {
    let finish!: () => void;
    const confirmEmail = vi.fn(() => new Promise<void>((r) => (finish = r)));
    renderForm({ confirmEmail });
    fillAll();

    fireEvent.click(submit());
    await waitFor(() => expect(screen.getByRole("button", { name: /Doğrulanıyor/ })).toBeDisabled());
    fireEvent.submit(screen.getByRole("form", { name: "E-posta doğrulama formu" }));
    expect(confirmEmail).toHaveBeenCalledTimes(1);

    await act(async () => finish());
    await waitFor(() => expect(replace).toHaveBeenCalled());
  });
});
