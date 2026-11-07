"use client";

import { useId, useState, useSyncExternalStore, type ChangeEvent, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useVerifyEmail } from "../hooks/use-verify-email";
import { registerLimits } from "../schemas/register.schema";
import {
  firstInvalidVerifyField,
  normalizeVerificationCode,
  validateResendEmail,
  validateVerifyEmail,
  verifyCodeLength,
  type VerifyErrors,
  type VerifyField,
  type VerifyValues
} from "../schemas/verify-email.schema";
import type { RegistrationApi, SteamApi } from "../services/auth-api";
import {
  clearPendingEmail,
  clearPendingProof,
  pendingEmailStore,
  pendingProofStore
} from "../services/pending-verification";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";
import { PasswordToggle } from "./password-toggle";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";

type VerifyEmailFormProps = {
  /** Test için sahte API adaptörü. */
  api?: RegistrationApi & Partial<SteamApi>;
};

/**
 * E-posta doğrulama formu: 6 haneli kod (yapıştırma desteği, başı 0 olabilir, metin olarak tutulur), kayıttaki parola
 * (kayıt sahibi kanıtı; sayfa yenilenirse yeniden sorulur) ve yeniden gönderme (60 sn bekleme). Kayıttan gelen e-posta
 * bellekten okunur (URL'de taşınmaz) ve düzenlenebilir. Başarıda oturum AÇILMAZ: kullanıcı giriş sayfasına yönlenir.
 * Kodun neden geçersiz olduğu söylenmez (süresi dolmuş, yanlış, deneme hakkı bitmiş: hepsi aynı mesaj).
 */
export function VerifyEmailForm({ api }: VerifyEmailFormProps) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const router = useRouter();
  const pendingEmail = useSyncExternalStore(
    pendingEmailStore.subscribe,
    pendingEmailStore.getSnapshot,
    pendingEmailStore.getServerSnapshot
  );
  // Steam ile gelen hesapta parola yoktur: tek kullanımlık doğrulama kanıtı (bellekte) parolanın yerini alır.
  const proof = useSyncExternalStore(
    pendingProofStore.subscribe,
    pendingProofStore.getSnapshot,
    pendingProofStore.getServerSnapshot
  );
  const {
    pending,
    resending,
    resent,
    problem,
    serverErrors,
    retryAfter,
    cooldown,
    resendCooldown,
    submit,
    resend
  } = useVerifyEmail(api);
  const formatWait = useWaitFormatter();
  const [emailEdit, setEmailEdit] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<VerifyErrors>({});
  const [attempted, setAttempted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const email = emailEdit ?? pendingEmail ?? "";
  const values: VerifyValues = { email, code, password };
  const fieldId = (name: VerifyField) => `${baseId}-${name}`;

  function errorText(name: VerifyField): string | undefined {
    if (name === "code" && serverErrors.code) {
      return t("fields.code.failed");
    }
    const found = errors[name];
    return found === undefined
      ? undefined
      : t(`fields.${name === "password" ? "verifyPassword" : name}.${found}`, { max: registerLimits.passwordMax });
  }

  function revalidate(next: VerifyValues) {
    if (attempted) {
      setErrors(validateVerifyEmail(next, { requirePassword: !proof }));
    }
  }

  function onEmail(event: ChangeEvent<HTMLInputElement>) {
    setEmailEdit(event.target.value);
    revalidate({ ...values, email: event.target.value });
  }

  function onCode(event: ChangeEvent<HTMLInputElement>) {
    const next = normalizeVerificationCode(event.target.value);
    setCode(next);
    revalidate({ ...values, code: next });
  }

  function onPassword(event: ChangeEvent<HTMLInputElement>) {
    setPassword(event.target.value);
    revalidate({ ...values, password: event.target.value });
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const found = validateVerifyEmail(values, { requirePassword: !proof });
    setAttempted(true);
    setErrors(found);

    const invalid = firstInvalidVerifyField(found);
    if (invalid) {
      const element = form.elements.namedItem(invalid);
      if (element instanceof HTMLElement) {
        element.focus();
      }
      return;
    }

    const outcome = await submit(values, proof);
    // Parola, sonuç ne olursa olsun alandan silinir (başarıda sayfa değişir, hatada yeniden yazılır).
    setPassword("");
    if (outcome === "verified") {
      clearPendingEmail();
      clearPendingProof();
      router.replace({ pathname: "/login", query: { verified: "1" } });
    }
  }

  async function onResend() {
    const emailError = validateResendEmail(email);
    if (emailError) {
      setAttempted(true);
      setErrors((current) => ({ ...current, email: emailError }));
      document.getElementById(fieldId("email"))?.focus();
      return;
    }
    setErrors((current) => ({ ...current, email: undefined }));
    await resend(email);
  }

  const blocked = pending || cooldown > 0;
  const resendBlocked = resending || resendCooldown > 0;

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("verify.formLabel")} className="space-y-5">
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />

      <FormField id={fieldId("email")} label={t("verify.email")} icon="mail" error={errorText("email")}>
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
            onChange={onEmail}
            className={authControlClass}
          />
        )}
      </FormField>

      <FormField
        id={fieldId("code")}
        label={t("verify.code")}
        icon="key"
        error={errorText("code")}
        hint={t("verify.codeHint", { length: verifyCodeLength })}
      >
        {(aria) => (
          <input
            {...aria}
            name="code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]*"
            maxLength={verifyCodeLength + 4}
            placeholder="000000"
            value={code}
            onChange={onCode}
            className={`${authControlClass} tracking-[0.35em]`}
          />
        )}
      </FormField>

      {proof ? null : (
        <FormField
          id={fieldId("password")}
          label={t("verify.password")}
          icon="lock"
          error={errorText("password")}
          hint={t("verify.passwordHint")}
          trailing={<PasswordToggle visible={showPassword} onToggle={() => setShowPassword((value) => !value)} />}
        >
          {(aria) => (
            <input
              {...aria}
              name="password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              value={password}
              onChange={onPassword}
              className={`${authControlClass} pe-12`}
            />
          )}
        </FormField>
      )}

      <button
        type="submit"
        disabled={blocked}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-control bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? (
          t("verify.submitting")
        ) : cooldown > 0 ? (
          t("login.retryIn", { time: formatWait(cooldown) })
        ) : (
          <>
            {t("verify.submit")}
            <AuthIcon name="arrow" />
          </>
        )}
      </button>

      <div className="flex flex-col items-center gap-2 text-sm">
        <button
          type="button"
          onClick={onResend}
          disabled={resendBlocked}
          className="font-medium text-brand-text underline underline-offset-2 disabled:cursor-not-allowed disabled:no-underline disabled:opacity-60"
        >
          {resendCooldown > 0 ? t("verify.resendIn", { time: formatWait(resendCooldown) }) : t("verify.resend")}
        </button>
        <div role="status" aria-live="polite" className="empty:hidden">
          {resent ? (
            <p className="flex items-start gap-1.5 text-text-secondary">
              <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand-text" />
              <span>{t("verify.resent")}</span>
            </p>
          ) : null}
        </div>
        <p className="text-text-secondary">
          <Link href="/login" className="font-medium text-brand-text underline underline-offset-2">
            {t("verify.backToLogin")}
          </Link>
        </p>
      </div>
    </form>
  );
}
