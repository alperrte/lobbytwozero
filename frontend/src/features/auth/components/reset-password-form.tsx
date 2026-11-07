"use client";

import { useId, useState, useSyncExternalStore, type ChangeEvent, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useResetPassword } from "../hooks/use-password-reset";
import {
  emptyResetValues,
  firstInvalidResetField,
  validateReset,
  type ResetErrors,
  type ResetField,
  type ResetValues
} from "../schemas/password-reset.schema";
import { registerLimits } from "../schemas/register.schema";
import type { PasswordResetApi } from "../services/auth-api";
import { forgetFragmentToken, fragmentTokenStore } from "../services/fragment-token";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";
import { PasswordToggle } from "./password-toggle";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";

type ResetPasswordFormProps = {
  /** Test için sahte API adaptörü. */
  api?: PasswordResetApi;
};

/** Token yok ya da sunucu reddetti: form yerine "yeni bağlantı iste" yönlendirmesi. */
function InvalidLink() {
  const t = useTranslations("Auth");
  return (
    <div role="alert" className="space-y-4 rounded-control border border-primary/60 bg-primary/10 p-4 text-sm text-text-primary">
      <p className="flex items-start gap-2.5 font-medium">
        <Icon name="info" className="mt-0.5 size-4 shrink-0 text-brand-text" />
        <span>{t("reset.tokenInvalid")}</span>
      </p>
      <Link href="/forgot-password" className="inline-flex font-medium text-brand-text underline underline-offset-2">
        {t("reset.requestNew")}
      </Link>
    </div>
  );
}

/**
 * Şifre sıfırlama formu. Token e-postadaki bağlantının URL parçasından (`#token=…`) okunur ve adres çubuğundan hemen
 * silinir; sunucuya yalnız istek gövdesinde gider. Yeni parola ve tekrarı ister (parola kırpılmaz, saklanmaz). Başarıda
 * giriş sayfasına yönlendirir (tüm oturumlar sunucuda iptal edilir). Token geçersiz/süresi dolmuşsa form yerine yeni bağlantı
 * isteme yönlendirmesi gösterilir; nedenler ayrıştırılmaz.
 */
export function ResetPasswordForm({ api }: ResetPasswordFormProps) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const router = useRouter();
  const token = useSyncExternalStore(
    fragmentTokenStore.subscribe,
    fragmentTokenStore.getSnapshot,
    fragmentTokenStore.getServerSnapshot
  );
  const { pending, tokenInvalid, problem, serverErrors, retryAfter, cooldown, submit } = useResetPassword(api);
  const formatWait = useWaitFormatter();
  const [values, setValues] = useState<ResetValues>(emptyResetValues);
  const [errors, setErrors] = useState<ResetErrors>({});
  const [attempted, setAttempted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [completed, setCompleted] = useState(false);

  const fieldId = (name: ResetField) => `${baseId}-${name}`;

  if (token === undefined) {
    return null; // sunucu görüntüsü / hidrasyon: token henüz okunmadı
  }
  if ((token === null || tokenInvalid) && !completed) {
    return <InvalidLink />;
  }

  function errorText(name: ResetField): string | undefined {
    if (name === "newPassword" && serverErrors.newPassword) {
      return t(`fields.newPassword.${serverErrors.newPassword}`);
    }
    const code = errors[name];
    if (code === undefined) {
      return undefined;
    }
    return t(`fields.${name}.${code}`, { min: registerLimits.passwordMin, max: registerLimits.passwordMax });
  }

  function update(next: ResetValues) {
    setValues(next);
    if (attempted) {
      setErrors(validateReset(next));
    }
  }

  function onChange(name: keyof ResetValues) {
    return (event: ChangeEvent<HTMLInputElement>) => update({ ...values, [name]: event.target.value });
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const found = validateReset(values);
    setAttempted(true);
    setErrors(found);

    const invalid = firstInvalidResetField(found);
    if (invalid) {
      const element = form.elements.namedItem(invalid);
      if (element instanceof HTMLElement) {
        element.focus();
      }
      return;
    }

    const outcome = await submit(token as string, values.newPassword, values.confirmation);
    // Parolalar, sonuç ne olursa olsun alanlardan silinir (başarıda sayfa değişir, hatada yeniden yazılır).
    setValues(emptyResetValues);
    if (outcome === "reset") {
      // Token kullanıldı: bellekten silinir; yönlendirme tamamlanana kadar form yerinde kalır (geçersiz bağlantı ekranı yanıp sönmez).
      setCompleted(true);
      forgetFragmentToken();
      router.replace({ pathname: "/login", query: { reset: "1" } });
    }
  }

  const blocked = pending || cooldown > 0;
  const toggle = <PasswordToggle visible={showPassword} onToggle={() => setShowPassword((value) => !value)} />;

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("reset.formLabel")} className="space-y-5">
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />

      <FormField
        id={fieldId("newPassword")}
        label={t("reset.newPassword")}
        icon="lock"
        error={errorText("newPassword")}
        hint={t("register.passwordHint", { min: registerLimits.passwordMin })}
        trailing={toggle}
      >
        {(aria) => (
          <input
            {...aria}
            name="newPassword"
            type={showPassword ? "text" : "password"}
            autoComplete="new-password"
            value={values.newPassword}
            onChange={onChange("newPassword")}
            className={`${authControlClass} pe-12`}
          />
        )}
      </FormField>

      <FormField id={fieldId("confirmation")} label={t("reset.confirmation")} icon="lock" error={errorText("confirmation")}>
        {(aria) => (
          <input
            {...aria}
            name="confirmation"
            type={showPassword ? "text" : "password"}
            autoComplete="new-password"
            value={values.confirmation}
            onChange={onChange("confirmation")}
            className={authControlClass}
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
          t("reset.submitting")
        ) : cooldown > 0 ? (
          t("login.retryIn", { time: formatWait(cooldown) })
        ) : (
          <>
            {t("reset.submit")}
            <AuthIcon name="arrow" />
          </>
        )}
      </button>
    </form>
  );
}
