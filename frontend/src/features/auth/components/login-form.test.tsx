import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { LoginApi } from "../services/auth-api";
import { AuthApiError } from "../services/auth-http";
import type { LoginResult } from "../types/auth.types";
import { LoginForm } from "./login-form";

const intlReplace = vi.fn();
const nextReplace = vi.fn();

vi.mock("@/shared/lib/i18n/navigation", () => ({
  useRouter: () => ({ replace: intlReplace })
}));
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
    renderForm(vi.fn().mockRejectedValue(new AuthApiError(401, "INVALID_CREDENTIALS")));
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
    renderForm(vi.fn().mockRejectedValue(new AuthApiError(403, code)));
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(pattern);
  });

  it("MFA gerektiren yanıtta oturum açılmış saymaz ve yönlendirmez", async () => {
    renderForm(
      vi.fn().mockResolvedValue({ status: "MFA_REQUIRED", challengeId: "c1", challengeExpiresAt: "x" } satisfies LoginResult)
    );
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(/iki aşamalı doğrulama istiyor/);
    expect(intlReplace).not.toHaveBeenCalled();
    expect(nextReplace).not.toHaveBeenCalled();
  });

  it("429'da düğmeyi geri sayım boyunca pasif yapar, süre dolunca açar", async () => {
    renderForm(vi.fn().mockRejectedValue(new AuthApiError(429, "TOO_MANY_ATTEMPTS", [], 1)));
    fill();
    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("1 saniye sonra tekrar dene");
    expect(screen.getByRole("button", { name: "1 sn sonra tekrar dene" })).toBeDisabled();

    await waitFor(() => expect(screen.getByRole("button", { name: /Giriş yap/ })).toBeEnabled(), {
      timeout: 3000
    });
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

  it("alanlar doğru autocomplete değerlerini taşır", () => {
    renderForm(vi.fn());
    expect(identifier()).toHaveAttribute("autocomplete", "username");
    expect(password()).toHaveAttribute("autocomplete", "current-password");
  });

  it("İngilizce arayüzde İngilizce hata gösterir", async () => {
    renderForm(vi.fn().mockRejectedValue(new AuthApiError(401, "INVALID_CREDENTIALS")), { locale: "en" });
    fireEvent.change(screen.getByLabelText("Email or username"), { target: { value: "ada" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "x" } });
    fireEvent.click(screen.getByRole("button", { name: /Sign in/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The email, username or password is incorrect.");
  });
});
