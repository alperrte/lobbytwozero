"use client";

import { useEffect, useId, useState, type FormEvent, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { authApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode, AuthMe, TotpEnrollment } from "../types/auth.types";
import { ProblemNotice } from "./problem-notice";

type Loaded = { status: "loading" } | { status: "error" } | { status: "ready"; me: AuthMe };

const inputClass =
  "min-h-11 w-full rounded-control border border-control-border bg-surface px-3 text-text-primary placeholder:text-text-secondary";
const buttonClass =
  "inline-flex min-h-11 items-center justify-center rounded-control bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-secondary disabled:opacity-60";
const ghostClass = "text-sm text-brand-text hover:underline";

/** Kod alanı: 6 haneli sayı TOTP kodudur, başka her şey kurtarma kodu olarak gönderilir. */
function factorOf(value: string): { code: string } | { recoveryCode: string } | undefined {
  const text = value.trim();
  if (text === "") return undefined;
  return /^\d{6}$/.test(text) ? { code: text } : { recoveryCode: text };
}

/**
 * Hesap güvenliği: hesap düzeyi özeti ve Auth'un sunduğu kullanıcı işlemleri (2FA, Google/Steam bağlama, parola belirleme,
 * e-posta değiştirme, tüm oturumları kapatma). Hangi işlemin açık olduğunu `GET /me` belirler; yetki kararı sunucudadır.
 * Oturumları kapatan işlemlerden sonra kullanıcı yeniden girişe yönlendirilir.
 */
export function AccountSecuritySection() {
  const t = useTranslations("AccountSettings.security");
  const [loaded, setLoaded] = useState<Loaded>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [closed, setClosed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const me = await authApi.getMe(controller.signal);
        if (me.accountTier === undefined) throw new Error("tier");
        setLoaded({ status: "ready", me });
      } catch {
        if (!controller.signal.aborted) setLoaded({ status: "error" });
      }
    })();
    return () => controller.abort();
  }, [attempt]);

  function reload() {
    setLoaded({ status: "loading" });
    setAttempt((value) => value + 1);
  }

  if (closed) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p role="status" className="text-sm text-text-primary">{t("relogin")}</p>
        <Link href="/login" className={buttonClass}>{t("goLogin")}</Link>
      </div>
    );
  }
  if (loaded.status === "loading") {
    return <p role="status" className="text-sm text-text-secondary">{t("loading")}</p>;
  }
  if (loaded.status === "error") {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <p role="alert" className="text-sm text-brand-text">{t("loadFailed")}</p>
        <button type="button" onClick={reload} className={ghostClass}>{t("retry")}</button>
      </div>
    );
  }

  const me = loaded.me;
  return (
    <div className="flex flex-col gap-8">
      <TierSummary me={me} />
      <TwoFactorPanel me={me} onChanged={reload} />
      <LinkProvidersPanel me={me} />
      {me.passwordSet === false ? <PasswordSetPanel me={me} onClosed={() => setClosed(true)} /> : null}
      <EmailChangePanel me={me} onClosed={() => setClosed(true)} />
      <LogoutAllPanel onClosed={() => setClosed(true)} />
    </div>
  );
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex max-w-xl flex-col gap-3">
      <h3 className="text-base font-semibold text-text-primary">{title}</h3>
      {children}
    </div>
  );
}

function TierSummary({ me }: { me: AuthMe }) {
  const t = useTranslations("AccountSettings.security.tier");
  const tier = me.accountTier ?? "A";
  const providers = me.providers ?? [];
  const registration = me.registrationMethod?.toUpperCase();
  const rows: Array<[string, string]> = [
    [t("password"), me.passwordSet ? t("passwordYes") : t("passwordNo")],
    [t("totp"), me.totpEnabled ? t("totpYes") : t("totpNo")],
    [t("providers"), providers.length > 0 ? providers.join(", ") : t("providersNone")]
  ];
  if (registration === "PASSWORD" || registration === "GOOGLE" || registration === "STEAM") {
    rows.push([t("registration"), t(`registration${registration}`)]);
  }
  return (
    <Panel title={t("heading")}>
      <p className="text-sm font-semibold text-text-primary">{t(tier)}</p>
      <p className="text-sm text-text-secondary">{t(`desc${tier}`)}</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-text-secondary">{label}</dt>
            <dd className="text-text-primary">{value}</dd>
          </div>
        ))}
      </dl>
    </Panel>
  );
}

