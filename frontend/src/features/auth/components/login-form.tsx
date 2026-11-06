"use client";

import { useId, useState, type ChangeEvent, type FormEvent } from "react";
import { useRouter as useNextRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { useLogin } from "../hooks/use-login";
import {
  emptyLoginValues,
  firstInvalidLoginField,
  loginLimits,
  validateLogin,
  type LoginErrors,
  type LoginValues
} from "../schemas/login.schema";
import type { LoginApi } from "../services/auth-api";
import type { AuthErrorCode } from "../types/auth.types";
import { safeNextPath } from "../utils/safe-next-path";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";

type LoginFormProps = {
  /** Giriş sonrası dönülecek dahili yol (`?next=`); güvenli değilse yok sayılır. */
  next?: string | null;
  /** Test için sahte API adaptörü. */
  api?: LoginApi;
};

/** Sunucu kodu → `Auth.errors.<anahtar>`; giriş akışında anlamı olmayan kodlar genel hataya düşer. */
const messageKeys: Partial<Record<AuthErrorCode, string>> = {
  INVALID_CREDENTIALS: "INVALID_CREDENTIALS",
  EMAIL_NOT_VERIFIED: "EMAIL_NOT_VERIFIED",
  ACCOUNT_SUSPENDED: "ACCOUNT_SUSPENDED",
  ACCOUNT_BANNED: "ACCOUNT_BANNED",
  ACCOUNT_UNAVAILABLE: "ACCOUNT_UNAVAILABLE",
  ACCOUNT_DELETION_PENDING: "ACCOUNT_DELETION_PENDING",
  TWO_FACTOR_ENROLLMENT_REQUIRED: "TWO_FACTOR_ENROLLMENT_REQUIRED",
  MFA_NOT_SUPPORTED: "MFA_NOT_SUPPORTED",
  CSRF_TOKEN_INVALID: "CSRF_TOKEN_INVALID",
  TOO_MANY_ATTEMPTS: "TOO_MANY_ATTEMPTS",
  SERVICE_BUSY: "SERVICE_BUSY",
  NETWORK: "NETWORK"
};

/**
 * Giriş formu. Akış: doğrula → `useLogin().submit` → başarıda `next` (güvenliyse) ya da ana sayfa.
 * Hesap var/yok ayrımı yapılmaz (`INVALID_CREDENTIALS` tek mesaj). Parola yalnız alan durumunda durur,
 * depoya yazılmaz. Erişilebilirlik: `aria-invalid`/`aria-describedby`, ilk hatalı alana odak, `role="alert"`.
 */
export function LoginForm({ next = null, api }: LoginFormProps) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const intlRouter = useRouter();
  const nextRouter = useNextRouter();
  const { pending, problem, cooldown, submit } = useLogin(api);
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
      const target = safeNextPath(next);
      if (target) {
        nextRouter.replace(target);
      } else {
        intlRouter.replace("/");
      }
    }
  }

  const messageKey = problem ? (messageKeys[problem.code] ?? "UNKNOWN") : null;
  /** 60 sn ve üstü dakikaya yuvarlanır (ör. 900 sn → "15 dakika"); altı saniye olarak gösterilir. */
  const formatWait = (seconds: number) =>
    seconds >= 60
      ? t("time.minutes", { count: Math.ceil(seconds / 60) })
      : t("time.seconds", { count: seconds });
  const blocked = pending || cooldown > 0;

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("login.formLabel")} className="space-y-5">
      {messageKey ? (
        <p
          role="alert"
          className="flex items-start gap-2.5 rounded-control border border-primary/60 bg-primary/10 px-4 py-3 text-sm font-medium text-text-primary"
        >
          <Icon name="info" className="mt-0.5 size-4 shrink-0 text-brand-text" />
          <span>{t(`errors.${messageKey}`, { time: formatWait(problem?.retryAfterSeconds ?? 0) })}</span>
        </p>
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
        trailing={
          <button
            type="button"
            onClick={() => setShowPassword((value) => !value)}
            aria-pressed={showPassword}
            aria-label={showPassword ? t("login.hide") : t("login.show")}
            className="inline-flex size-10 items-center justify-center rounded-control text-text-secondary transition-colors duration-150 hover:text-text-primary motion-reduce:transition-none"
          >
            <AuthIcon name={showPassword ? "eyeOff" : "eye"} />
          </button>
        }
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

      <button
        type="submit"
        disabled={blocked}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-control bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
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
    </form>
  );
}
