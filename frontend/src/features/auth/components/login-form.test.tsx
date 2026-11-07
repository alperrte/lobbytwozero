import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { LoginApi } from "../services/auth-api";
import { clearPendingEmail, pendingEmailStore } from "../services/pending-verification";
import { ApiHttpError } from "@/shared/lib/api/client";
import type { LoginResult } from "../types/auth.types";
import { LoginForm } from "./login-form";

const intlReplace = vi.fn();
const nextReplace = vi.fn();

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ replace: intlReplace }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: nextReplace })
}));

const OK: LoginResult = { status: "AUTHENTICATED", accessTokenExpiresAt: "2026-11-06T10:00:00Z" };

function renderForm(login: LoginApi["login"], props: { next?: string | null; locale?: "tr" | "en" } = {}) {
  const { locale = "tr", next } = props;
  return render(
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en} timeZone="Europe/Istanbul">
      <LoginForm api={{ login }} next={next} />
    </NextIntlClientProvider>
  );
}

const identifier = () => screen.getByLabelText("E-posta veya kullanıcı adı");
const password = () => screen.getByLabelText("Şifre") as HTMLInputElement;
const submit = () => screen.getByRole("button", { name: /Giriş yap/ });

function fill(id = "ada", pass = "dogru-parola-123") {
  fireEvent.change(identifier(), { target: { value: id } });
  fireEvent.change(password(), { target: { value: pass } });
}

beforeEach(() => {
  intlReplace.mockReset();
  nextReplace.mockReset();
});