function TwoFactorPanel({ me, onChanged }: { me: AuthMe; onChanged: () => void }) {
  const t = useTranslations("AccountSettings.security");
  const tf = useTranslations("AccountSettings.security.twoFactor");
  const id = useId();
  const [enrollment, setEnrollment] = useState<TotpEnrollment | null>(null);
  const [recovery, setRecovery] = useState<string[] | null>(null);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const enabled = me.totpEnabled === true;

  async function run(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setProblem(null);
    try {
      await action();
    } catch (error) {
      setProblem(authErrorCode(error));
    } finally {
      setBusy(false);
    }
  }

  if (recovery) {
    return (
      <Panel title={tf("recoveryTitle")}>
        <p role="note" className="text-sm text-brand-text">{tf("recoveryWarn")}</p>
        <ul className="grid grid-cols-2 gap-2 font-mono text-sm text-text-primary">
          {recovery.map((item) => <li key={item}>{item}</li>)}
        </ul>
        <div>
          <button
            type="button"
            className={buttonClass}
            onClick={() => {
              setRecovery(null);
              onChanged();
            }}
          >
            {tf("recoverySaved")}
          </button>
        </div>
      </Panel>
    );
  }

  if (!enabled && enrollment) {
    return (
      <Panel title={tf("title")}>
        <form
          noValidate
          className="flex flex-col gap-3"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            void run(async () => {
              const result = await authApi.activateTotp(code.trim());
              setCode("");
              setEnrollment(null);
              setRecovery(result.recoveryCodes);
            });
          }}
        >
          <p className="text-sm text-text-secondary">{tf("secret")}</p>
          <code className="break-all rounded-control border border-border bg-surface-elevated px-3 py-2 text-sm">{enrollment.secret}</code>
          {enrollment.otpauthUri !== "" ? (
            <a href={enrollment.otpauthUri} className={ghostClass}>{tf("openApp")}</a>
          ) : null}
          <label htmlFor={`${id}-code`} className="text-sm font-medium">{tf("enterCode")}</label>
          <input
            id={`${id}-code`}
            inputMode="numeric"
            autoComplete="one-time-code"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            className={inputClass}
          />
          <ProblemNotice code={problem} />
          <div className="flex items-center gap-4">
            <button type="submit" disabled={busy || code.trim() === ""} className={buttonClass}>
              {busy ? t("sending") : tf("activate")}
            </button>
            <button type="button" className={ghostClass} onClick={() => { setEnrollment(null); setProblem(null); }}>
              {t("cancel")}
            </button>
          </div>
        </form>
      </Panel>
    );
  }

  if (!enabled) {
    return (
      <Panel title={tf("title")}>
        <p className="text-sm text-text-secondary">{tf("offIntro")}</p>
        <ProblemNotice code={problem} />
        <div>
          <button
            type="button"
            disabled={busy}
            className={buttonClass}
            onClick={() => void run(async () => setEnrollment(await authApi.setupTotp()))}
          >
            {busy ? t("sending") : tf("start")}
          </button>
        </div>
      </Panel>
    );
  }

  const needsPassword = me.passwordSet === true;
  return (
    <Panel title={tf("title")}>
      <p className="text-sm text-text-primary">{tf("onIntro")}</p>
      <p className="text-sm text-text-secondary">{tf("disableHint")}</p>
      <form
        noValidate
        className="flex flex-col gap-3"
        onSubmit={(event: FormEvent) => {
          event.preventDefault();
          const factor = factorOf(code);
          if (!factor) return;
          void run(async () => {
            await authApi.disableTotp({ ...(needsPassword ? { password } : {}), ...factor });
            setCode("");
            setPassword("");
            onChanged();
          });
        }}
      >
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
        <label htmlFor={`${id}-factor`} className="text-sm font-medium">{t("factor")}</label>
        <input
          id={`${id}-factor`}
          autoComplete="one-time-code"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          className={inputClass}
        />
        <ProblemNotice code={problem} />
        <div>
          <button
            type="submit"
            disabled={busy || code.trim() === "" || (needsPassword && password === "")}
            className={buttonClass}
          >
            {busy ? t("sending") : tf("disable")}
          </button>
        </div>
      </form>
    </Panel>
  );
}

