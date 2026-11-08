"use client";

import { useId, useState, type ChangeEvent, type FormEvent } from "react";
import { useRouter as useNextRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useLogin } from "../hooks/use-login";
import {
  emptyLoginValues,
  firstInvalidLoginField,
  loginLimits,
  validateLogin,
  type LoginErrors,
  type LoginValues
} from "../schemas/login.schema";
import { safeNextPath } from "../schemas/safe-next-path";
import type { LoginApi, TwoFactorApi } from "../services/auth-api";
import { rememberPendingEmail } from "../services/pending-verification";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";
import { PasswordToggle } from "./password-toggle";
import { SocialButtons } from "./social-buttons";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";
import { TwoFactor } from "./two-factor";

type LoginFormProps = {
  /** Giriş sonrası dönülecek dahili yol (`?next=`); güvenli değilse yok sayılır. */
  next?: string | null;
  /** E-posta doğrulaması yeni tamamlandıysa (`?verified=1`) bilgi kutusu gösterilir. */
  verified?: boolean;
  /** Şifre yeni sıfırlandıysa (`?reset=1`) bilgi kutusu gösterilir. */
  passwordReset?: boolean;
  /** Test için sahte API adaptörü. */
  api?: LoginApi;
  /** Test için sahte ikinci faktör adaptörü. */
  twoFactorApi?: TwoFactorApi;
};

/**
 * Giriş formu. Akış: doğrula → `useLogin().submit` → başarıda `next` (güvenliyse) ya da ana sayfa.
 * Hesap var/yok ayrımı yapılmaz (`INVALID_CREDENTIALS` tek mesaj). Parola yalnız alan durumunda durur,
 * depoya yazılmaz. Erişilebilirlik: `aria-invalid`/`aria-describedby`, ilk hatalı alana odak, `role="alert"`.
 */
export function LoginForm({ next = null, verified = false, passwordReset = false, api, twoFactorApi }: LoginFormProps) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const intlRouter = useRouter();
  const nextRouter = useNextRouter();
  const { pending, problem, challenge, cooldown, submit, clearChallenge } = useLogin(api);
  const formatWait = useWaitFormatter();
  const [values, setValues] = useState<LoginValues>(emptyLoginValues);
  const [errors, setErrors] = useState<LoginErrors>({});
  const [attempted, setAttempted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const fieldId = (name: keyof LoginValues) => `${baseId}-${name}`;
  const errorText = (name: keyof LoginValues) => {
    const code = errors[name];
    return code === undefined
      ? undefined
      : t(`fields.${name}.${code}`, {
          max: name === "identifier" ? loginLimits.identifierMax : loginLimits.passwordMax
        });
  };

  function onChange(name: keyof LoginValues) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      const nextValues = { ...values, [name]: event.target.value };
      setValues(nextValues);
      if (attempted) {
        setErrors(validateLogin(nextValues));
      }
    };
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const found = validateLogin(values);
    setAttempted(true);
    setErrors(found);

    const invalid = firstInvalidLoginField(found);
    if (invalid) {
      const element = form.elements.namedItem(invalid);
      if (element instanceof HTMLElement) {
        element.focus();
      }
      return;
    }

    const outcome = await submit(values.identifier, values.password);
    // Parola, sonuç ne olursa olsun alandan silinir (başarıda sayfa değişir, hatada yeniden yazılır).
    setValues((current) => ({ ...current, password: "" }));
    if (outcome === "authenticated") {
      finish();
    }
  }

  function finish() {
    const target = safeNextPath(next);
    if (target) {
      nextRouter.replace(target);
    } else {
      intlRouter.replace("/");
    }
  }

  const blocked = pending || cooldown > 0;

  if (challenge !== null) {
    return <TwoFactor challenge={challenge} onAuthenticated={finish} onCancel={clearChallenge} api={twoFactorApi} />;
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("login.formLabel")} className="space-y-5">
      {verified || passwordReset ? (
        <p
          role="status"
          className="flex items-start gap-2.5 rounded-xl border border-border bg-surface-elevated px-4 py-3 text-sm font-medium text-text-primary"
        >
          <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand-text" />
          <span>{passwordReset ? t("login.resetNotice") : t("login.verifiedNotice")}</span>
        </p>
      ) : null}
      <SocialButtons mode="login" />
      <ProblemNotice code={problem?.code ?? null} retryAfterSeconds={problem?.retryAfterSeconds} />
      {problem?.code === "ACCOUNT_DELETION_PENDING" ? (
        <Link href="/account/cancel-deletion" className="inline-flex text-sm font-medium text-brand-text underline underline-offset-2">
          {t("cancelDeletion.fromLogin")}
        </Link>
      ) : null}
      {problem?.code === "EMAIL_NOT_VERIFIED" ? (
        <Link
          href="/verify-email"
          onClick={() => {
            // Girilen değer e-postaysa doğrulama ekranında hazır gelir (bellekte taşınır, adrese yazılmaz).
            const typed = values.identifier.trim();
            if (typed.includes("@")) rememberPendingEmail(typed);
          }}
          className="inline-flex text-sm font-medium text-brand-text underline underline-offset-2"
        >
          {t("login.verifyLink")}
        </Link>
      ) : null}

      <FormField
        id={fieldId("identifier")}
        label={t("login.identifier")}
        icon="mail"
        error={errorText("identifier")}
      >
        {(aria) => (
          <input
            {...aria}
            name="identifier"
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            placeholder={t("login.identifierPlaceholder")}
            value={values.identifier}
            onChange={onChange("identifier")}
            className={authControlClass}
          />
        )}
      </FormField>

      <FormField
        id={fieldId("password")}
        label={t("login.password")}
        icon="lock"
        error={errorText("password")}
        trailing={<PasswordToggle visible={showPassword} onToggle={() => setShowPassword((value) => !value)} />}
      >
        {(aria) => (
          <input
            {...aria}
            name="password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            placeholder={t("login.passwordPlaceholder")}
            value={values.password}
            onChange={onChange("password")}
            className={`${authControlClass} pe-12`}
          />
        )}
      </FormField>

      <p className="-mt-2 text-end text-sm">
        <Link href="/forgot-password" className="font-medium text-text-secondary underline underline-offset-2 hover:text-text-primary">
          {t("login.forgot")}
        </Link>
      </p>

      <button
        type="submit"
        disabled={blocked}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? (
          t("login.submitting")
        ) : cooldown > 0 ? (
          t("login.retryIn", { time: formatWait(cooldown) })
        ) : (
          <>
            {t("login.submit")}
            <AuthIcon name="arrow" />
          </>
        )}
      </button>

      <p className="text-center text-sm text-text-secondary">
        {t("login.noAccount")}{" "}
        <Link href="/register" className="font-medium text-brand-text underline underline-offset-2">
          {t("login.toRegister")}
        </Link>
      </p>
    </form>
  );
}
