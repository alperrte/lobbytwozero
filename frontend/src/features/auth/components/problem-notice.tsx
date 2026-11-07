"use client";

import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import type { AuthErrorCode } from "../types/auth.types";

/** `Auth.errors.<kod>` anahtarı olan sunucu kodları; diğerleri genel hata metnine (`UNKNOWN`) düşer. */
const MESSAGE_CODES: ReadonlySet<AuthErrorCode> = new Set<AuthErrorCode>([
  "INVALID_CREDENTIALS",
  "EMAIL_NOT_VERIFIED",
  "ACCOUNT_SUSPENDED",
  "ACCOUNT_BANNED",
  "ACCOUNT_UNAVAILABLE",
  "ACCOUNT_DELETION_PENDING",
  "TWO_FACTOR_ENROLLMENT_REQUIRED",
  "MFA_NOT_SUPPORTED",
  "MFA_FAILED",
  "INVALID_TWO_FACTOR_CODE",
  "CSRF_TOKEN_INVALID",
  "TOO_MANY_ATTEMPTS",
  "SERVICE_BUSY",
  "VALIDATION_FAILED",
  "NETWORK"
]);

/** Bekleme süresini okunur yazar: 60 sn ve üstü dakikaya yuvarlanır (900 sn → "15 dakika"), altı saniye olarak kalır. */
export function useWaitFormatter() {
  const t = useTranslations("Auth");
  return (seconds: number) =>
    seconds >= 60
      ? t("time.minutes", { count: Math.ceil(seconds / 60) })
      : t("time.seconds", { count: seconds });
}

type ProblemNoticeProps = {
  /** Sunucu hata kodu; `null` ise hiçbir şey gösterilmez. */
  code: AuthErrorCode | null;
  /** `TOO_MANY_ATTEMPTS` için `Retry-After` saniyesi (mesaja süre olarak girer). */
  retryAfterSeconds?: number;
};

/**
 * Sunucu hata kodunu çevrilmiş, `role="alert"` bir kutuya çevirir. Sunucunun kendi metni gösterilmez; yalnız kod okunur
 * (iç ayrıntı ve girilen değerler arayüze sızmaz). Hata rengin yanı sıra simge ve metinle bildirilir.
 */
export function ProblemNotice({ code, retryAfterSeconds }: ProblemNoticeProps) {
  const t = useTranslations("Auth");
  const formatWait = useWaitFormatter();
  if (code === null) {
    return null;
  }
  const key = MESSAGE_CODES.has(code) ? code : "UNKNOWN";
  return (
    <p
      role="alert"
      className="flex items-start gap-2.5 rounded-control border border-primary/60 bg-primary/10 px-4 py-3 text-sm font-medium text-text-primary"
    >
      <Icon name="info" className="mt-0.5 size-4 shrink-0 text-brand-text" />
      <span>{t(`errors.${key}`, { time: formatWait(retryAfterSeconds ?? 0) })}</span>
    </p>
  );
}