function LinkProvidersPanel({ me }: { me: AuthMe }) {
  const t = useTranslations("AccountSettings.security");
  const tl = useTranslations("AccountSettings.security.link");
  const id = useId();
  const providers = (me.providers ?? []).map((item) => item.toLowerCase());
  const [target, setTarget] = useState<"google" | "steam" | null>(null);
  const [password, setPassword] = useState("");
  const [factor, setFactor] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const hasPassword = me.passwordSet === true;
  const needsFactor = me.totpEnabled === true;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy || target === null) return;
    setBusy(true);
    setProblem(null);
    try {
      const url = await authApi.startProviderLink(target, { password, ...(needsFactor ? factorOf(factor) : {}) });
      window.location.assign(url);
    } catch (error) {
      setProblem(authErrorCode(error));
      setBusy(false);
    }
  }

  return (
    <Panel title={tl("title")}>
      <p className="text-sm text-text-secondary">{tl("intro")}</p>
      <ul className="flex flex-col gap-2">
        {(["google", "steam"] as const).map((provider) => (
          <li key={provider} className="flex items-center justify-between gap-3 text-sm">
            <span className="text-text-primary">{tl(provider)}</span>
            {providers.includes(provider) ? (
              <span className="text-text-secondary">{tl("linked")}</span>
            ) : (
              <button
                type="button"
                disabled={!hasPassword}
                onClick={() => { setTarget(provider); setProblem(null); }}
                className={`${ghostClass} disabled:opacity-60 disabled:no-underline`}
              >
                {tl("connect")}
              </button>
            )}
          </li>
        ))}
      </ul>
      {!hasPassword ? <p role="note" className="text-sm text-text-secondary">{tl("needPassword")}</p> : null}
      {target !== null && hasPassword ? (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
          <label htmlFor={`${id}-password`} className="text-sm font-medium">{t("password")}</label>
          <input
            id={`${id}-password`}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className={inputClass}
          />
          {needsFactor ? (
            <>
              <label htmlFor={`${id}-factor`} className="text-sm font-medium">{t("factor")}</label>
              <input
                id={`${id}-factor`}
                autoComplete="one-time-code"
                value={factor}
                onChange={(event) => setFactor(event.target.value)}
                className={inputClass}
              />
            </>
          ) : null}
          <ProblemNotice code={problem} />
          <div className="flex items-center gap-4">
            <button
              type="submit"
              disabled={busy || password === "" || (needsFactor && factor.trim() === "")}
              className={buttonClass}
            >
              {busy ? t("sending") : tl("confirm")}
            </button>
            <button type="button" className={ghostClass} onClick={() => { setTarget(null); setPassword(""); setFactor(""); }}>
              {t("cancel")}
            </button>
          </div>
        </form>
      ) : null}
    </Panel>
  );
}

