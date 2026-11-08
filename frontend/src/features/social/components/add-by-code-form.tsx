"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { useSocialMutations } from "../hooks/use-social-mutations";
import { validateInviteCode } from "../schemas/invite-code";
import { useAnnounce, useClearAnnouncement } from "./social-announcer";

/** Sayfa başlığındaki "Arkadaş Ekle" düğmesinin odaklayacağı alanın kimliği. */
export const ADD_BY_CODE_INPUT_ID = "social-add-by-code";

/**
 * Arkadaşının davet kodunu girerek istek gönderme (`POST /friend-requests/by-code`). Biçim hatası alanın altında, sunucu hatası
 * (kod bulunamadı, zaten arkadaş…) da aynı yerde gösterilir; düzeltilebilir hatada yazılan kod silinmez. Başarıda alan temizlenir.
 */
export function AddByCodeForm({ heading }: { heading?: "h2" | "h3" | null }) {
  const t = useTranslations("Social");
  const announce = useAnnounce();
  const clearAnnouncement = useClearAnnouncement();
  const mutations = useSocialMutations();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const hintId = useId();
  const errorId = useId();
  const Heading = heading ?? null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // Önceki gönderimin bandı yeni sonucun yanında kalmasın (başarıda yeniden duyurulur).
    clearAnnouncement();
    const issue = validateInviteCode(code);
    if (issue) {
      setError(t(`addByCode.issues.${issue}`));
      inputRef.current?.focus();
      return;
    }
    setError(null);
    const outcome = await mutations.sendByCode(code);
    if (outcome.ok) {
      setCode("");
      announce(t("announce.sent"));
    } else {
      setError(t(`errors.${outcome.error}`));
      inputRef.current?.focus();
    }
  }

  return (
    <form noValidate onSubmit={submit}>
      {Heading ? (
        <Heading className="mb-3 font-display text-base font-semibold text-text-primary">{t("addByCode.title")}</Heading>
      ) : null}
      <label htmlFor={ADD_BY_CODE_INPUT_ID} className="text-sm font-medium text-text-primary">
        {t("addByCode.label")}
      </label>
      <div className="mt-1.5 flex flex-col gap-2 sm:flex-row lg:flex-col xl:flex-row">
        <input
          ref={inputRef}
          id={ADD_BY_CODE_INPUT_ID}
          name="inviteCode"
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
            if (error) {
              setError(null);
            }
          }}
          maxLength={32}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          placeholder={t("addByCode.placeholder")}
          aria-invalid={error ? true : undefined}
          aria-describedby={cn(hintId, error && errorId)}
          className={cn(
            "h-11 min-w-0 grow rounded-control border bg-surface-elevated px-3 font-display tracking-wider text-text-primary uppercase placeholder:font-sans placeholder:tracking-normal placeholder:text-text-secondary placeholder:normal-case focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-focus",
            error ? "border-primary" : "border-control-border/60 hover:border-control-border"
          )}
        />
        <button
          type="submit"
          disabled={mutations.busy}
          aria-busy={mutations.busy || undefined}
          className={buttonClass("primary", "md", "h-11 shrink-0")}
        >
          <Icon name="send" className="size-4" />
          {mutations.busy ? t("addByCode.submitting") : t("addByCode.submit")}
        </button>
      </div>
      <p id={hintId} className="mt-1.5 text-xs text-text-secondary">
        {t("addByCode.hint")}
      </p>
      {error ? (
        <p id={errorId} role="alert" className="mt-1.5 flex items-start gap-1.5 text-sm text-brand-text">
          <Icon name="info" className="mt-0.5 size-4 shrink-0" />
          {error}
        </p>
      ) : null}
    </form>
  );
}
