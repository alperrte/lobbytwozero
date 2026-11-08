"use client";

import { useEffect, useId, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useExternalRegistration, type ExternalProvider } from "../hooks/use-external-registration";
import { registerLimits, usernamePattern } from "../schemas/register.schema";
import type { ExternalRegistrationApi, TwoFactorApi } from "../services/auth-api";
import { callbackResultStore, forgetCallbackResult } from "../services/callback-params";
import { rememberPendingProof } from "../services/pending-verification";
import { authControlClass, FormField } from "./form-field";
import { ProblemNotice } from "./problem-notice";
import { AuthIcon } from "./auth-icons";
import { TwoFactor } from "./two-factor";

type AuthCallbackProps = {
  /** Test için sahte API adaptörü. */
  api?: ExternalRegistrationApi;
  /** Test için sahte ikinci faktör adaptörü. */
  twoFactorApi?: TwoFactorApi;
};

/** `Auth.callback.errors.<kod>` anahtarı olan dönüş hataları; diğerleri genel hataya düşer. */
const ERROR_CODES: ReadonlySet<string> = new Set([
  "EXTERNAL_AUTH_FAILED",
  "EXTERNAL_EMAIL_UNVERIFIED",
  "ACCOUNT_LINK_REQUIRED",
  "EMAIL_NOT_VERIFIED",
  "ACCOUNT_SUSPENDED",
  "ACCOUNT_BANNED",
  "ACCOUNT_UNAVAILABLE",
  "TWO_FACTOR_ENROLLMENT_REQUIRED",
  "PROVIDER_NOT_CONFIGURED"
]);

function Notice({ tone, children }: { tone: "info" | "error"; children: React.ReactNode }) {
  return (
    <p
      role={tone === "error" ? "alert" : "status"}
      className={
        tone === "error"
          ? "flex items-start gap-2.5 rounded-xl border border-primary/60 bg-primary/10 px-4 py-3 text-sm font-medium text-text-primary"
          : "flex items-start gap-2.5 rounded-xl border border-border bg-surface-elevated px-4 py-3 text-sm font-medium text-text-primary"
      }
    >
      <Icon name={tone === "error" ? "info" : "check"} className="mt-0.5 size-4 shrink-0 text-brand-text" />
      <span>{children}</span>
    </p>
  );
}

/**
 * İlk kez Google/Steam ile gelen kullanıcı: YALNIZ kullanıcı adı sorulur (e-posta sorulmaz; Google'ınkini sunucu bilir,
 * Steam e-posta vermez). Sağlayıcı tarafı Auth'ta doğrulanmıştır; bu form yalnız hesabı tamamlar. Başarıda oturum açılır
 * ya da MFA gerekiyorsa ikinci adım gösterilir. Kayıt token'ı bellekte kalır.
 */
function UsernameForm({
  provider,
  token,
  api,
  twoFactorApi
}: {
  provider: ExternalProvider;
  token: string;
  api: AuthCallbackProps["api"];
  twoFactorApi: AuthCallbackProps["twoFactorApi"];
}) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const router = useRouter();
  const { pending, tokenInvalid, linkRequired, problem, usernameError, retryAfter, cooldown, challenge, clearChallenge, submit } =
    useExternalRegistration(api);
  const [username, setUsername] = useState("");
  const [error, setError] = useState<"required" | "pattern" | undefined>(undefined);

  const backToLogin = (
    <Link href="/login" className="inline-flex text-sm font-medium text-brand-text underline underline-offset-2">
      {t("callback.backToLogin")}
    </Link>
  );

  function finish() {
    forgetCallbackResult();
    router.replace("/");
  }

  if (challenge !== null) {
    return (
      <TwoFactor
        challenge={challenge}
        onAuthenticated={finish}
        onCancel={() => {
          clearChallenge();
          forgetCallbackResult();
          router.replace("/login");
        }}
        api={twoFactorApi}
      />
    );
  }

  if (tokenInvalid) {
    return (
      <div className="space-y-4">
        <Notice tone="error">{t(`callback.${provider}.tokenInvalid`)}</Notice>
        {backToLogin}
      </div>
    );
  }

  if (linkRequired) {
    return (
      <div className="space-y-4">
        <Notice tone="error">{t("callback.errors.ACCOUNT_LINK_REQUIRED")}</Notice>
        {backToLogin}
      </div>
    );
  }

  const fieldError = usernameError
    ? t(`fields.username.${usernameError}`)
    : error
      ? t(`fields.username.${error}`)
      : undefined;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const clean = username.trim();
    const found = clean === "" ? "required" : usernamePattern.test(clean) ? undefined : "pattern";
    setError(found);
    if (found) {
      const element = form.elements.namedItem("username");
      if (element instanceof HTMLElement) element.focus();
      return;
    }
    const outcome = await submit(provider, token, clean);
    if (outcome === "authenticated") {
      finish();
    }
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("callback.username.formLabel")} className="space-y-5">
      <p className="text-sm text-text-secondary">{t(`callback.${provider}.description`)}</p>
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />
      <FormField id={`${baseId}-username`} label={t("register.username")} icon="user" error={fieldError} hint={t("register.usernameHint")}>
        {(aria) => (
          <input
            {...aria}
            name="username"
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={registerLimits.usernameMax}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className={authControlClass}
          />
        )}
      </FormField>
      <button
        type="submit"
        disabled={pending || cooldown > 0}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? t("callback.username.submitting") : (<>{t("callback.username.submit")}<AuthIcon name="arrow" /></>)}
      </button>
    </form>
  );
}