function PasswordSetPanel({ me, onClosed }: { me: AuthMe; onClosed: () => void }) {
  const t = useTranslations("AccountSettings.security");
  const tp = useTranslations("AccountSettings.security.passwordSet");
  const id = useId();
  const needsFactor = me.totpEnabled === true;
  const [factor, setFactor] = useState("");
  const [sent, setSent] = useState(false);
  const [emailCode, setEmailCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [mismatch, setMismatch] = useState(false);

  async function sendCode(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setProblem(null);
    try {
      await authApi.requestPasswordSet(needsFactor ? factorOf(factor) : undefined);
      setSent(true);
    } catch (error) {
      setProblem(authErrorCode(error));
    } finally {
      setBusy(false);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    if (password !== confirmation) {
      setMismatch(true);
      return;
    }
    setMismatch(false);
    setBusy(true);
    setProblem(null);
    try {
      await authApi.setPassword(emailCode.trim(), password, confirmation);
      setPassword("");
      setConfirmation("");
      onClosed();
    } catch (error) {
      setProblem(authErrorCode(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title={tp("title")}>
      <p className="text-sm text-text-secondary">{tp("intro")}</p>
      {!sent ? (
        <form onSubmit={sendCode} noValidate className="flex flex-col gap-3">
          {needsFactor ? (
            <>
              <label htmlFor={`${id}-factor`} className="text-sm font-medium">{t("factor")}</label>
              <input
                id={`${id}-factor`}
                autoComplete="one-time-code"
                value={factor}
                onChange={(event) => setFactor(event.target.value)}
                className={inputClass}
              />
            </>
          ) : null}
          <ProblemNotice code={problem} />
          <div>
            <button type="submit" disabled={busy || (needsFactor && factor.trim() === "")} className={buttonClass}>
              {busy ? t("sending") : tp("sendCode")}
            </button>
          </div>
        </form>
      ) : (
        <form onSubmit={submit} noValidate className="flex flex-col gap-3">
          <label htmlFor={`${id}-code`} className="text-sm font-medium">{tp("emailCode")}</label>
          <input
            id={`${id}-code`}
            autoComplete="one-time-code"
            value={emailCode}
            onChange={(event) => setEmailCode(event.target.value)}
            className={inputClass}
          />
          <label htmlFor={`${id}-new`} className="text-sm font-medium">{tp("newPassword")}</label>
          <input
            id={`${id}-new`}
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className={inputClass}
          />
          <label htmlFor={`${id}-confirm`} className="text-sm font-medium">{tp("confirmation")}</label>
          <input
            id={`${id}-confirm`}
            type="password"
            autoComplete="new-password"
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            className={inputClass}
          />
          {mismatch ? <p role="alert" className="text-sm text-brand-text">{tp("mismatch")}</p> : null}
          <ProblemNotice code={problem} />
          <div>
            <button
              type="submit"
              disabled={busy || emailCode.trim() === "" || password === "" || confirmation === ""}
              className={buttonClass}
            >
              {busy ? t("sending") : tp("submit")}
            </button>
          </div>
        </form>
      )}
    </Panel>
  );
}

function EmailChangePanel({ me, onClosed }: { me: AuthMe; onClosed: () => void }) {
  const t = useTranslations("AccountSettings.security");
  const te = useTranslations("AccountSettings.security.email");
  const id = useId();
  const needsPassword = me.passwordSet === true;
  const needsFactor = me.totpEnabled === true;
  const [newEmail, setNewEmail] = useState("");
  const [password, setPassword] = useState("");
  const [factor, setFactor] = useState("");
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);

  if (me.registrationMethod?.toUpperCase() === "GOOGLE") {
    return (
      <Panel title={te("title")}>
        <p role="note" className="text-sm text-text-secondary">{te("locked")}</p>
      </Panel>
    );
  }
  if (!needsPassword && !needsFactor) {
    return (
      <Panel title={te("title")}>
        <p role="note" className="text-sm text-text-secondary">{te("needAuth")}</p>
      </Panel>
    );
  }

  async function request(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setProblem(null);
    try {
      await authApi.requestEmailChange({
        newEmail: newEmail.trim(),
        ...(needsPassword ? { password } : {}),
        ...(needsFactor ? factorOf(factor) : {})
      });
      setPassword("");
      setFactor("");
      setSent(true);
    } catch (error) {
      setProblem(authErrorCode(error));
    } finally {
      setBusy(false);
    }
  }

  async function confirm(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setProblem(null);
    try {
      await authApi.confirmEmailChange(code.trim());
      onClosed();
    } catch (error) {
      setProblem(authErrorCode(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title={te("title")}>
      {!sent ? (
        <form onSubmit={request} noValidate className="flex flex-col gap-3">
          <label htmlFor={`${id}-email`} className="text-sm font-medium">{te("newEmail")}</label>
          <input
            id={`${id}-email`}
            type="email"
            autoComplete="email"
            value={newEmail}
            onChange={(event) => setNewEmail(event.target.value)}
            className={inputClass}
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
              disabled={busy || newEmail.trim() === "" || (needsPassword && password === "") || (needsFactor && factor.trim() === "")}
              className={buttonClass}
            >
              {busy ? t("sending") : te("send")}
            </button>
          </div>
        </form>
      ) : (
        <form onSubmit={confirm} noValidate className="flex flex-col gap-3">
          <p role="status" className="text-sm text-text-primary">{te("codeSent")}</p>
          <label htmlFor={`${id}-code`} className="text-sm font-medium">{t("code")}</label>
          <input
            id={`${id}-code`}
            autoComplete="one-time-code"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            className={inputClass}
          />
          <ProblemNotice code={problem} />
          <div>
            <button type="submit" disabled={busy || code.trim() === ""} className={buttonClass}>
              {busy ? t("sending") : te("confirm")}
            </button>
          </div>
        </form>
      )}
    </Panel>
  );
}

function LogoutAllPanel({ onClosed }: { onClosed: () => void }) {
  const t = useTranslations("AccountSettings.security.logoutAll");
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);

  async function logout() {
    if (busy) return;
    setBusy(true);
    setProblem(null);
    try {
      await authApi.logoutAll();
      onClosed();
    } catch (error) {
      setProblem(authErrorCode(error));
      setBusy(false);
    }
  }

  return (
    <Panel title={t("title")}>
      <p className="text-sm text-text-secondary">{t("intro")}</p>
      <ProblemNotice code={problem} />
      <div>
        <button
          type="button"
          disabled={busy}
          className={buttonClass}
          onClick={() => (confirming ? void logout() : setConfirming(true))}
        >
          {confirming ? t("confirm") : t("button")}
        </button>
      </div>
    </Panel>
  );
}
