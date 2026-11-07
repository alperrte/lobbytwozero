"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useCancelDeletion } from "../hooks/use-cancel-deletion";
import { loginLimits } from "../schemas/login.schema";
import type { DeletionCancelApi } from "../services/auth-api";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";
import { PasswordToggle } from "./password-toggle";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";

type CancelDeletionFormProps = {
  /** Test için sahte API adaptörü. */
  api?: DeletionCancelApi;
};

const TOTP = /^[0-9]{6}$/;
const RECOVERY = /^[A-Za-z0-9]{5}-[A-Za-z0-9]{5}$/;
const linkClass = "inline-flex text-sm font-medium text-brand-text underline underline-offset-2";

type Errors = { identifier?: boolean; password?: boolean; code?: boolean };

/**
 * Silme talebinden vazgeçme. Yalnız bekleme (geri alma) süresi içinde çalışır; hesap yeniden `ACTIVE` olur ama oturum
 * AÇILMAZ, kullanıcı normal girişle devam eder. Parola ve kod hata ya da başarı sonrası alandan silinir.
 */
export function CancelDeletionForm({ api }: CancelDeletionFormProps) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const { pending, done, reason, problem, retryAfter, cooldown, submit } = useCancelDeletion(api);
  const formatWait = useWaitFormatter();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<Errors>({});

  if (done) {
    return (
      <div className="space-y-4">
        <p
          role="status"
          className="flex items-start gap-2.5 rounded-control border border-border bg-surface-elevated px-4 py-3 text-sm font-medium text-text-primary"
        >
          <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand-text" />
          <span>{t("cancelDeletion.done")}</span>
        </p>
        <Link href="/login" className={linkClass}>
          {t("cancelDeletion.toLogin")}
        </Link>
      </div>
    );
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const cleanCode = code.trim();
    const found: Errors = {
      identifier: identifier.trim() === "" || undefined,
      password: password === "" || undefined,
      code: cleanCode !== "" && !TOTP.test(cleanCode) && !RECOVERY.test(cleanCode) ? true : undefined
    };
    setErrors(found);
    const first = found.identifier ? "identifier" : found.password ? "password" : found.code ? "code" : null;
    if (first) {
      const element = form.elements.namedItem(first);
      if (element instanceof HTMLElement) element.focus();
      return;
    }
    const factor =
      cleanCode === "" ? undefined : TOTP.test(cleanCode) ? { code: cleanCode } : { recoveryCode: cleanCode.toUpperCase() };
    await submit(identifier, password, factor);
    setPassword("");
    setCode("");
  }

  const reasonKey = reason ?? null;

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("cancelDeletion.formLabel")} className="space-y-5">
      {reasonKey ? (
        <p
          role="alert"
          className="flex items-start gap-2.5 rounded-control border border-primary/60 bg-primary/10 px-4 py-3 text-sm font-medium text-text-primary"
        >
          <Icon name="info" className="mt-0.5 size-4 shrink-0 text-brand-text" />
          <span>{t(`cancelDeletion.${reasonKey}`)}</span>
        </p>
      ) : null}
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />

      <FormField
        id={`${baseId}-identifier`}
        label={t("login.identifier")}
        icon="mail"
        error={errors.identifier ? t("fields.identifier.required") : undefined}
      >
        {(aria) => (
          <input
            {...aria}
            name="identifier"
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={loginLimits.identifierMax}
            value={identifier}
            onChange={(event) => setIdentifier(event.target.value)}
            className={authControlClass}
          />
        )}
      </FormField>

      <FormField
        id={`${baseId}-password`}
        label={t("login.password")}
        icon="lock"
        error={errors.password ? t("fields.password.required") : undefined}
        trailing={<PasswordToggle visible={showPassword} onToggle={() => setShowPassword((value) => !value)} />}
      >
        {(aria) => (
          <input
            {...aria}
            name="password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            maxLength={loginLimits.passwordMax}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className={`${authControlClass} pe-12`}
          />
        )}
      </FormField>

      <FormField
        id={`${baseId}-code`}
        label={t("cancelDeletion.code")}
        icon="key"
        error={errors.code ? t("cancelDeletion.codeInvalid") : undefined}
        hint={t("cancelDeletion.codeHint")}
      >
        {(aria) => (
          <input
            {...aria}
            name="code"
            type="text"
            autoComplete="one-time-code"
            spellCheck={false}
            maxLength={11}
            value={code}
            onChange={(event) => setCode(event.target.value)}
            className={authControlClass}
          />
        )}
      </FormField>

      <p className="text-xs text-text-secondary">{t("cancelDeletion.noPassword")}</p>

      <button
        type="submit"
        disabled={pending || cooldown > 0}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-control bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? (
          t("cancelDeletion.submitting")
        ) : cooldown > 0 ? (
          t("login.retryIn", { time: formatWait(cooldown) })
        ) : (
          <>
            {t("cancelDeletion.submit")}
            <AuthIcon name="arrow" />
          </>
        )}
      </button>

      <Link href="/login" className={linkClass}>
        {t("cancelDeletion.back")}
      </Link>
    </form>
  );
}
