import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { RegistrationApi } from "../services/auth-api";
import { clearPendingEmail, pendingEmailStore } from "../services/pending-verification";
import { RegisterForm } from "./register-form";

const push = vi.fn();

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ push, replace: vi.fn() }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});

const PASSWORD = "Dogru-Parola-123456";

function renderForm(register: RegistrationApi["register"], locale: "tr" | "en" = "tr") {
  const api: RegistrationApi = { register, confirmEmail: vi.fn(), resendEmailCode: vi.fn() };
  return render(
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en} timeZone="Europe/Istanbul">
      <RegisterForm api={api} />
    </NextIntlClientProvider>
  );
}

const username = () => screen.getByLabelText("Kullanıcı adı");
const email = () => screen.getByLabelText("E-posta");
const password = () => screen.getByLabelText("Şifre") as HTMLInputElement;
const terms = () => screen.getByRole("checkbox");
const submit = () => screen.getByRole("button", { name: /Hesap oluştur/ });

function fill(overrides: { u?: string; e?: string; p?: string; terms?: boolean } = {}) {
  fireEvent.change(username(), { target: { value: overrides.u ?? "ada_01" } });
  fireEvent.change(email(), { target: { value: overrides.e ?? "ada@example.com" } });
  fireEvent.change(password(), { target: { value: overrides.p ?? PASSWORD } });
  if (overrides.terms ?? true) fireEvent.click(terms());
}

beforeEach(() => {
  push.mockReset();
  clearPendingEmail();
});

describe("RegisterForm", () => {
  it("boş gönderimde alan hatalarını gösterir, ilk alana odaklanır ve API çağrılmaz", async () => {
    const register = vi.fn();
    renderForm(register);

    fireEvent.click(submit());

    expect(await screen.findByText("Bir kullanıcı adı belirle.")).toBeInTheDocument();
    expect(screen.getByText("E-posta adresini gir.")).toBeInTheDocument();
    expect(screen.getByText("Şifreni gir.")).toBeInTheDocument();
    expect(screen.getByText("Devam etmek için koşulları kabul etmelisin.")).toBeInTheDocument();
    expect(username()).toHaveFocus();
    expect(username()).toHaveAttribute("aria-invalid", "true");
    expect(register).not.toHaveBeenCalled();
  });

  it("koşulları kabul etmeden gönderilmez", () => {
    const register = vi.fn();
    renderForm(register);
    fill({ terms: false });

    fireEvent.click(submit());

    expect(screen.getByText("Devam etmek için koşulları kabul etmelisin.")).toBeInTheDocument();
    expect(register).not.toHaveBeenCalled();
  });

  it("başarıda e-postayı bellekte bırakıp doğrulama ekranına geçer; parola alandan silinir", async () => {
    const register = vi.fn().mockResolvedValue(undefined);
    renderForm(register);

    fill({ e: "  ada@example.com  " });
    fireEvent.click(submit());

    await waitFor(() => expect(push).toHaveBeenCalledWith("/verify-email"));
    expect(register).toHaveBeenCalledWith("ada@example.com", "ada_01", PASSWORD);
    expect(pendingEmailStore.getSnapshot()).toBe("ada@example.com");
    expect(password().value).toBe("");
  });

  it("USERNAME_TAKEN kullanıcı adı alanında, WEAK_PASSWORD şifre alanında gösterilir", async () => {
    const register = vi
      .fn()
      .mockRejectedValueOnce(new ApiHttpError(409, { code: "USERNAME_TAKEN" }))
      .mockRejectedValueOnce(new ApiHttpError(400, { code: "WEAK_PASSWORD" }));
    renderForm(register);

    fill();
    fireEvent.click(submit());
    expect(await screen.findByText("Bu kullanıcı adı kullanılıyor.")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();

    fireEvent.change(password(), { target: { value: PASSWORD } });
    fireEvent.click(submit());
    expect(await screen.findByText(/tahmin edilmesi kolay/)).toBeInTheDocument();
    expect(screen.queryByText("Bu kullanıcı adı kullanılıyor.")).toBeNull();
  });

  it("VALIDATION_FAILED alan hatalarını ilgili alana bağlar", async () => {
    const register = vi.fn().mockRejectedValue(
      new ApiHttpError(400, { code: "VALIDATION_FAILED", fieldErrors: [{ field: "email", code: "Email" }] })
    );
    renderForm(register);
    fill();

    fireEvent.click(submit());

    expect(await screen.findByText("Geçerli bir e-posta adresi gir.")).toBeInTheDocument();
  });

  it.each([
    ["SERVICE_BUSY", /yoğun/],
    ["NETWORK", /Sunucuya ulaşılamadı/],
    ["UNKNOWN", /ters gitti/]
  ] as const)("%s için genel hata kutusu gösterir", async (code, pattern) => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(code === "NETWORK" ? 0 : 503, { code })));
    fill();

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(pattern);
    expect(push).not.toHaveBeenCalled();
  });

  it("429'da geri sayım gösterir ve düğmeyi pasif yapar", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 900 })));
    fill();

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("15 dakika sonra tekrar dene");
    expect(screen.getByRole("button", { name: "15 dakika sonra tekrar dene" })).toBeDisabled();
  });

  it("gönderim sürerken çift gönderimi engeller", async () => {
    let resolve!: () => void;
    const register = vi.fn(() => new Promise<void>((r) => (resolve = r)));
    renderForm(register);
    fill();

    fireEvent.click(submit());
    await waitFor(() => expect(screen.getByRole("button", { name: /oluşturuluyor/ })).toBeDisabled());
    fireEvent.submit(screen.getByRole("form", { name: "Kayıt formu" }));
    expect(register).toHaveBeenCalledTimes(1);

    resolve();
    await waitFor(() => expect(push).toHaveBeenCalled());
  });

  it("alanlar doğru autocomplete değerlerini taşır; şifre alanı yeni şifre olarak işaretlidir", () => {
    renderForm(vi.fn());
    expect(username()).toHaveAttribute("autocomplete", "username");
    expect(email()).toHaveAttribute("autocomplete", "email");
    expect(password()).toHaveAttribute("autocomplete", "new-password");
  });

  it("koşullar ve gizlilik bağlantıları gerçek sayfalara gider", () => {
    renderForm(vi.fn());
    expect(screen.getByRole("link", { name: /Kullanım koşullarını/ })).toHaveAttribute("href", "/legal/terms");
    expect(screen.getByRole("link", { name: /Gizlilik politikasını/ })).toHaveAttribute("href", "/legal/privacy");
  });

  it("şifre göster/gizle düğmesi alan türünü değiştirir", () => {
    renderForm(vi.fn());
    expect(password()).toHaveAttribute("type", "password");
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi göster" }));
    expect(password()).toHaveAttribute("type", "text");
  });

  it("İngilizce arayüzde İngilizce hata gösterir", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(409, { code: "USERNAME_TAKEN" })), "en");
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "ada_01" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: PASSWORD } });
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: /Create account/ }));

    expect(await screen.findByText("This username is already taken.")).toBeInTheDocument();
  });
});
