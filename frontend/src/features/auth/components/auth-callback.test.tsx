import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { forgetCallbackResult } from "../services/callback-params";
import { clearPendingEmail, clearPendingProof, pendingProofStore } from "../services/pending-verification";
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

  it("mfa_required + challenge: ikinci adım formunu gösterir; doğrulanınca ana sayfaya gider", async () => {
    open("#status=mfa_required&challenge=abc");
    const verifyTwoFactor = vi.fn().mockResolvedValue(undefined);
    wrap(
      <AuthCallback twoFactorApi={{ verifyTwoFactor, startTwoFactorEnrollment: vi.fn(), completeTwoFactorEnrollment: vi.fn() }} />
    );

    const code = await screen.findByLabelText("Doğrulama kodu");
    expect(replace).not.toHaveBeenCalled();
    expect(window.location.hash).toBe("");

    fireEvent.change(code, { target: { value: "654321" } });
    fireEvent.click(screen.getByRole("button", { name: /Doğrula/ }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(verifyTwoFactor).toHaveBeenCalledWith("abc", { code: "654321" });
  });

  it("mfa durumu challenge'sız gelirse oturum açılmış saymaz ve açıklayıcı mesaj verir", async () => {
    open("#status=mfa_required");
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

describe("AuthCallback: ilk girişte kullanıcı adı (Google ve Steam)", () => {
  const username = () => screen.getByLabelText("Kullanıcı adı");
  const submit = () => screen.getByRole("button", { name: /Devam et/ });
  const AUTHENTICATED = { status: "AUTHENTICATED" as const, accessTokenExpiresAt: "2030-01-01T00:00:00Z" };

  it.each([
    ["google", /Google hesabınla ilk kez geliyorsun/],
    ["steam", /Steam hesabınla ilk kez geliyorsun/]
  ])("%s: yalnız kullanıcı adı sorulur (e-posta alanı yoktur); başarıda oturum açılır ve ana sayfaya gidilir", async (provider, text) => {
    open(`#status=registration_required&provider=${provider}&token=${TOKEN}`);
    const completeExternalRegistration = vi.fn().mockResolvedValue(AUTHENTICATED);
    wrap(<AuthCallback api={{ completeExternalRegistration }} />);
    await waitFor(() => expect(username()).toBeInTheDocument());
    expect(window.location.hash).toBe("");
    expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.queryByLabelText("E-posta")).toBeNull();
    expect(screen.queryByLabelText("Şifre")).toBeNull();

    fireEvent.change(username(), { target: { value: " ada_oyuncu " } });
    fireEvent.click(submit());

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(completeExternalRegistration).toHaveBeenCalledWith(provider, TOKEN, "ada_oyuncu");
    expect(push).not.toHaveBeenCalled();
    expect(window.location.href).not.toContain(TOKEN);
  });

  it("sağlayıcı bildirilmemişse (eski bağlantı) Steam kabul edilir", async () => {
    open(`#status=registration_required&token=${TOKEN}`);
    const completeExternalRegistration = vi.fn().mockResolvedValue(AUTHENTICATED);
    wrap(<AuthCallback api={{ completeExternalRegistration }} />);
    await waitFor(() => expect(username()).toBeInTheDocument());

    fireEvent.change(username(), { target: { value: "steam_ada" } });
    fireEvent.click(submit());

    await waitFor(() => expect(completeExternalRegistration).toHaveBeenCalledWith("steam", TOKEN, "steam_ada"));
  });

  it("bilinmeyen sağlayıcı değeri geçersiz sayılır; form açılmaz", async () => {
    open(`#status=registration_required&provider=evil&token=${TOKEN}`);
    wrap(<AuthCallback api={{ completeExternalRegistration: vi.fn() }} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("yalnız Google veya Steam ile giriş dönüşü içindir");
    expect(screen.queryByLabelText("Kullanıcı adı")).toBeNull();
  });

  it("boş ve geçersiz kullanıcı adında hata gösterir; istek atmaz", async () => {
    open(`#status=registration_required&provider=google&token=${TOKEN}`);
    const completeExternalRegistration = vi.fn();
    wrap(<AuthCallback api={{ completeExternalRegistration }} />);
    await waitFor(() => expect(username()).toBeInTheDocument());

    fireEvent.click(submit());
    expect(await screen.findByText("Bir kullanıcı adı belirle.")).toBeInTheDocument();
    expect(username()).toHaveFocus();

    fireEvent.change(username(), { target: { value: "ada-ada" } });
    fireEvent.click(submit());
    expect(await screen.findByText(/yalnız harf, rakam ve alt çizgi/)).toBeInTheDocument();
    expect(completeExternalRegistration).not.toHaveBeenCalled();
  });

  it("USERNAME_TAKEN kullanıcı adı alanında gösterilir; form açık kalır ve yeniden denenebilir", async () => {
    open(`#status=registration_required&provider=google&token=${TOKEN}`);
    const completeExternalRegistration = vi
      .fn()
      .mockRejectedValueOnce(new ApiHttpError(409, { code: "USERNAME_TAKEN" }))
      .mockResolvedValueOnce(AUTHENTICATED);
    wrap(<AuthCallback api={{ completeExternalRegistration }} />);
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "alinmis_ad" } });

    fireEvent.click(submit());

    expect(await screen.findByText("Bu kullanıcı adı kullanılıyor.")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();

    fireEvent.change(username(), { target: { value: "bos_ad" } });
    fireEvent.click(submit());
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(completeExternalRegistration).toHaveBeenNthCalledWith(2, "google", TOKEN, "bos_ad");
  });

  it("sunucu doğrulama hatası (VALIDATION_FAILED, username) alanda gösterilir", async () => {
    open(`#status=registration_required&provider=steam&token=${TOKEN}`);
    wrap(
      <AuthCallback
        api={{
          completeExternalRegistration: vi
            .fn()
            .mockRejectedValue(new ApiHttpError(400, { code: "VALIDATION_FAILED", fieldErrors: [{ field: "username", code: "Pattern" }] }))
        }}
      />
    );
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "steam_ada" } });

    fireEvent.click(submit());

    expect(await screen.findByText("Kullanıcı adı geçersiz.")).toBeInTheDocument();
  });

  it("REGISTRATION_TOKEN_INVALID gelince form yerine sağlayıcıya özgü yeniden başlatma yönlendirmesi gösterir", async () => {
    open(`#status=registration_required&provider=google&token=${TOKEN}`);
    wrap(
      <AuthCallback
        api={{ completeExternalRegistration: vi.fn().mockRejectedValue(new ApiHttpError(400, { code: "REGISTRATION_TOKEN_INVALID" })) }}
      />
    );
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "google_ada" } });

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("Google ile girişi yeniden başlat");
    expect(screen.queryByLabelText("Kullanıcı adı")).toBeNull();
    expect(screen.getByRole("link", { name: "Giriş sayfasına dön" })).toHaveAttribute("href", "/login");
  });

  it("ACCOUNT_LINK_REQUIRED (e-posta bu arada alındı) birleştirme yapmaz, açıklayıp girişe yönlendirir", async () => {
    open(`#status=registration_required&provider=google&token=${TOKEN}`);
    wrap(
      <AuthCallback
        api={{ completeExternalRegistration: vi.fn().mockRejectedValue(new ApiHttpError(409, { code: "ACCOUNT_LINK_REQUIRED" })) }}
      />
    );
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "google_ada" } });

    fireEvent.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent("zaten bir hesap var");
    expect(replace).not.toHaveBeenCalled();
  });

  it("MFA gerekiyorsa oturum açılmış sayılmaz: ikinci adım (kurulum) gösterilir, ana sayfaya gidilmez", async () => {
    open(`#status=registration_required&provider=steam&token=${TOKEN}`);
    const completeExternalRegistration = vi
      .fn()
      .mockResolvedValue({ status: "MFA_ENROLLMENT_REQUIRED", challengeId: "kurulum-1", challengeExpiresAt: "2030-01-01T00:00:00Z" });
    const startTwoFactorEnrollment = vi.fn().mockResolvedValue({ secret: "ABCDEFGHIJKLMNOP", otpauthUri: "otpauth://totp/LTZ:x?secret=ABC" });
    wrap(
      <AuthCallback
        api={{ completeExternalRegistration }}
        twoFactorApi={{ verifyTwoFactor: vi.fn(), startTwoFactorEnrollment, completeTwoFactorEnrollment: vi.fn() }}
      />
    );
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "steam_ada" } });

    fireEvent.click(submit());

    const start = await screen.findByRole("button", { name: tr.Auth.mfa.enroll.start });
    expect(screen.queryByLabelText("Kullanıcı adı")).toBeNull();
    expect(replace).not.toHaveBeenCalled();
    fireEvent.click(start);
    await waitFor(() => expect(startTwoFactorEnrollment).toHaveBeenCalledWith("kurulum-1"));
    expect(replace).not.toHaveBeenCalled();
  });

  it("çift tıklamada tek istek gider", async () => {
    open(`#status=registration_required&provider=google&token=${TOKEN}`);
    let release: (value: typeof AUTHENTICATED) => void = () => undefined;
    const completeExternalRegistration = vi.fn().mockReturnValue(new Promise((resolve) => (release = resolve)));
    wrap(<AuthCallback api={{ completeExternalRegistration }} />);
    await waitFor(() => expect(username()).toBeInTheDocument());
    fireEvent.change(username(), { target: { value: "google_ada" } });

    const button = submit();
    fireEvent.click(button);
    fireEvent.click(button);
    release(AUTHENTICATED);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(completeExternalRegistration).toHaveBeenCalledTimes(1);
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