/**
 * Google/Steam dönüş sayfasının içeriği. Auth sonucu URL PARÇASIYLA iletir (`#status=…`, `#error=…`); parça okunur okunmaz
 * adres çubuğundan silinir ve yalnız bellekte tutulur. Sonuca göre: oturum açıldıysa ana sayfaya; eski Steam hesabında e-posta
 * doğrulaması gerekiyorsa doğrulama ekranına (kanıt bellekte taşınır); ilk kez gelen Google/Steam kullanıcısı için YALNIZ kullanıcı
 * adı formu (backend `registration_required` ile bildirir, frontend yeni/mevcut kullanıcıyı tahmin etmez); hata ve
 * henüz desteklenmeyen iki aşamalı doğrulama için açıklayıcı mesaj. Sonuç dışarıdan gelen adrese yönlendirme yapmaz.
 */
export function AuthCallback({ api, twoFactorApi }: AuthCallbackProps) {
  const t = useTranslations("Auth");
  const router = useRouter();
  const result = useSyncExternalStore(
    callbackResultStore.subscribe,
    callbackResultStore.getSnapshot,
    callbackResultStore.getServerSnapshot
  );
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current || result === undefined || result.kind !== "status") {
      return;
    }
    if (result.status === "authenticated") {
      handled.current = true;
      forgetCallbackResult();
      router.replace("/");
    } else if (result.status === "email_verification_required" && result.proof) {
      handled.current = true;
      rememberPendingProof(result.proof);
      forgetCallbackResult();
      router.replace("/verify-email");
    }
  }, [result, router]);

  if (result === undefined) {
    return null; // sunucu görüntüsü / hidrasyon: parça henüz okunmadı
  }

  const backToLogin = (
    <Link href="/login" className="inline-flex text-sm font-medium text-brand-text underline underline-offset-2">
      {t("callback.backToLogin")}
    </Link>
  );

  if (result.kind === "error") {
    const key = ERROR_CODES.has(result.code) ? result.code : "UNKNOWN";
    return (
      <div className="space-y-4">
        <Notice tone="error">{t(`callback.errors.${key}`)}</Notice>
        {backToLogin}
      </div>
    );
  }

  if (result.kind === "empty") {
    return (
      <div className="space-y-4">
        <Notice tone="error">{t("callback.empty")}</Notice>
        {backToLogin}
      </div>
    );
  }

  switch (result.status) {
    case "authenticated":
    case "email_verification_required":
      return <Notice tone="info">{t("callback.processing")}</Notice>;
    case "registration_required":
      return result.token ? (
        // Sağlayıcıyı Auth bildirir (`provider`); eski bağlantılarda yoksa Steam kabul edilir.
        <UsernameForm provider={result.provider ?? "steam"} token={result.token} api={api} twoFactorApi={twoFactorApi} />
      ) : (
        <div className="space-y-4">
          <Notice tone="error">{t("callback.empty")}</Notice>
          {backToLogin}
        </div>
      );
    case "mfa_required":
    case "mfa_enrollment_required":
      if (result.challenge) {
        return (
          <TwoFactor
            challenge={{ kind: result.status === "mfa_required" ? "verify" : "enroll", id: result.challenge }}
            onAuthenticated={() => {
              forgetCallbackResult();
              router.replace("/");
            }}
            onCancel={() => {
              forgetCallbackResult();
              router.replace("/login");
            }}
            api={twoFactorApi}
          />
        );
      }
      return (
        <div className="space-y-4">
          <Notice tone="error">{t("callback.mfa")}</Notice>
          {backToLogin}
        </div>
      );
    case "google_linked":
    case "steam_linked":
      return (
        <div className="space-y-4">
          <Notice tone="info">{t("callback.linked")}</Notice>
          <Link href="/" className="inline-flex text-sm font-medium text-brand-text underline underline-offset-2">
            {t("callback.toHome")}
          </Link>
        </div>
      );
    default:
      return (
        <div className="space-y-4">
          <Notice tone="error">{t("callback.empty")}</Notice>
          {backToLogin}
        </div>
      );
  }
}
