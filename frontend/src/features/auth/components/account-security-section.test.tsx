import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { AuthMe } from "../types/auth.types";

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});

const api = vi.hoisted(() => ({
  getMe: vi.fn(),
  setupTotp: vi.fn(),
  activateTotp: vi.fn(),
  disableTotp: vi.fn(),
  startProviderLink: vi.fn(),
  requestPasswordSet: vi.fn(),
  setPassword: vi.fn(),
  requestEmailChange: vi.fn(),
  confirmEmailChange: vi.fn(),
  logoutAll: vi.fn()
}));
vi.mock("../services/auth-api", () => ({ authApi: api }));

import { AccountSecuritySection } from "./account-security-section";

const base: AuthMe = {
  accountId: "1",
  roles: ["USER"],
  authMethods: ["PASSWORD"],
  accessTokenExpiresAt: "2030-01-01T00:00:00Z",
  accountTier: "A",
  passwordSet: true,
  providers: [],
  totpEnabled: false,
  registrationMethod: "PASSWORD"
};

function renderSection(me: Partial<AuthMe> = {}) {
  api.getMe.mockResolvedValue({ ...base, ...me });
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <AccountSecuritySection />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  for (const mock of Object.values(api)) mock.mockReset();
});
afterEach(cleanup);

describe("AccountSecuritySection", () => {
  it("hesap düzeyini ve nedenini gösterir", async () => {
    renderSection();
    expect(await screen.findByText("Standart hesap (A)")).toBeInTheDocument();
    expect(screen.getByText(/bazı özellikler kısıtlıdır/)).toBeInTheDocument();
  });

  it("türü bilinmeyen (eski Auth) yanıtta hata ve tekrar deneme gösterir", async () => {
    api.getMe.mockResolvedValue({ ...base, accountTier: undefined });
    render(
      <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
        <AccountSecuritySection />
      </NextIntlClientProvider>
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Güvenlik bilgisi alınamadı");
  });

  it("2FA: kurulum → kodla etkinleştirme → kurtarma kodları yalnız bir kez gösterilir", async () => {
    api.setupTotp.mockResolvedValue({ secret: "ABCDSECRET", otpauthUri: "otpauth://totp/x" });
    api.activateTotp.mockResolvedValue({ recoveryCodes: ["rc-1", "rc-2"] });
    renderSection();

    fireEvent.click(await screen.findByRole("button", { name: "Kurulumu başlat" }));
    expect(await screen.findByText("ABCDSECRET")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Uygulamadaki 6 haneli kodu yaz"), { target: { value: " 123456 " } });
    fireEvent.click(screen.getByRole("button", { name: "Etkinleştir" }));

    expect(await screen.findByText("rc-1")).toBeInTheDocument();
    expect(api.activateTotp).toHaveBeenCalledWith("123456");

    api.getMe.mockResolvedValue({ ...base, accountTier: "B", totpEnabled: true });
    fireEvent.click(screen.getByRole("button", { name: "Kodları kaydettim" }));
    expect(await screen.findByText("İki adımlı doğrulama açık.")).toBeInTheDocument();
    expect(screen.queryByText("rc-1")).toBeNull();
  });

  it("2FA kapatma: parola + 6 haneli kod `code`, diğer her şey `recoveryCode` olarak gider", async () => {
    api.disableTotp.mockResolvedValue(undefined);
    renderSection({ accountTier: "B", totpEnabled: true });

    // Aynı etiket 2FA kapatma, bağlama ve e-posta formlarında da var; ilki 2FA kapatma panelidir.
    fireEvent.change((await screen.findAllByLabelText("Parolan"))[0], { target: { value: "gizli-parola-123456" } });
    fireEvent.change(screen.getAllByLabelText("Doğrulama kodu (6 hane) ya da kurtarma kodu")[0], { target: { value: "abcd-efgh" } });
    fireEvent.click(screen.getByRole("button", { name: "Kapat" }));

    await waitFor(() =>
      expect(api.disableTotp).toHaveBeenCalledWith({ password: "gizli-parola-123456", recoveryCode: "abcd-efgh" })
    );
  });

  it("2FA hatası (rol zorunlu) kullanıcıya anlaşılır mesajla gösterilir", async () => {
    api.disableTotp.mockRejectedValue(new ApiHttpError(403, { code: "TWO_FACTOR_REQUIRED_FOR_ROLE" }));
    renderSection({ accountTier: "B", totpEnabled: true, passwordSet: false });

    fireEvent.change((await screen.findAllByLabelText("Doğrulama kodu (6 hane) ya da kurtarma kodu"))[0], {
      target: { value: "123456" }
    });
    fireEvent.click(screen.getByRole("button", { name: "Kapat" }));

    expect(await screen.findByText(/Yönetici ve moderatör hesaplarında/)).toBeInTheDocument();
    expect(api.disableTotp).toHaveBeenCalledWith({ code: "123456" });
  });

  it("parolasız hesapta bağlama kapalıdır ve nedeni yazar; parola belirleme paneli görünür", async () => {
    renderSection({ accountTier: "C", passwordSet: false, registrationMethod: "GOOGLE", providers: ["google"] });

    expect(await screen.findByText("Bağlamak için önce parola belirlemelisin.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Parola belirle" })).toBeInTheDocument();
    expect(screen.getByText("Bağlı")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bağla" })).toBeDisabled();
  });

  it("parolası olan hesapta bağlama, parola ile başlar ve dönen adrese yönlendirir", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign });
    api.startProviderLink.mockResolvedValue("https://accounts.example/auth");
    renderSection({ providers: ["google"] });

    fireEvent.click(await screen.findByRole("button", { name: "Bağla" }));
    fireEvent.change(screen.getAllByLabelText("Parolan")[0], { target: { value: "gizli-parola-123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Doğrula ve bağla" }));

    await waitFor(() => expect(api.startProviderLink).toHaveBeenCalledWith("steam", { password: "gizli-parola-123456" }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith("https://accounts.example/auth"));
    vi.unstubAllGlobals();
  });

  it("Google ile açılan hesapta e-posta değişikliği kilitlidir", async () => {
    renderSection({ accountTier: "C", registrationMethod: "GOOGLE", passwordSet: true });
    expect(await screen.findByText(/e-posta adresi değiştirilemez/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Yeni e-posta adresi")).toBeNull();
  });

  it("parolası da 2FA'sı da olmayan hesapta e-posta değişikliği yerine yönlendirme gösterilir", async () => {
    renderSection({ passwordSet: false, totpEnabled: false, registrationMethod: "STEAM", accountTier: "C" });
    expect(await screen.findByText(/önce parola belirle ya da iki adımlı doğrulamayı aç/)).toBeInTheDocument();
  });

  it("e-posta değişikliği: istek → yeni adrese gelen kodla onay → oturumlar kapandı mesajı", async () => {
    api.requestEmailChange.mockResolvedValue(undefined);
    api.confirmEmailChange.mockResolvedValue(undefined);
    renderSection();

    fireEvent.change(await screen.findByLabelText("Yeni e-posta adresi"), { target: { value: " yeni@ltz.com " } });
    fireEvent.change(screen.getAllByLabelText("Parolan")[0], { target: { value: "gizli-parola-123456" } });
    fireEvent.click(screen.getByRole("button", { name: "Kod gönder" }));

    await waitFor(() =>
      expect(api.requestEmailChange).toHaveBeenCalledWith({ newEmail: "yeni@ltz.com", password: "gizli-parola-123456" })
    );
    fireEvent.change(await screen.findByLabelText("Kod"), { target: { value: "654321" } });
    fireEvent.click(screen.getByRole("button", { name: "Adresi değiştir" }));

    expect(await screen.findByText(/tüm oturumların kapandı/)).toBeInTheDocument();
    expect(api.confirmEmailChange).toHaveBeenCalledWith("654321");
    expect(screen.getByRole("link", { name: "Girişe git" })).toHaveAttribute("href", "/login");
  });

  it("parola belirleme: kod iste → kod + yeni parola; eşleşmezse istek atılmaz", async () => {
    api.requestPasswordSet.mockResolvedValue(undefined);
    api.setPassword.mockResolvedValue(undefined);
    renderSection({ accountTier: "C", passwordSet: false, registrationMethod: "GOOGLE" });

    fireEvent.click(await screen.findByRole("button", { name: "Kod gönder" }));
    await waitFor(() => expect(api.requestPasswordSet).toHaveBeenCalledWith(undefined));

    fireEvent.change(await screen.findByLabelText("E-postana gelen kod"), { target: { value: "111222" } });
    fireEvent.change(screen.getByLabelText("Yeni parola (en az 8 karakter)"), { target: { value: "Bir-Uzun-Parola-12345" } });
    fireEvent.change(screen.getByLabelText("Yeni parola (tekrar)"), { target: { value: "Baska-Bir-Parola-12345" } });
    fireEvent.click(screen.getByRole("button", { name: "Parolayı belirle" }));
    expect(await screen.findByText("Parolalar aynı değil.")).toBeInTheDocument();
    expect(api.setPassword).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Yeni parola (tekrar)"), { target: { value: "Bir-Uzun-Parola-12345" } });
    fireEvent.click(screen.getByRole("button", { name: "Parolayı belirle" }));
    await waitFor(() =>
      expect(api.setPassword).toHaveBeenCalledWith("111222", "Bir-Uzun-Parola-12345", "Bir-Uzun-Parola-12345")
    );
    expect(await screen.findByText(/tüm oturumların kapandı/)).toBeInTheDocument();
  });

  it("tüm oturumları kapatma ikinci tıklamada onaylanır ve yeniden girişe yönlendirir", async () => {
    api.logoutAll.mockResolvedValue(undefined);
    renderSection();

    fireEvent.click(await screen.findByRole("button", { name: "Tüm cihazlardan çık" }));
    expect(api.logoutAll).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: /Evet, hepsini kapat/ }));

    expect(await screen.findByText(/tüm oturumların kapandı/)).toBeInTheDocument();
    expect(api.logoutAll).toHaveBeenCalledTimes(1);
  });
});
