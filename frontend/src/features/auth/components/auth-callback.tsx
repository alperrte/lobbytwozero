"use client";

import { useEffect, useId, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useSteamRegistration } from "../hooks/use-steam-registration";
import { emailPattern, registerLimits, usernamePattern } from "../schemas/register.schema";
import type { SteamApi } from "../services/auth-api";
import { callbackResultStore, forgetCallbackResult } from "../services/callback-params";
import { rememberPendingEmail, rememberPendingProof } from "../services/pending-verification";
import { authControlClass, FormField } from "./form-field";
import { ProblemNotice } from "./problem-notice";
import { AuthIcon } from "./auth-icons";

type AuthCallbackProps = {
  /** Test için sahte API adaptörü. */
  api?: Pick<SteamApi, "completeSteamRegistration">;
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
          ? "flex items-start gap-2.5 rounded-control border border-primary/60 bg-primary/10 px-4 py-3 text-sm font-medium text-text-primary"
          : "flex items-start gap-2.5 rounded-control border border-border bg-surface-elevated px-4 py-3 text-sm font-medium text-text-primary"
      }
    >
      <Icon name={tone === "error" ? "info" : "check"} className="mt-0.5 size-4 shrink-0 text-brand-text" />
      <span>{children}</span>
    </p>
  );
}

/** Steam'den dönen yeni kullanıcı: e-posta ve kullanıcı adı ister, kayıt token'ı bellekte kalır. */
function SteamCompleteForm({ token, api }: { token: string; api: AuthCallbackProps["api"] }) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const router = useRouter();
  const { pending, tokenInvalid, problem, serverErrors, retryAfter, cooldown, submit } = useSteamRegistration(api);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [errors, setErrors] = useState<{ username?: "required" | "pattern"; email?: "required" | "invalid" | "tooLong" }>({});

  if (tokenInvalid) {
    return (
      <div className="space-y-4">
        <Notice tone="error">{t("callback.steam.tokenInvalid")}</Notice>
        <Link href="/login" className="inline-flex text-sm font-medium text-brand-text underline underline-offset-2">
          {t("callback.backToLogin")}
        </Link>
      </div>
    );
  }

  const usernameError = serverErrors.username
    ? t(`fields.username.${serverErrors.username}`)
    : errors.username
      ? t(`fields.username.${errors.username}`)
      : undefined;
  const emailError = serverErrors.email
    ? t(`fields.email.${serverErrors.email}`)
    : errors.email
      ? t(`fields.email.${errors.email}`, { max: registerLimits.emailMax })
      : undefined;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const cleanUsername = username.trim();
    const cleanEmail = email.trim();
    const found: typeof errors = {};
    if (cleanUsername === "") found.username = "required";
    else if (!usernamePattern.test(cleanUsername)) found.username = "pattern";
    if (cleanEmail === "") found.email = "required";
    else if (cleanEmail.length > registerLimits.emailMax) found.email = "tooLong";
    else if (!emailPattern.test(cleanEmail)) found.email = "invalid";
    setErrors(found);
    const firstInvalid = found.username ? "username" : found.email ? "email" : null;
    if (firstInvalid) {
      const element = form.elements.namedItem(firstInvalid);
      if (element instanceof HTMLElement) element.focus();
      return;
    }

    const outcome = await submit(token, cleanEmail, cleanUsername);
    if (outcome.status === "registered") {
      rememberPendingEmail(cleanEmail);
      rememberPendingProof(outcome.verificationProof);
      forgetCallbackResult();
      router.push("/verify-email");
    }
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("callback.steam.formLabel")} className="space-y-5">
      <p className="text-sm text-text-secondary">{t("callback.steam.description")}</p>
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />
      <FormField id={`${baseId}-username`} label={t("register.username")} icon="user" error={usernameError} hint={t("register.usernameHint")}>
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
      <FormField id={`${baseId}-email`} label={t("register.email")} icon="mail" error={emailError}>
        {(aria) => (
          <input
            {...aria}
            name="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            spellCheck={false}
            maxLength={registerLimits.emailMax}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className={authControlClass}
          />
        )}
      </FormField>
      <button
        type="submit"
        disabled={pending || cooldown > 0}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-control bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? t("register.submitting") : (<>{t("callback.steam.submit")}<AuthIcon name="arrow" /></>)}
      </button>
    </form>
  );
}

/**
 * Google/Steam dönüş sayfasının içeriği. Auth sonucu URL PARÇASIYLA iletir (`#status=…`, `#error=…`); parça okunur okunmaz
 * adres çubuğundan silinir ve yalnız bellekte tutulur. Sonuca göre: oturum açıldıysa ana sayfaya; Steam'de e-posta
 * doğrulaması gerekiyorsa doğrulama ekranına (kanıt bellekte taşınır); yeni Steam kullanıcısı için kayıt formu; hata ve
 * henüz desteklenmeyen iki aşamalı doğrulama için açıklayıcı mesaj. Sonuç dışarıdan gelen adrese yönlendirme yapmaz.
 */
export function AuthCallback({ api }: AuthCallbackProps) {
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
        <SteamCompleteForm token={result.token} api={api} />
      ) : (
        <div className="space-y-4">
          <Notice tone="error">{t("callback.empty")}</Notice>
          {backToLogin}
        </div>
      );
    case "mfa_required":
    case "mfa_enrollment_required":
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
