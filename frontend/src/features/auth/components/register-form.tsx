"use client";

import { useId, useState, type ChangeEvent, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useRegister, type RegisterServerErrors } from "../hooks/use-register";
import {
  emptyRegisterValues,
  firstInvalidRegisterField,
  registerLimits,
  validateRegister,
  type RegisterErrors,
  type RegisterField,
  type RegisterValues
} from "../schemas/register.schema";
import type { RegistrationApi } from "../services/auth-api";
import { rememberPendingEmail } from "../services/pending-verification";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";
import { PasswordToggle } from "./password-toggle";
import { SocialButtons } from "./social-buttons";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";

type RegisterFormProps = {
  /** Test için sahte API adaptörü. */
  api?: RegistrationApi;
};

type TextField = "username" | "email" | "password";

/**
 * Kayıt formu. Akış: istemci doğrulaması → `useRegister().submit` → başarıda (her zaman `202`) e-postayı BELLEKTE bırakıp
 * doğrulama ekranına geçer; hesabın zaten var olup olmadığı söylenmez. Sunucu hataları ilgili alana bağlanır
 * (`USERNAME_TAKEN`, `WEAK_PASSWORD`). Parola yalnız alan durumunda durur, depoya yazılmaz ve başarıda silinir.
 */
export function RegisterForm({ api }: RegisterFormProps) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const router = useRouter();
  const { pending, problem, serverErrors, retryAfter, cooldown, submit } = useRegister(api);
  const formatWait = useWaitFormatter();
  const [values, setValues] = useState<RegisterValues>(emptyRegisterValues);
  const [errors, setErrors] = useState<RegisterErrors>({});
  const [attempted, setAttempted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const fieldId = (name: RegisterField) => `${baseId}-${name}`;

  function errorText(name: TextField): string | undefined {
    const server = serverErrors[name as keyof RegisterServerErrors];
    if (server !== undefined) {
      return t(`fields.${name}.${server}`);
    }
    const code = errors[name];
    return code === undefined
      ? undefined
      : t(`fields.${name}.${code}`, {
          min: registerLimits.passwordMin,
          max: name === "password" ? registerLimits.passwordMax : registerLimits.emailMax
        });
  }

  function update(next: RegisterValues) {
    setValues(next);
    if (attempted) {
      setErrors(validateRegister(next));
    }
  }

  function onChange(name: TextField) {
    return (event: ChangeEvent<HTMLInputElement>) => update({ ...values, [name]: event.target.value });
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const found = validateRegister(values);
    setAttempted(true);
    setErrors(found);

    const invalid = firstInvalidRegisterField(found);
    if (invalid) {
      const element = form.elements.namedItem(invalid);
      if (element instanceof HTMLElement) {
        element.focus();
      }
      return;
    }

    const outcome = await submit(values);
    // Parola, sonuç ne olursa olsun alandan silinir (başarıda sayfa değişir, hatada yeniden yazılır).
    setValues((current) => ({ ...current, password: "" }));
    if (outcome === "registered") {
      rememberPendingEmail(values.email.trim());
      router.push("/verify-email");
    }
  }

  const termsError = errors.acceptTerms ? t("fields.acceptTerms.required") : undefined;
  const termsId = fieldId("acceptTerms");
  const blocked = pending || cooldown > 0;

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("register.formLabel")} className="space-y-5">
      <SocialButtons mode="register" />
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />

      <FormField
        id={fieldId("username")}
        label={t("register.username")}
        icon="user"
        error={errorText("username")}
        hint={t("register.usernameHint")}
      >
        {(aria) => (
          <input
            {...aria}
            name="username"
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={registerLimits.usernameMax}
            placeholder={t("register.usernamePlaceholder")}
            value={values.username}
            onChange={onChange("username")}
            className={authControlClass}
          />
        )}
      </FormField>

      <FormField id={fieldId("email")} label={t("register.email")} icon="mail" error={errorText("email")}>
        {(aria) => (
          <input
            {...aria}
            name="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            spellCheck={false}
            maxLength={registerLimits.emailMax}
            placeholder={t("register.emailPlaceholder")}
            value={values.email}
            onChange={onChange("email")}
            className={authControlClass}
          />
        )}
      </FormField>

      <FormField
        id={fieldId("password")}
        label={t("register.password")}
        icon="lock"
        error={errorText("password")}
        hint={t("register.passwordHint", { min: registerLimits.passwordMin })}
        trailing={<PasswordToggle visible={showPassword} onToggle={() => setShowPassword((value) => !value)} />}
      >
        {(aria) => (
          <input
            {...aria}
            name="password"
            type={showPassword ? "text" : "password"}
            autoComplete="new-password"
            placeholder={t("register.passwordPlaceholder")}
            value={values.password}
            onChange={onChange("password")}
            className={`${authControlClass} pe-12`}
          />
        )}
      </FormField>

      <div>
        <div className="flex items-start gap-3">
          <input
            id={termsId}
            name="acceptTerms"
            type="checkbox"
            checked={values.acceptTerms}
            onChange={(event) => update({ ...values, acceptTerms: event.target.checked })}
            aria-invalid={termsError !== undefined}
            aria-describedby={termsError ? `${termsId}-error` : undefined}
            className="mt-1 size-5 shrink-0 rounded-xl border border-control-border bg-surface-elevated/60 accent-primary"
          />
          <label htmlFor={termsId} className="text-sm leading-relaxed text-text-secondary">
            {t.rich("register.terms", {
              terms: (chunks) => (
                <Link href="/legal/terms" className="font-medium text-brand-text underline underline-offset-2">
                  {chunks}
                </Link>
              ),
              privacy: (chunks) => (
                <Link href="/legal/privacy" className="font-medium text-brand-text underline underline-offset-2">
                  {chunks}
                </Link>
              )
            })}
          </label>
        </div>
        {termsError ? (
          <p id={`${termsId}-error`} className="mt-1.5 flex items-start gap-1.5 text-sm font-medium text-brand-text">
            <Icon name="info" className="mt-0.5 size-4 shrink-0" />
            <span>{termsError}</span>
          </p>
        ) : null}
      </div>

      <button
        type="submit"
        disabled={blocked}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? (
          t("register.submitting")
        ) : cooldown > 0 ? (
          t("login.retryIn", { time: formatWait(cooldown) })
        ) : (
          <>
            {t("register.submit")}
            <AuthIcon name="arrow" />
          </>
        )}
      </button>
      <p className="text-center text-xs text-text-secondary">{t("register.nextStep")}</p>
    </form>
  );
}
