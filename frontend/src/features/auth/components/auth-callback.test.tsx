import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { forgetCallbackResult } from "../services/callback-params";
import {
  clearPendingEmail,
  clearPendingProof,
  pendingEmailStore,
  pendingProofStore
} from "../services/pending-verification";
import { AuthCallback } from "./auth-callback";
import { SocialButtons } from "./social-buttons";

const replace = vi.fn();
const push = vi.fn();

vi.mock("@/shared/lib/i18n/navigation", async () => {
  const { createElement } = await import("react");
  return {
    useRouter: () => ({ push, replace }),
    Link: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) =>
      createElement("a", { href, ...rest }, children)
  };
});

const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_";

function open(hash: string) {
  window.history.replaceState(null, "", `/auth/callback${hash}`);
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
  push.mockReset();
  forgetCallbackResult();
  clearPendingEmail();
  clearPendingProof();
  window.history.replaceState(null, "", "/");
});

describe("AuthCallback", () => {
  it("authenticated: ana sayfaya yönlendirir ve parçayı adresten siler", async () => {
    open("#status=authenticated");
    wrap(<AuthCallback />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(window.location.hash).toBe("");
    expect(screen.getByRole("status")).toHaveTextContent("tamamlanıyor");
  });

  it("email_verification_required: kanıtı bellekte bırakıp doğrulama ekranına geçer (URL'de taşımaz)", async () => {
    open(`#status=email_verification_required&proof=${TOKEN}`);
    wrap(<AuthCallback />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/verify-email"));
    expect(pendingProofStore.getSnapshot()).toBe(TOKEN);
    expect(window.location.href).not.toContain(TOKEN);
  });

  it.each([
    ["EXTERNAL_AUTH_FAILED", /doğrulanamadı/],
    ["ACCOUNT_LINK_REQUIRED", /zaten bir hesap var/],
    ["EXTERNAL_EMAIL_UNVERIFIED", /doğrulanmamış/],
    ["ACCOUNT_BANNED", /yasaklandı/],
    ["PROVIDER_NOT_CONFIGURED", /kullanılamıyor/],
    ["TWO_FACTOR_ENROLLMENT_REQUIRED", /iki aşamalı doğrulama kurulumu/],
    ["BILINMEYEN_KOD", /ters gitti/]
  ])("#error=%s için çevrilmiş mesaj ve giriş bağlantısı gösterir", async (code, pattern) => {
    open(`#error=${code}`);
    wrap(<AuthCallback />);

    expect(await screen.findByRole("alert")).toHaveTextContent(pattern);
    expect(screen.getByRole("link", { name: "Giriş sayfasına dön" })).toHaveAttribute("href", "/login");
    expect(replace).not.toHaveBeenCalled();
  });

  it("mfa durumlarında oturum açılmış saymaz ve açıklayıcı mesaj verir", async () => {
    open("#status=mfa_required&challenge=abc");
    wrap(<AuthCallback />);

    expect(await screen.findByRole("alert")).toHaveTextContent("iki aşamalı doğrulama istiyor");
    expect(replace).not.toHaveBeenCalled();
  });

  it("parçasız ya da geçersiz parçalı ziyaret açıklayıcı hata gösterir; dış adrese yönlendirmez", async () => {
    open("#status=authenticated&redirect=https://evil.example");
    wrap(<AuthCallback />);

    expect(await screen.findByRole("alert")).toHaveTextContent("yalnız Google veya Steam ile giriş dönüşü içindir");
    expect(replace).not.toHaveBeenCalled();
    expect(push).not.toHaveBeenCalled();
  });

  it("bağlama sonucunda bilgi ve ana sayfa bağlantısı gösterir", async () => {
    open("#status=google_linked");
    wrap(<AuthCallback />);

    expect(await screen.findByRole("status")).toHaveTextContent("Hesabın bağlandı.");
    expect(screen.getByRole("link", { name: "Ana sayfaya git" })).toHaveAttribute("href", "/");
  });
});

describe("AuthCallback: Steam ile yeni hesap", () => {
  const username = () => screen.getByLabelText("Kullanıcı adı");
  const email = () => screen.getByLabelText("E-posta");
  const submit = () => screen.getByRole("button", { name: /Hesabı oluştur/ });

  it("kayıt formunu gösterir; başarıda e-posta ve kanıtı bellekte bırakıp doğrulama ekranına geçer", async () => {
    open(`#status=registration_required&token=${TOKEN}`);
    const completeSteamRegistration = vi.fn().mockResolvedValue({ verificationProof: "kanit-123" });
    wrap(<AuthCallback api={{ completeSteamRegistration }} />);
    await waitFor(() => expect(username()).toBeInTheDocument());
    expect(window.location.hash).toBe("");

    fireEvent.change(username(), { target: { value: "steam_ada" } });
    fireEvent.change(email(), { target: { value: " ada@example.com " } });
    fireEvent.click(submit());

    await waitFor(() => expect(push).toHaveBeenCalledWith("/verify-email"));
    expect(completeSteamRegistration).toHaveBeenCalledWith(TOKEN, "ada@example.com", "steam_ada");
    expect(pendingEmailStore.getSnapshot()).toBe("ada@example.com");
    expect(pendingProofStore.getSnapshot()).toBe("kanit-123");
    expect(window.location.href).not.toContain("kanit-123");
  });

  it("boş ve geçersiz alanlarda hata gösterir; istek atmaz", async () => {
    open(`#status=registration_required&token=${TOKEN}`);
    const completeSteamRegistration = vi.fn();
    wrap(<AuthCallback api={{ completeSteamRegistration }} />);
    await waitFor(() => expect(username()).toBeInTheDocument());

    fireEvent.click(submit());
    expect(await screen.findByText("Bir kullanıcı adı belirle.")).toBeInTheDocument();
    expect(username()).toHaveFocus();

    fireEvent.change(username(), { target: { value: "ada-ada" } });
    fireEvent.change(email(), { target: { value: "ada@example.com" } });
    fireEvent.click(submit());
    expect(await screen.findByText(/yalnız harf, rakam ve alt çizgi/)).toBeInTheDocument();
    expect(completeSteamRegistration).not.toHaveBeenCalled();
  });

  it("USERNAME_TAKEN kullanıcı adı alanında gösterilir", async () => {
    open(`#status=registration_required&token=${TOKEN}`);
    wrap(
      <AuthCallback
        api={{ completeSteamRegistration: vi.fn().mockRejectedValue(new ApiHttpError(409, { code: "USERNAME_TAKEN" })) }}
      />
    );
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "steam_ada" } });
    fireEvent.change(email(), { target: { value: "ada@example.com" } });

    fireEvent.click(submit());

    expect(await screen.findByText("Bu kullanıcı adı kullanılıyor.")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });

  it("REGISTRATION_TOKEN_INVALID gelince form yerine yeniden başlatma yönlendirmesi gösterir", async () => {
    open(`#status=registration_required&token=${TOKEN}`);
    wrap(
      <AuthCallback
        api={{ completeSteamRegistration: vi.fn().mockRejectedValue(new ApiHttpError(400, { code: "REGISTRATION_TOKEN_INVALID" })) }}
      />
    );
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "steam_ada" } });
    fireEvent.change(email(), { target: { value: "ada@example.com" } });

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("Kayıt bağlantısı geçersiz ya da süresi dolmuş");
    expect(screen.queryByLabelText("Kullanıcı adı")).toBeNull();
  });
});

describe("SocialButtons", () => {
  it("Google ve Steam bağlantıları tarayıcı gezinmesi olarak Gateway'in start adreslerine gider", () => {
    wrap(<SocialButtons mode="login" />);

    expect(screen.getByRole("link", { name: /Google ile giriş/ }).getAttribute("href")).toMatch(
      /\/api\/v1\/auth\/oauth\/google\/start$/
    );
    expect(screen.getByRole("link", { name: /Steam ile giriş/ }).getAttribute("href")).toMatch(
      /\/api\/v1\/auth\/oauth\/steam\/start$/
    );
  });

  it("kayıt kipinde metinler 'kayıt' olur", () => {
    wrap(<SocialButtons mode="register" />);
    expect(screen.getByRole("link", { name: /Google ile kayıt/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Steam ile kayıt/ })).toBeInTheDocument();
  });
});
