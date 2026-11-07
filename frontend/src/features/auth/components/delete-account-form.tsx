"use client";

import { useId, useState, useSyncExternalStore, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useDeleteAccount } from "../hooks/use-delete-account";
import type { AccountDeletionApi } from "../services/auth-api";
import { forgetFragmentToken, fragmentTokenStore } from "../services/fragment-token";
import { AuthIcon } from "./auth-icons";
import { authControlClass, FormField } from "./form-field";
import { ProblemNotice, useWaitFormatter } from "./problem-notice";

type DeleteAccountFormProps = {
  /** Test için sahte API adaptörü. */
  api?: AccountDeletionApi;
};

function Notice({ tone, children }: { tone: "info" | "error"; children: React.ReactNode }) {
  return (
    <p
      role={tone === "error" ? "alert" : "status"}
      className="flex items-start gap-2.5 rounded-control border border-primary/60 bg-primary/10 px-4 py-3 text-sm font-medium text-text-primary"
    >
      <Icon name={tone === "error" ? "info" : "check"} className="mt-0.5 size-4 shrink-0 text-brand-text" />
      <span>{children}</span>
    </p>
  );
}

const linkClass = "inline-flex text-sm font-medium text-brand-text underline underline-offset-2";

/**
 * Hesap silme onayı. Token e-postadaki bağlantının URL parçasından (`#token=…`) okunur ve adres çubuğundan hemen silinir.
 * Açık onay olarak kullanıcı adı yazdırılır. Oturum gerekir: giriş yoksa kullanıcı yönlendirilir. Başarıda sunucu tüm
 * oturumları kapatır; ekranda 14 günlük geri alma süresi anlatılır.
 */
export function DeleteAccountForm({ api }: DeleteAccountFormProps) {
  const t = useTranslations("Auth");
  const baseId = useId();
  const token = useSyncExternalStore(
    fragmentTokenStore.subscribe,
    fragmentTokenStore.getSnapshot,
    fragmentTokenStore.getServerSnapshot
  );
  const { pending, done, reason, problem, retryAfter, cooldown, submit } = useDeleteAccount(api);
  const formatWait = useWaitFormatter();
  const [username, setUsername] = useState("");
  const [missing, setMissing] = useState(false);

  if (token === undefined) {
    return null; // sunucu görüntüsü / hidrasyon: token henüz okunmadı
  }

  if (done) {
    return (
      <div className="space-y-4">
        <Notice tone="info">{t("deleteAccount.done")}</Notice>
        <p className="text-sm text-text-secondary">{t("deleteAccount.doneDetail")}</p>
        <Link href="/" className={linkClass}>
          {t("deleteAccount.toHome")}
        </Link>
      </div>
    );
  }

  if (token === null) {
    return (
      <div className="space-y-4">
        <Notice tone="error">{t("deleteAccount.tokenInvalid")}</Notice>
        <Link href="/" className={linkClass}>
          {t("deleteAccount.toHome")}
        </Link>
      </div>
    );
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const clean = username.trim();
    if (clean === "") {
      setMissing(true);
      const element = event.currentTarget.elements.namedItem("username");
      if (element instanceof HTMLElement) {
        element.focus();
      }
      return;
    }
    setMissing(false);
    const ok = await submit(token as string, clean);
    setUsername("");
    if (ok) {
      forgetFragmentToken();
    }
  }

  const reasonText =
    reason === "confirmation"
      ? t("deleteAccount.confirmationInvalid")
      : reason === "admin"
        ? t("deleteAccount.adminForbidden")
        : reason === "login"
          ? t("deleteAccount.needLogin")
          : null;

  return (
    <form noValidate onSubmit={onSubmit} aria-label={t("deleteAccount.formLabel")} className="space-y-5">
      <p className="rounded-control border border-border bg-surface-elevated px-4 py-3 text-sm text-text-secondary">
        {t("deleteAccount.warning")}
      </p>
      {reasonText ? <Notice tone="error">{reasonText}</Notice> : null}
      {reason === "login" ? (
        <Link href="/login" className={linkClass}>
          {t("deleteAccount.toLogin")}
        </Link>
      ) : null}
      <ProblemNotice code={problem} retryAfterSeconds={retryAfter} />

      <FormField
        id={`${baseId}-username`}
        label={t("deleteAccount.username")}
        icon="user"
        error={missing ? t("fields.username.required") : undefined}
        hint={t("deleteAccount.usernameHint")}
      >
        {(aria) => (
          <input
            {...aria}
            name="username"
            type="text"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className={authControlClass}
          />
        )}
      </FormField>

      <button
        type="submit"
        disabled={pending || cooldown > 0}
        aria-busy={pending}
        className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-control bg-primary px-6 py-3 text-base font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
      >
        {pending ? (
          t("deleteAccount.submitting")
        ) : cooldown > 0 ? (
          t("login.retryIn", { time: formatWait(cooldown) })
        ) : (
          <>
            {t("deleteAccount.submit")}
            <AuthIcon name="arrow" />
          </>
        )}
      </button>
    </form>
  );
}
