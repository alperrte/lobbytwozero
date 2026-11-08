"use client";

import { useEffect, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { Icon } from "@/shared/components/ui/icons";
import { useInviteCode } from "../hooks/use-social-data";
import { useSocialMutations } from "../hooks/use-social-mutations";
import { formatInviteCode } from "../schemas/invite-code";
import type { InviteCode } from "../types/social.types";
import { DialogError } from "./dialog-error";
import { PanelCard } from "./panel-card";
import { useAnnounce } from "./social-announcer";

type CopyState = "idle" | "copied" | "failed";

/**
 * Kendi davet kodun (SOC-14): okunur 5-5 gösterim, kopyalama, kullanım ve son geçerlilik bilgisi, yeni kod üretme.
 * Kod yoksa "Davet kodu oluştur" sunulur. Yeni kod eski kodu hemen geçersiz kıldığından önce onay alınır.
 */
export function InviteCodeCard() {
  const t = useTranslations("Social");
  const announce = useAnnounce();
  const invite = useInviteCode();
  const mutations = useSocialMutations();
  const [confirming, setConfirming] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  async function create() {
    const outcome = await mutations.createInviteCode();
    if (!outcome.ok) {
      announce(t(`errors.${outcome.error}`), "error");
    }
  }

  async function regenerate() {
    const outcome = await mutations.regenerateInviteCode();
    if (outcome.ok) {
      setConfirming(false);
      announce(t("announce.regenerated"));
    } else {
      setDialogError(t(`errors.${outcome.error}`));
      // Yenileme iki adımdır (iptal + üret). İptal geçip üretim düştüyse ekrandaki kod artık geçersizdir: kart yeniden okunur,
      // kod yoksa "Davet kodu oluştur" görünür. İptal de düştüyse okuma aynı kodu döner, zararı yoktur.
      invite.reload();
    }
  }

  function askRegenerate() {
    setDialogError(null);
    setConfirming(true);
  }

  let body;
  if (invite.loading) {
    body = <div aria-hidden="true" className="h-14 animate-pulse rounded-control bg-surface-elevated motion-reduce:animate-none" />;
  } else if (invite.data) {
    body = <ActiveInviteCode code={invite.data} busy={mutations.busy} onRegenerate={askRegenerate} />;
  } else {
    body = (
      <div>
        <p className="text-sm text-text-secondary">{t("invite.none")}</p>
        <button
          type="button"
          onClick={create}
          disabled={mutations.busy}
          aria-busy={mutations.busy || undefined}
          className={buttonClass("primary", "md", "mt-3 w-full")}
        >
          {mutations.busy ? t("invite.creating") : t("invite.create")}
        </button>
      </div>
    );
  }

  return (
    <PanelCard icon="share" title={t("invite.title")}>
      {body}
      {confirming ? (
        <RegenerateDialog
          busy={mutations.busy}
          error={dialogError}
          onConfirm={regenerate}
          onCancel={() => setConfirming(false)}
        />
      ) : null}
    </PanelCard>
  );
}

/** Etkin kod: gösterim, kopyalama, kullanım/son geçerlilik ve "Yeni kod oluştur". */
function ActiveInviteCode({
  code,
  busy,
  onRegenerate
}: Readonly<{ code: InviteCode; busy: boolean; onRegenerate: () => void }>) {
  const t = useTranslations("Social");
  const format = useFormatter();
  const [copy, setCopy] = useState<CopyState>("idle");

  useEffect(() => {
    if (copy === "idle") {
      return;
    }
    const timeout = setTimeout(() => setCopy("idle"), 2500);
    return () => clearTimeout(timeout);
  }, [copy]);

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(code.code);
      setCopy("copied");
    } catch {
      setCopy("failed");
    }
  }

  const copied = copy === "copied";
  return (
    <>
      <div className="flex items-stretch gap-2">
        <output
          aria-label={t("invite.codeLabel")}
          className="flex min-w-0 flex-1 items-center justify-center rounded-control border border-border bg-surface-elevated px-3 py-3 font-display text-xl font-bold tracking-[0.12em] whitespace-nowrap text-text-primary select-all"
        >
          {formatInviteCode(code.code)}
        </output>
        <button
          type="button"
          onClick={copyCode}
          aria-label={t("invite.copyAria")}
          className={buttonClass("secondary", "md", "h-auto px-3.5")}
        >
          <Icon name={copied ? "check" : "copy"} className="size-4" />
          <span className="max-sm:sr-only">{copied ? t("invite.copied") : t("invite.copy")}</span>
        </button>
      </div>
      <p aria-live="polite" className="mt-2 min-h-5 text-sm text-text-secondary">
        <CopyStatus state={copy} />
      </p>
      <p className="mt-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-xs text-text-secondary">
        <span>{t("invite.usage", { used: code.usedCount, max: code.maxUses })}</span>
        <span>{t("invite.expires", { date: format.dateTime(new Date(code.expiresAt), { dateStyle: "medium" }) })}</span>
      </p>
      <button type="button" onClick={onRegenerate} disabled={busy} className={buttonClass("ghost", "sm", "mt-3 -ms-3")}>
        <Icon name="refresh" className="size-4" />
        {t("invite.regenerate")}
      </button>
    </>
  );
}

function CopyStatus({ state }: Readonly<{ state: CopyState }>) {
  const t = useTranslations("Social");
  if (state === "copied") {
    return <span className="text-success">{t("invite.copied")}</span>;
  }
  if (state === "failed") {
    return <span className="text-brand-text">{t("invite.copyFailed")}</span>;
  }
  return t("invite.hint");
}

/** "Yeni kod oluştur" onayı. Eski kod geçersiz olacağı için ilk odak "Vazgeç"tedir (`danger`). */
function RegenerateDialog({
  busy,
  error,
  onConfirm,
  onCancel
}: Readonly<{ busy: boolean; error: string | null; onConfirm: () => void; onCancel: () => void }>) {
  const t = useTranslations("Social");
  return (
    <ConfirmDialog
      title={t("invite.regenerateTitle")}
      description={t("invite.regenerateBody")}
      confirmLabel={busy ? t("invite.regenerating") : t("invite.regenerate")}
      cancelLabel={t("dialogs.cancel")}
      busy={busy}
      tone="danger"
      onConfirm={onConfirm}
      onCancel={onCancel}
    >
      {error ? <DialogError message={error} /> : null}
    </ConfirmDialog>
  );
}
