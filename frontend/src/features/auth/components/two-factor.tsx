"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { useTwoFactor } from "../hooks/use-two-factor";
import type { TwoFactorApi } from "../services/auth-api";
import type { MfaChallenge } from "../types/auth.types";
import { authControlClass, FormField } from "./form-field";
import { AuthIcon } from "./auth-icons";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";

type TwoFactorProps = {
  challenge: MfaChallenge;
  /** Oturum açıldı (çerezler yazıldı): çağıran yönlendirmeyi yapar. */
  onAuthenticated: () => void;
  /** Kullanıcı başa dönmek ister ya da challenge bitmiştir. */
  onCancel: () => void;
  /** Test için sahte API adaptörü. */
  api?: TwoFactorApi;
};

const submitClass =
  "inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-control bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none";
const linkButtonClass = "text-sm font-medium text-brand-text underline underline-offset-2";

const TOTP = /^[0-9]{6}$/;
const RECOVERY = /^[A-Za-z0-9]{5}-[A-Za-z0-9]{5}$/;

function Submit({ pending, cooldown, children }: { pending: boolean; cooldown: number; children: React.ReactNode }) {
  const formatWait = useWaitFormatter();
  const t = useTranslations("Auth");
  return (
    <button type="submit" disabled={pending || cooldown > 0} aria-busy={pending} className={submitClass}>
      {pending ? t("mfa.submitting") : cooldown > 0 ? t("login.retryIn", { time: formatWait(cooldown) }) : children}
    </button>
  );
}

/** Giriş ikinci adımı: 6 haneli TOTP kodu ya da (anahtar kaybında) tek kullanımlık kurtarma kodu. */
function VerifyStep({ challenge, onAuthenticated, onCancel, api }: TwoFactorProps) {
  const t = useTranslations("Auth.mfa");
  const baseId = useId();
  const { pending, problem, cooldown, verify } = useTwoFactor(api);
  const [useRecovery, setUseRecovery] = useState(false);
  const [value, setValue] = useState("");
  const [invalid, setInvalid] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const clean = value.trim();
    if (!(useRecovery ? RECOVERY.test(clean) : TOTP.test(clean))) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    const outcome = await verify(challenge.id, useRecovery ? { recoveryCode: clean.toUpperCase() } : { code: clean });
    setValue("");
    if (outcome === "authenticated") {
      onAuthenticated();
    }
  }

  const expired = problem?.code === "MFA_FAILED";
  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("verify.formLabel")} className="space-y-5">
      <p className="text-sm text-text-secondary">{useRecovery ? t("verify.recoveryDescription") : t("verify.description")}</p>
      <ProblemNotice code={problem?.code ?? null} retryAfterSeconds={problem?.retryAfterSeconds} />
      <FormField
        id={`${baseId}-factor`}
        label={useRecovery ? t("verify.recoveryCode") : t("verify.code")}
        icon="key"
        error={invalid ? t(useRecovery ? "verify.recoveryInvalid" : "verify.codeInvalid") : undefined}
      >
        {(aria) => (
          <input
            {...aria}
            key={useRecovery ? "recovery" : "totp"}
            name={useRecovery ? "recoveryCode" : "code"}
            type="text"
            inputMode={useRecovery ? "text" : "numeric"}
            autoComplete="one-time-code"
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={useRecovery ? 11 : 6}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            className={authControlClass}
          />
        )}
      </FormField>
      <Submit pending={pending} cooldown={cooldown}>
        {t("verify.submit")}
        <AuthIcon name="arrow" />
      </Submit>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          className={linkButtonClass}
          onClick={() => {
            setUseRecovery((current) => !current);
            setValue("");
            setInvalid(false);
          }}
        >
          {useRecovery ? t("verify.useCode") : t("verify.useRecovery")}
        </button>
        <button type="button" className={linkButtonClass} onClick={onCancel}>
          {expired ? t("verify.restart") : t("verify.cancel")}
        </button>
      </div>
    </form>
  );
}

