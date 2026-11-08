"use client";

import { useId, useState, type ChangeEvent, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useForgotPassword } from "../hooks/use-password-reset";
import { validateForgotPassword, type ForgotErrors } from "../schemas/password-reset.schema";
import { registerLimits } from "../schemas/register.schema";
import type { PasswordResetApi } from "../services/auth-api";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";

type ForgotPasswordFormProps = {
  /** Test için sahte API adaptörü. */
  api?: PasswordResetApi;
};

/**
 * Şifremi unuttum formu: yalnız e-posta ister. Başarıda (her zaman `202`) hesabın var olup olmadığı SÖYLENMEZ; "adres
 * kayıtlıysa bağlantı gönderdik" bilgisi gösterilir. 429'da geri sayım başlar.
 */
export function ForgotPasswordForm({ api }: ForgotPasswordFormProps) {
  const t = useTranslations("Auth");
  const emailId = useId();
  const { pending, sent, problem, retryAfter, cooldown, submit } = useForgotPassword(api);
  const formatWait = useWaitFormatter();
  const [email, setEmail] = useState("");
  const [errors, setErrors] = useState<ForgotErrors>({});
  const [attempted, setAttempted] = useState(false);

  function onChange(event: ChangeEvent<HTMLInputElement>) {
    setEmail(event.target.value);
    if (attempted) {
      setErrors(validateForgotPassword(event.target.value));
    }
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const found = validateForgotPassword(email);
    setAttempted(true);
    setErrors(found);
    if (found.email) {
      const element = form.elements.namedItem("email");
      if (element instanceof HTMLElement) {
        element.focus();
      }
      return;
    }
    await submit(email);
  }

  const emailError = errors.email
    ? t(`fields.email.${errors.email}`, { max: registerLimits.emailMax })
    : undefined;
  const blocked = pending || cooldown > 0;

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("forgot.formLabel")} className="space-y-5">
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />

      <div role="status" aria-live="polite" className="empty:hidden">
        {sent ? (
          <p className="flex items-start gap-2.5 rounded-xl border border-border bg-surface-elevated px-4 py-3 text-sm font-medium text-text-primary">
            <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand-text" />
            <span>{t("forgot.sent")}</span>
          </p>
        ) : null}
      </div>

      <FormField id={emailId} label={t("forgot.email")} icon="mail" error={emailError}>
        {(aria) => (
          <input
            {...aria}
            name="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            spellCheck={false}
            maxLength={registerLimits.emailMax}
            placeholder={t("forgot.emailPlaceholder")}
            value={email}
            onChange={onChange}
            className={authControlClass}
          />
        )}
      </FormField>

      <button
        type="submit"
        disabled={blocked}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? (
          t("forgot.submitting")
        ) : cooldown > 0 ? (
          t("login.retryIn", { time: formatWait(cooldown) })
        ) : (
          <>
            {sent ? t("forgot.resend") : t("forgot.submit")}
            <AuthIcon name="arrow" />
          </>
        )}
      </button>

      <p className="text-center text-sm text-text-secondary">
        <Link href="/login" className="font-medium text-brand-text underline underline-offset-2">
          {t("forgot.backToLogin")}
        </Link>
      </p>
    </form>
  );
}
