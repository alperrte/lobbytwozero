"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import type { VerifyValues } from "../schemas/verify-email.schema";
import { authApi, type RegistrationApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

/** Sunucu, kod geçersiz / süresi dolmuş / deneme hakkı bitmiş hepsi için AYNI hatayı verir (neden ayrımı yapılmaz). */
export type VerifyServerErrors = { code?: "failed" };

export type VerifyOutcome = "verified" | "failed";

/** Sunucudaki 60 sn yeniden gönderme bekleme süresiyle aynı; yalnız görsel kolaylık, sınırı sunucu uygular. */
export const RESEND_WAIT_SECONDS = 60;

/**
 * E-posta doğrulama: kodu gönderme (çift gönderim engeli, `VERIFICATION_FAILED` alana bağlanır, 429 geri sayımı) ve
 * kodu yeniden gönderme (her durumda aynı yanıt; 60 sn bekleme sayacı). Parola yalnız `submit` parametresidir.
 */
export function useVerifyEmail(api: RegistrationApi = authApi) {
  const [pending, setPending] = useState(false);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [serverErrors, setServerErrors] = useState<VerifyServerErrors>({});
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const { remaining: resendCooldown, start: startResendCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(values: VerifyValues): Promise<VerifyOutcome> {
    if (busy.current || cooldown > 0) {
      return "failed";
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    setServerErrors({});
    setResent(false);
    try {
      await api.confirmEmail(values.email.trim(), values.code, values.password);
      return "verified";
    } catch (error) {
      if (!(error instanceof ApiHttpError)) {
        setProblem("UNKNOWN");
        return "failed";
      }
      const code = authErrorCode(error);
      if (code === "VERIFICATION_FAILED") {
        setServerErrors({ code: "failed" });
      } else {
        setProblem(code);
        setRetryAfter(error.retryAfterSeconds);
        if (code === "TOO_MANY_ATTEMPTS" && error.retryAfterSeconds) {
          startCooldown(error.retryAfterSeconds);
        }
      }
      return "failed";
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  async function resend(email: string): Promise<boolean> {
    if (resending || resendCooldown > 0) {
      return false;
    }
    setResending(true);
    setProblem(null);
    setResent(false);
    try {
      await api.resendEmailCode(email.trim());
      setResent(true);
      startResendCooldown(RESEND_WAIT_SECONDS);
      return true;
    } catch (error) {
      if (error instanceof ApiHttpError) {
        const code = authErrorCode(error);
        setProblem(code);
        setRetryAfter(error.retryAfterSeconds);
        if (code === "TOO_MANY_ATTEMPTS" && error.retryAfterSeconds) {
          startResendCooldown(error.retryAfterSeconds);
        }
      } else {
        setProblem("UNKNOWN");
      }
      return false;
    } finally {
      setResending(false);
    }
  }

  return { pending, resending, resent, problem, serverErrors, retryAfter, cooldown, resendCooldown, submit, resend };
}