/** Zorunlu TOTP kurulumu: anahtarı göster → doğrulama kodu → kurtarma kodlarını göster (yalnız bir kez). */
function EnrollStep({ challenge, onAuthenticated, onCancel, api }: TwoFactorProps) {
  const t = useTranslations("Auth.mfa");
  const baseId = useId();
  const { pending, problem, cooldown, enrollment, recoveryCodes, startEnrollment, completeEnrollment } = useTwoFactor(api);
  const [code, setCode] = useState("");
  const [invalid, setInvalid] = useState(false);
  const [saved, setSaved] = useState(false);

  if (recoveryCodes !== null) {
    return (
      <div className="space-y-5">
        <p role="status" className="text-sm font-medium text-text-primary">
          {t("enroll.done")}
        </p>
        <p className="text-sm text-text-secondary">{t("enroll.recoveryDescription")}</p>
        <ul
          aria-label={t("enroll.recoveryList")}
          className="grid grid-cols-2 gap-2 rounded-control border border-border bg-surface-elevated p-4 font-mono text-sm text-text-primary"
        >
          {recoveryCodes.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
        <label className="flex items-start gap-2.5 text-sm text-text-primary">
          <input type="checkbox" checked={saved} onChange={(event) => setSaved(event.target.checked)} className="mt-1 size-4" />
          <span>{t("enroll.saved")}</span>
        </label>
        <button type="button" disabled={!saved} onClick={onAuthenticated} className={submitClass}>
          {t("enroll.continue")}
          <AuthIcon name="arrow" />
        </button>
      </div>
    );
  }

  if (enrollment === null) {
    return (
      <div className="space-y-5">
        <p className="text-sm text-text-secondary">{t("enroll.intro")}</p>
        <ProblemNotice code={problem?.code ?? null} retryAfterSeconds={problem?.retryAfterSeconds} />
        <button
          type="button"
          disabled={pending || cooldown > 0}
          aria-busy={pending}
          onClick={() => void startEnrollment(challenge.id)}
          className={submitClass}
        >
          {pending ? t("submitting") : t("enroll.start")}
        </button>
        <button type="button" className={linkButtonClass} onClick={onCancel}>
          {t("verify.cancel")}
        </button>
      </div>
    );
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const clean = code.trim();
    if (!TOTP.test(clean)) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    await completeEnrollment(challenge.id, clean);
    setCode("");
  }

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("enroll.formLabel")} className="space-y-5">
      <p className="text-sm text-text-secondary">{t("enroll.scan")}</p>
      <div className="space-y-2 rounded-control border border-border bg-surface-elevated p-4">
        <p className="text-xs text-text-secondary">{t("enroll.secret")}</p>
        <p data-testid="totp-secret" className="break-all font-mono text-sm text-text-primary">
          {enrollment.secret}
        </p>
        <a href={enrollment.otpauthUri} className={linkButtonClass}>
          {t("enroll.openApp")}
        </a>
      </div>
      <ProblemNotice code={problem?.code ?? null} retryAfterSeconds={problem?.retryAfterSeconds} />
      <FormField id={`${baseId}-code`} label={t("verify.code")} icon="key" error={invalid ? t("verify.codeInvalid") : undefined}>
        {(aria) => (
          <input
            {...aria}
            name="code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            spellCheck={false}
            maxLength={6}
            value={code}
            onChange={(event) => setCode(event.target.value)}
            className={authControlClass}
          />
        )}
      </FormField>
      <Submit pending={pending} cooldown={cooldown}>
        {t("enroll.activate")}
        <AuthIcon name="arrow" />
      </Submit>
      <button type="button" className={linkButtonClass} onClick={onCancel}>
        {t("verify.cancel")}
      </button>
    </form>
  );
}

/** İkinci faktör ekranı: challenge türüne göre giriş adımı ya da zorunlu kurulum. */
export function TwoFactor(props: TwoFactorProps) {
  return props.challenge.kind === "verify" ? <VerifyStep {...props} /> : <EnrollStep {...props} />;
}
