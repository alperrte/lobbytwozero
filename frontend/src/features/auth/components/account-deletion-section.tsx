"use client";

import { useEffect, useId, useState, type FormEvent } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { authApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode, AuthMe, DeletionSupportRequest } from "../types/auth.types";
import { ProblemNotice } from "./problem-notice";

type Loaded =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; me: AuthMe; request: DeletionSupportRequest | null };

const inputClass =
  "min-h-11 w-full rounded-control border border-control-border bg-surface px-3 text-text-primary placeholder:text-text-secondary";
const buttonClass =
  "inline-flex min-h-11 items-center justify-center rounded-control bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary disabled:opacity-60";

/** Kod alanı: 6 haneli sayı TOTP kodudur, başka her şey kurtarma kodu olarak gönderilir. */
function factorOf(value: string): { code: string } | { recoveryCode: string } | undefined {
  const text = value.trim();
  if (text === "") return undefined;
  return /^\d{6}$/.test(text) ? { code: text } : { recoveryCode: text };
}

/**
 * Hesap silme (Auth hesap türüne göre). A türü kendi silme isteğini başlatır (parola → e-posta onayı). B ve C türü
 * (Google/Steam ya da 2FA ile korunan hesap) kendi başına silemez; yöneticiye talep açar ve son talebin durumunu görür.
 * Türü ve zorunlu doğrulama alanlarını `GET /me` belirler; yetki kararı her zaman sunucudadır.
 */
export function AccountDeletionSection() {
  const t = useTranslations("AccountSettings.deletion");
  const [loaded, setLoaded] = useState<Loaded>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const me = await authApi.getMe(controller.signal);
        const supportTier = me.accountTier === "B" || me.accountTier === "C";
        const request = supportTier ? await authApi.getDeletionSupportRequest(controller.signal) : null;
        if (me.accountTier === undefined) throw new Error("tier");
        setLoaded({ status: "ready", me, request });
      } catch {
        if (!controller.signal.aborted) setLoaded({ status: "error" });
      }
    })();
    return () => controller.abort();
  }, [attempt]);

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
          className="text-sm text-brand-text hover:underline"
        >
          {t("retry")}
        </button>
      </div>
    );
  }

  return loaded.me.accountTier === "A" ? (
    <SelfServiceForm />
  ) : (
    <SupportRequestForm me={loaded.me} initial={loaded.request} />
  );
}

function SelfServiceForm() {
  const t = useTranslations("AccountSettings.deletion");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [sent, setSent] = useState(false);
  const id = useId();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy || password === "") return;
    setBusy(true);
    setProblem(null);
    try {
      await authApi.requestAccountDeletion({ password });
      setPassword("");
      setSent(true);
    } catch (error) {
      setProblem(authErrorCode(error));
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return <p role="status" className="text-sm text-text-primary">{t("sentMail")}</p>;
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-xl flex-col gap-3">
      <p className="text-sm text-text-secondary">{t("selfIntro")}</p>
      <label htmlFor={`${id}-password`} className="text-sm font-medium">{t("password")}</label>
      <input
        id={`${id}-password`}
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        className={inputClass}
      />
      <ProblemNotice code={problem} />
      <div>
        <button type="submit" disabled={busy || password === ""} className={buttonClass}>
          {busy ? t("sending") : t("submitDelete")}
        </button>
      </div>
    </form>
  );
}

function SupportRequestForm({ me, initial }: { me: AuthMe; initial: DeletionSupportRequest | null }) {
  const t = useTranslations("AccountSettings.deletion");
  const format = useFormatter();
  const [request, setRequest] = useState(initial);
  const [message, setMessage] = useState("");
  const [password, setPassword] = useState("");
  const [factor, setFactor] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const id = useId();

  const needsPassword = me.passwordSet === true;
  const needsFactor = me.totpEnabled === true;
  // Parolasız hesap TOTP'siz talep açamaz (Auth: DELETION_REQUIRES_SECOND_FACTOR); formu hiç göstermeyiz.
  const blocked = !needsPassword && !needsFactor;
  const pending = request?.status === "PENDING";
  const approved = request?.status === "APPROVED";

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setProblem(null);
    try {
      const note = message.trim();
      const created = await authApi.requestDeletionSupport({
        ...(note !== "" ? { message: note } : {}),
        ...(needsPassword ? { password } : {}),
        ...(needsFactor ? factorOf(factor) : {})
      });
      setRequest(created);
      setPassword("");
      setFactor("");
      setMessage("");
    } catch (error) {
      setProblem(authErrorCode(error));
    } finally {
      setBusy(false);
    }
  }

  const when = request ? format.dateTime(new Date(request.createdAt), { dateStyle: "medium" }) : "";

  return (
    <div className="flex max-w-xl flex-col gap-3">
      <p className="text-sm text-text-secondary">{t("supportIntro")}</p>

      {request ? (
        <p role="status" className="rounded-control border border-border bg-surface-elevated px-3 py-3 text-sm text-text-primary">
          {pending
            ? t("request.pending", { date: when })
            : approved
              ? t("request.approved")
              : request.status === "REJECTED"
                ? t("request.rejected")
                : t("request.other", { status: request.status })}
        </p>
      ) : null}

      {pending || approved ? null : blocked ? (
        <p role="note" className="text-sm text-brand-text">{t("needSecondFactor")}</p>
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
          <label htmlFor={`${id}-message`} className="text-sm font-medium">{t("note")}</label>
          <textarea
            id={`${id}-message`}
            value={message}
            maxLength={500}
            rows={3}
            onChange={(event) => setMessage(event.target.value)}
            className={`${inputClass} py-2`}
          />
          {needsPassword ? (
            <>
              <label htmlFor={`${id}-password`} className="text-sm font-medium">{t("password")}</label>
              <input
                id={`${id}-password`}
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className={inputClass}
              />
            </>
          ) : null}
          {needsFactor ? (
            <>
              <label htmlFor={`${id}-factor`} className="text-sm font-medium">{t("factor")}</label>
              <input
                id={`${id}-factor`}
                type="text"
                inputMode="text"
                autoComplete="one-time-code"
                value={factor}
                onChange={(event) => setFactor(event.target.value)}
                className={inputClass}
              />
            </>
          ) : null}
          <ProblemNotice code={problem} />
          <div>
            <button
              type="submit"
              disabled={busy || (needsPassword && password === "") || (needsFactor && factor.trim() === "")}
              className={buttonClass}
            >
              {busy ? t("sending") : t("submitSupport")}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