describe("LoginForm", () => {
  it("boş gönderimde alan hatalarını gösterir ve ilk hatalı alana odaklanır; API çağrılmaz", async () => {
    const login = vi.fn();
    renderForm(login);

    fireEvent.click(submit());

    expect(await screen.findByText("E-posta adresini veya kullanıcı adını gir.")).toBeInTheDocument();
    expect(screen.getByText("Şifreni gir.")).toBeInTheDocument();
    expect(identifier()).toHaveAttribute("aria-invalid", "true");
    expect(identifier()).toHaveFocus();
    expect(login).not.toHaveBeenCalled();
  });

  it("başarıda ana sayfaya gider ve kimliği kırpılmış, parolayı olduğu gibi gönderir", async () => {
    const login = vi.fn().mockResolvedValue(OK);
    renderForm(login);

    fill("  ada  ", " parola ");
    fireEvent.click(submit());

    await waitFor(() => expect(intlReplace).toHaveBeenCalledWith("/"));
    expect(login).toHaveBeenCalledWith("ada", " parola ");
    expect(nextReplace).not.toHaveBeenCalled();
  });

  it("güvenli next varsa oraya gider", async () => {
    renderForm(vi.fn().mockResolvedValue(OK), { next: "/tr/ayarlar" });
    fill();
    fireEvent.click(submit());

    await waitFor(() => expect(nextReplace).toHaveBeenCalledWith("/tr/ayarlar"));
    expect(intlReplace).not.toHaveBeenCalled();
  });

  it.each(["https://evil.com", "//evil.com", "/\\evil.com"])("güvensiz next (%s) yok sayılır", async (next) => {
    renderForm(vi.fn().mockResolvedValue(OK), { next });
    fill();
    fireEvent.click(submit());

    await waitFor(() => expect(intlReplace).toHaveBeenCalledWith("/"));
    expect(nextReplace).not.toHaveBeenCalled();
  });

  it("hatalı kimlikte tek ve ayrım yapmayan mesaj gösterir; parola alanı temizlenir", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(401, { code: "INVALID_CREDENTIALS" })));
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("E-posta, kullanıcı adı veya şifre hatalı.");
    expect(password().value).toBe("");
    expect(intlReplace).not.toHaveBeenCalled();
  });

  it.each([
    ["EMAIL_NOT_VERIFIED", /henüz doğrulanmadı/],
    ["ACCOUNT_SUSPENDED", /askıya alındı/],
    ["ACCOUNT_BANNED", /yasaklandı/],
    ["ACCOUNT_DELETION_PENDING", /silinme süreci/],
    ["TWO_FACTOR_ENROLLMENT_REQUIRED", /iki aşamalı doğrulama kurulumu/],
    ["CSRF_TOKEN_INVALID", /Sayfayı yenileyip/],
    ["SERVICE_BUSY", /yoğun/],
    ["NETWORK", /Sunucuya ulaşılamadı/],
    ["UNKNOWN", /ters gitti/]
  ] as const)("%s için ayrı mesaj gösterir", async (code, pattern) => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(403, { code })));
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(pattern);
  });

  it("silinme sürecindeki hesapta 'silme talebinden vazgeç' bağlantısı çıkar", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(403, { code: "ACCOUNT_DELETION_PENDING" })));
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(/vazgeçebilirsin/);
    expect(screen.getByRole("link", { name: "Silme talebinden vazgeç" })).toHaveAttribute("href", "/account/cancel-deletion");
  });

  it("e-postası doğrulanmamış hesapta doğrulama bağlantısı çıkar ve e-posta bellekte taşınır", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(403, { code: "EMAIL_NOT_VERIFIED" })));
    fill("ada@example.com");
    fireEvent.click(submit());

    const link = await screen.findByRole("link", { name: "E-postamı doğrula" });
    expect(link).toHaveAttribute("href", "/verify-email");
    fireEvent.click(link);
    expect(pendingEmailStore.getSnapshot()).toBe("ada@example.com");
    clearPendingEmail();
  });

  it("MFA gerektiren yanıtta oturum açılmış saymaz; ikinci adım formunu gösterir, doğrulanınca yönlendirir", async () => {
    const verifyTwoFactor = vi.fn().mockResolvedValue(undefined);
    render(
      <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
        <LoginForm
          api={{
            login: vi.fn().mockResolvedValue({ status: "MFA_REQUIRED", challengeId: "c1", challengeExpiresAt: "x" } satisfies LoginResult)
          }}
          twoFactorApi={{ verifyTwoFactor, startTwoFactorEnrollment: vi.fn(), completeTwoFactorEnrollment: vi.fn() }}
        />
      </NextIntlClientProvider>
    );
    fill();
    fireEvent.click(submit());

    const code = await screen.findByLabelText("Doğrulama kodu");
    expect(intlReplace).not.toHaveBeenCalled();
    expect(nextReplace).not.toHaveBeenCalled();

    fireEvent.change(code, { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /Doğrula/ }));

    await waitFor(() => expect(intlReplace).toHaveBeenCalledWith("/"));
    expect(verifyTwoFactor).toHaveBeenCalledWith("c1", { code: "123456" });
  });

  it("429'da düğmeyi geri sayım boyunca pasif yapar, süre dolunca açar", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 1 })));
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("1 saniye sonra tekrar dene");
    expect(screen.getByRole("button", { name: "1 saniye sonra tekrar dene" })).toBeDisabled();

    await waitFor(() => expect(screen.getByRole("button", { name: /Giriş yap/ })).toBeEnabled(), {
      timeout: 3000
    });
  });

  it("uzun bekleme süresini dakika olarak gösterir", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_ATTEMPTS", retryAfterSeconds: 900 })));
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("15 dakika sonra tekrar dene");
    expect(screen.getByRole("button", { name: "15 dakika sonra tekrar dene" })).toBeDisabled();
  });

  it("gönderim sürerken çift gönderimi engeller", async () => {
    let resolve!: (value: LoginResult) => void;
    const login = vi.fn(() => new Promise<LoginResult>((r) => (resolve = r)));
    renderForm(login);
    fill();

    fireEvent.click(submit());
    await waitFor(() => expect(screen.getByRole("button", { name: /Giriş yapılıyor/ })).toBeDisabled());
    fireEvent.submit(screen.getByRole("form", { name: "Giriş formu" }));
    expect(login).toHaveBeenCalledTimes(1);

    await act(async () => resolve(OK));
    await waitFor(() => expect(intlReplace).toHaveBeenCalled());
  });

  it("şifre göster/gizle düğmesi alan türünü değiştirir ve durumu bildirir", () => {
    renderForm(vi.fn());
    const toggle = screen.getByRole("button", { name: "Şifreyi göster" });

    expect(password()).toHaveAttribute("type", "password");
    expect(toggle).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(toggle);

    expect(password()).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Şifreyi gizle" })).toHaveAttribute("aria-pressed", "true");
  });

  it("kayıt sayfasına giden bağlantı vardır; doğrulama sonrası bilgi kutusu yalnız verified ile görünür", () => {
    const { unmount } = renderForm(vi.fn());
    expect(screen.getByRole("link", { name: "Kayıt ol" })).toHaveAttribute("href", "/register");
    expect(screen.queryByText("E-posta adresin doğrulandı. Şimdi giriş yapabilirsin.")).toBeNull();
    unmount();

    render(
      <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
        <LoginForm api={{ login: vi.fn() }} verified />
      </NextIntlClientProvider>
    );
    expect(screen.getByRole("status")).toHaveTextContent("E-posta adresin doğrulandı. Şimdi giriş yapabilirsin.");
  });

  it("alanlar doğru autocomplete değerlerini taşır", () => {
    renderForm(vi.fn());
    expect(identifier()).toHaveAttribute("autocomplete", "username");
    expect(password()).toHaveAttribute("autocomplete", "current-password");
  });

  it("İngilizce arayüzde İngilizce hata gösterir", async () => {
    renderForm(vi.fn().mockRejectedValue(new ApiHttpError(401, { code: "INVALID_CREDENTIALS" })), { locale: "en" });
    fireEvent.change(screen.getByLabelText("Email or username"), { target: { value: "ada" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "x" } });
    fireEvent.click(screen.getByRole("button", { name: /Sign in/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The email, username or password is incorrect.");
  });
});
