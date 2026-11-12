import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { PasswordResetApi } from "../services/auth-api";
import { forgetFragmentToken } from "../services/fragment-token";
import { ForgotPasswordForm } from "./forgot-password-form";
import { ResetPasswordForm } from "./reset-password-form";

const replace = vi.fn();

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ push: vi.fn(), replace }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});

const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdEf";
const PASSWORD = "Yeni-Dogru-Parola-123";

function apiWith(overrides: Partial<PasswordResetApi> = {}): PasswordResetApi {
  return {
    forgotPassword: vi.fn().mockResolvedValue(undefined),
    resetPassword: vi.fn().mockResolvedValue(undefined),
    ...overrides
  };
}

function wrap(node: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      {node}
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  replace.mockReset();
  forgetFragmentToken();
  window.history.replaceState(null, "", "/");
});

describe("ForgotPasswordForm", () => {
  const email = () => screen.getByLabelText("E-posta");
  const submit = () => screen.getByRole("button", { name: /Bağlantı gönder|Yeniden gönder/ });

  it("geçersiz e-postada hata gösterir, odaklanır ve istek atmaz", async () => {
    const api = apiWith();
    wrap(<ForgotPasswordForm api={api} />);

    fireEvent.click(submit());

    expect(await screen.findByText("E-posta adresini gir.")).toBeInTheDocument();
    expect(email()).toHaveFocus();
    expect(api.forgotPassword).not.toHaveBeenCalled();
  });

  it("başarıda hesap varlığını söylemeden tek bilgi mesajı gösterir", async () => {
    const api = apiWith();
    wrap(<ForgotPasswordForm api={api} />);
    fireEvent.change(email(), { target: { value: "  ada@example.com " } });

    fireEvent.click(submit());

    expect(await screen.findByText(/Bu adres kayıtlıysa bir şifre sıfırlama bağlantısı gönderdik/)).toBeInTheDocument();
    expect(api.forgotPassword).toHaveBeenCalledWith("ada@example.com");
  });

  it("429'da geri sayım gösterir ve düğmeyi pasif yapar", async () => {
    wrap(
      <ForgotPasswordForm
        api={apiWith({ forgotPassword: vi.fn().mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 120 })) })}
      />
    );
    fireEvent.change(email(), { target: { value: "ada@example.com" } });

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("2 dakika sonra tekrar dene");
    expect(screen.getByRole("button", { name: "2 dakika sonra tekrar dene" })).toBeDisabled();
  });

  it("ağ hatasında hata kutusu gösterir; giriş sayfasına dönüş bağlantısı vardır", async () => {
    wrap(<ForgotPasswordForm api={apiWith({ forgotPassword: vi.fn().mockRejectedValue(new ApiHttpError(0, { code: "NETWORK" })) })} />);
    fireEvent.change(email(), { target: { value: "ada@example.com" } });

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("Sunucuya ulaşılamadı");
    expect(screen.getByRole("link", { name: "Giriş sayfasına dön" })).toHaveAttribute("href", "/login");
  });
});

