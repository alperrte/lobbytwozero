"use client";

import { useContext, useEffect, useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { ApiHttpError } from "@/shared/lib/api/client";
import { SessionContext } from "../hooks/use-session";
import { authApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";
import { ProblemNotice } from "./problem-notice";

type Loaded = { status: "loading" } | { status: "error" } | { status: "ready"; verified: boolean };

const inputClass =
  "min-h-11 w-full max-w-xs rounded-control border border-control-border bg-surface px-3 text-text-primary";
const buttonClass =
  "inline-flex min-h-11 items-center justify-center rounded-control bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary disabled:opacity-60";
const ghostClass = "text-sm text-brand-text hover:underline";

/** Bugünün tarihi (yerel) `YYYY-MM-DD`; tarih alanının üst sınırı. */
function todayIso(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

/**
 * Yaş doğrulaması (18+): kullanıcı doğum tarihini beyan eder, sunucu yalnız "18 yaşından büyük" sonucunu saklar.
 * Doğum tarihi yalnız bileşen durumunda ve isteğin gövdesinde bulunur (URL, depolama, log ve genel duruma yazılmaz);
 * gönderimden sonra alan temizlenir. Başarıda access token yenilenir (`adult_verified` iddiası için) ve oturum yeniden
 * yüklenir. Yetki kararı sunucudadır; burada yalnız beyan ve geri alma vardır.
 */
export function AccountAgeSection() {
  const t = useTranslations("AccountSettings.age");
  const session = useContext(SessionContext);
  const fieldId = useId();
  const hintId = `${fieldId}-hint`;
  const [loaded, setLoaded] = useState<Loaded>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [birthDate, setBirthDate] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [notice, setNotice] = useState<"done" | "revoked" | null>(null);
  const [problem, setProblem] = useState<{ code: AuthErrorCode; retryAfterSeconds?: number } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const me = await authApi.getMe(controller.signal);
        setLoaded({ status: "ready", verified: me.adultVerified === true });
      } catch {
        if (!controller.signal.aborted) setLoaded({ status: "error" });
      }
    })();
    return () => controller.abort();
  }, [attempt]);

  async function run(action: () => Promise<void>, success: "done" | "revoked") {
    if (busy) return;
    setBusy(true);
    setProblem(null);
    setNotice(null);
    try {
      await action();
      try {
        await authApi.refreshSession();
      } catch {
        // Yenileme başarısızsa iddia bir sonraki yenilemede gelir; sonucu bozmayız.
      }
      await session?.reload();
      setConfirming(false);
      setNotice(success);
      setAttempt((value) => value + 1);
    } catch (error) {
      setProblem({
        code: authErrorCode(error),
        retryAfterSeconds: error instanceof ApiHttpError ? error.retryAfterSeconds : undefined
      });
    } finally {
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (birthDate === "") return;
    const value = birthDate;
    setBirthDate("");
    void run(() => authApi.verifyAge(value), "done");
  }

  if (loaded.status === "loading") {
    return <p role="status" className="text-sm text-text-secondary">{t("loading")}</p>;
  }
  if (loaded.status === "error") {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <p role="alert" className="text-sm text-brand-text">{t("loadFailed")}</p>
        <button
          type="button"
          onClick={() => {
            setLoaded({ status: "loading" });
            setAttempt((value) => value + 1);
          }}
          className={ghostClass}
        >
          {t("retry")}
        </button>
      </div>
    );
  }

  return (
    <div className="flex max-w-xl flex-col gap-4">
      {notice !== null ? (
        <p role="status" className="text-sm text-text-primary">{t(notice)}</p>
      ) : null}
      <ProblemNotice code={problem?.code ?? null} retryAfterSeconds={problem?.retryAfterSeconds} />
      {loaded.verified ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm font-semibold text-text-primary">{t("verifiedStatus")}</p>
          <p className="text-sm text-text-secondary">{t("verifiedNote")}</p>
          {confirming ? (
            <div role="group" aria-label={t("revoke")} className="flex flex-col gap-3">
              <p className="text-sm text-text-primary">{t("revokeConfirm")}</p>
              <div className="flex flex-wrap gap-3">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void run(() => authApi.revokeAge(), "revoked")}
                  className={buttonClass}
                >
                  {t("revokeYes")}
                </button>
                <button type="button" onClick={() => setConfirming(false)} className={ghostClass}>
                  {t("revokeNo")}
                </button>
              </div>
            </div>
          ) : (
            <button type="button" onClick={() => setConfirming(true)} className={`${ghostClass} self-start`}>
              {t("revoke")}
            </button>
          )}
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-3" noValidate>
          <p className="text-sm text-text-secondary">{t("unverifiedStatus")}</p>
          <label htmlFor={fieldId} className="text-sm font-medium text-text-primary">{t("birthDate")}</label>
          <input
            id={fieldId}
            type="date"
            value={birthDate}
            max={todayIso()}
            onChange={(event) => setBirthDate(event.target.value)}
            autoComplete="bday"
            aria-describedby={hintId}
            className={inputClass}
          />
          <p id={hintId} className="text-sm text-text-secondary">{t("hint")}</p>
          <Link href="/legal/kvkk" className={`${ghostClass} self-start`}>{t("kvkkLink")}</Link>
          <button type="submit" disabled={busy || birthDate === ""} className={`${buttonClass} self-start`}>
            {busy ? t("submitting") : t("submit")}
          </button>
        </form>
      )}
    </div>
  );
}
