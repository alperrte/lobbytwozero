"use client";

import { useId, useState, type FormEvent } from "react";
import { authApi } from "@/features/auth";
import { adminErrorMessage } from "../services/admin-errors";
import { errorClass, inputClass, labelClass, linkButton, primaryButton } from "./ui";

type Step = { kind: "credentials" } | { kind: "code"; challengeId: string } | { kind: "enroll" };

/** Kod alanı: 6 haneli sayı TOTP kodudur, başka her şey kurtarma kodu olarak gönderilir. */
function factorOf(value: string): { code: string } | { recoveryCode: string } {
  const text = value.trim();
  return /^\d{6}$/.test(text) ? { code: text } : { recoveryCode: text };
}

/**
 * Yönetim girişi: kimlik + parola, ardından (Auth isterse) TOTP/kurtarma kodu. Rol kararı üst bileşendedir;
 * burada yalnız oturum açılır ve `onSignedIn` çağrılır. Parola hiçbir yerde saklanmaz ya da loglanmaz.
 */
export function AdminLogin({ onSignedIn }: { onSignedIn: () => void }) {
  const [step, setStep] = useState<Step>({ kind: "credentials" });
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const id = useId();

  async function submitCredentials(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await authApi.login(identifier.trim(), password);
      setPassword("");
      if (result.status === "AUTHENTICATED") onSignedIn();
      else if (result.status === "MFA_REQUIRED") setStep({ kind: "code", challengeId: result.challengeId });
      else setStep({ kind: "enroll" });
    } catch (failure) {
      setError(adminErrorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  async function submitCode(event: FormEvent) {
    event.preventDefault();
    if (step.kind !== "code") return;
    setBusy(true);
    setError(null);
    try {
      await authApi.verifyTwoFactor(step.challengeId, factorOf(code));
      setCode("");
      onSignedIn();
    } catch (failure) {
      setError(adminErrorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  const stepNo = step.kind === "credentials" ? "01" : "02";

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm border border-border border-t-2 border-t-primary bg-surface">
        <div className="flex items-center justify-between border-b border-border px-5 py-3">
          <span className="font-display text-sm font-semibold uppercase tracking-[0.2em] text-text-primary">
            LTZ <span className="text-primary">{"//"}</span> Yönetim
          </span>
          <span className={labelClass}>Adım {stepNo}/02</span>
        </div>

        <div className="p-5">
          {step.kind === "credentials" && (
            <form onSubmit={submitCredentials} className="flex flex-col gap-5" noValidate>
              <label htmlFor={`${id}-user`} className="flex flex-col gap-2">
                <span className={labelClass}>Kimlik</span>
                <input id={`${id}-user`} className={inputClass} autoComplete="username" autoCapitalize="none"
                  spellCheck={false} value={identifier} onChange={(event) => setIdentifier(event.target.value)} />
              </label>
              <label htmlFor={`${id}-pass`} className="flex flex-col gap-2">
                <span className={labelClass}>Parola</span>
                <input id={`${id}-pass`} className={inputClass} type="password" autoComplete="current-password"
                  value={password} onChange={(event) => setPassword(event.target.value)} />
              </label>
              {error && <p role="alert" className={errorClass}>{error}</p>}
              <button type="submit" className={primaryButton} disabled={busy || identifier.trim() === "" || password === ""}>
                {busy ? "Doğrulanıyor…" : "Devam"}
              </button>
            </form>
          )}

          {step.kind === "code" && (
            <form onSubmit={submitCode} className="flex flex-col gap-5" noValidate>
              <label htmlFor={`${id}-code`} className="flex flex-col gap-2">
                <span className={labelClass}>Doğrulama kodu / kurtarma kodu</span>
                <input id={`${id}-code`} className={`${inputClass} font-mono tracking-[0.3em]`} autoComplete="one-time-code"
                  autoCapitalize="none" spellCheck={false} autoFocus value={code}
                  onChange={(event) => setCode(event.target.value)} />
              </label>
              {error && <p role="alert" className={errorClass}>{error}</p>}
              <button type="submit" className={primaryButton} disabled={busy || code.trim() === ""}>
                {busy ? "Doğrulanıyor…" : "Giriş"}
              </button>
            </form>
          )}

          {step.kind === "enroll" && (
            <div className="flex flex-col gap-4">
              <p role="alert" className={errorClass}>
                Bu hesapta iki adımlı doğrulama kurulu değil. Yönetim girişi için zorunludur; önce normal sitedeki
                hesap güvenliği ayarlarından kur, sonra tekrar dene.
              </p>
              <button type="button" className={`${linkButton} self-start`}
                onClick={() => { setStep({ kind: "credentials" }); setError(null); }}>
                ← Girişe dön
              </button>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