describe("ResetPasswordForm", () => {
  const newPassword = () => screen.getByLabelText("Yeni şifre") as HTMLInputElement;
  const confirmation = () => screen.getByLabelText("Yeni şifre (tekrar)") as HTMLInputElement;
  const submit = () => screen.getByRole("button", { name: /Şifreyi güncelle/ });

  function openWithToken() {
    window.history.replaceState(null, "", `/auth/reset-password#token=${TOKEN}`);
  }

  it("bağlantıda token yoksa form yerine yeni bağlantı isteme yönlendirmesi gösterir", async () => {
    wrap(<ResetPasswordForm api={apiWith()} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Bu bağlantı geçersiz ya da süresi dolmuş");
    expect(screen.getByRole("link", { name: "Yeni bağlantı iste" })).toHaveAttribute("href", "/forgot-password");
    expect(screen.queryByLabelText("Yeni şifre")).toBeNull();
  });

  it("token'ı adres çubuğundan siler ve yalnız istek gövdesinde gönderir", async () => {
    openWithToken();
    const api = apiWith();
    wrap(<ResetPasswordForm api={api} />);
    await waitFor(() => expect(newPassword()).toBeInTheDocument());
    expect(window.location.hash).toBe("");

    fireEvent.change(newPassword(), { target: { value: PASSWORD } });
    fireEvent.change(confirmation(), { target: { value: PASSWORD } });
    fireEvent.click(submit());

    await waitFor(() => expect(replace).toHaveBeenCalledWith({ pathname: "/login", query: { reset: "1" } }));
    expect(api.resetPassword).toHaveBeenCalledWith(TOKEN, PASSWORD, PASSWORD);
    expect(newPassword().value).toBe("");
  });

  it("boş gönderim ve eşleşmeyen tekrarda hata gösterir; istek atmaz", async () => {
    openWithToken();
    const api = apiWith();
    wrap(<ResetPasswordForm api={api} />);
    await waitFor(() => expect(newPassword()).toBeInTheDocument());

    fireEvent.click(submit());
    expect(await screen.findByText("Yeni şifreni gir.")).toBeInTheDocument();
    expect(newPassword()).toHaveFocus();

    fireEvent.change(newPassword(), { target: { value: PASSWORD } });
    fireEvent.change(confirmation(), { target: { value: `${PASSWORD}x` } });
    fireEvent.click(submit());
    expect(await screen.findByText("Şifreler eşleşmiyor.")).toBeInTheDocument();
    expect(api.resetPassword).not.toHaveBeenCalled();
  });

  it("PASSWORD_RESET_TOKEN_INVALID gelince form yerine yeni bağlantı yönlendirmesi gösterir", async () => {
    openWithToken();
    wrap(
      <ResetPasswordForm
        api={apiWith({ resetPassword: vi.fn().mockRejectedValue(new ApiHttpError(400, { code: "PASSWORD_RESET_TOKEN_INVALID" })) })}
      />
    );
    await waitFor(() => expect(newPassword()).toBeInTheDocument());
    fireEvent.change(newPassword(), { target: { value: PASSWORD } });
    fireEvent.change(confirmation(), { target: { value: PASSWORD } });

    fireEvent.click(submit());

    expect(await screen.findByRole("link", { name: "Yeni bağlantı iste" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Yeni şifre")).toBeNull();
    expect(replace).not.toHaveBeenCalled();
  });

  it.each([
    ["WEAK_PASSWORD", /tahmin edilmesi kolay/],
    ["NEW_PASSWORD_SAME_AS_CURRENT", /mevcut şifrenden farklı/]
  ] as const)("%s yeni şifre alanında gösterilir; parolalar silinir", async (code, pattern) => {
    openWithToken();
    wrap(<ResetPasswordForm api={apiWith({ resetPassword: vi.fn().mockRejectedValue(new ApiHttpError(400, { code })) })} />);
    await waitFor(() => expect(newPassword()).toBeInTheDocument());
    fireEvent.change(newPassword(), { target: { value: PASSWORD } });
    fireEvent.change(confirmation(), { target: { value: PASSWORD } });

    fireEvent.click(submit());

    expect(await screen.findByText(pattern)).toBeInTheDocument();
    expect(newPassword().value).toBe("");
    expect(confirmation().value).toBe("");
  });

  it("alanlar yeni-parola autocomplete değeri taşır; göster/gizle iki alanı birlikte değiştirir", async () => {
    openWithToken();
    wrap(<ResetPasswordForm api={apiWith()} />);
    await waitFor(() => expect(newPassword()).toBeInTheDocument());

    expect(newPassword()).toHaveAttribute("autocomplete", "new-password");
    expect(confirmation()).toHaveAttribute("autocomplete", "new-password");
    fireEvent.click(screen.getByRole("button", { name: "Şifreyi göster" }));
    expect(newPassword()).toHaveAttribute("type", "text");
    expect(confirmation()).toHaveAttribute("type", "text");
  });
});
