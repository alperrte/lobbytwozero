"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { authApi, type PasswordResetApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

/**
 * Şifremi unuttum: her durumda aynı sonuç (`202`) gösterilir; hesabın var olup olmadığı belli olmaz. Başarıda
 * `sent` true olur ve e-posta alanı yerine bilgi gösterilir. 429'da geri sayım başlar.
 */
export function useForgotPassword(api: PasswordResetApi = authApi) {
  const [pending, setPending] = useState(false);
  const [sent, setSent] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(email: string): Promise<boolean> {
    if (busy.current || cooldown > 0) {
      return false;
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    try {
      await api.forgotPassword(email.trim());
      setSent(true);
      return true;
    } catch (error) {
      if (error instanceof ApiHttpError) {
        const code = authErrorCode(error);
        setProblem(code);
        setRetryAfter(error.retryAfterSeconds);
        if (code === "TOO_MANY_ATTEMPTS" && error.retryAfterSeconds) {
          startCooldown(error.retryAfterSeconds);
        }
      } else {
        setProblem("UNKNOWN");
      }
      return false;
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return { pending, sent, problem, retryAfter, cooldown, submit };
}

/** Sunucunun alana bağladığı sıfırlama hataları. */
export type ResetServerErrors = {
  newPassword?: "weak" | "same" | "invalid";
};

export type ResetOutcome = "reset" | "failed";

/**
 * Şifre sıfırlama: bağlantıdaki token'la yeni parolayı gönderir. `PASSWORD_RESET_TOKEN_INVALID` (süresi dolmuş/kullanılmış/
 * geçersiz) `tokenInvalid` olarak ayrılır: form yerine "yeni bağlantı iste" yönlendirmesi gösterilir. Parola ve token
 * yalnız `submit` parametresidir; durumda, günlükte ya da depoda tutulmaz.
 */
export function useResetPassword(api: PasswordResetApi = authApi) {
  const [pending, setPending] = useState(false);
  const [tokenInvalid, setTokenInvalid] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [serverErrors, setServerErrors] = useState<ResetServerErrors>({});
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(token: string, newPassword: string, confirmation: string): Promise<ResetOutcome> {
    if (busy.current || cooldown > 0) {
      return "failed";
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    setServerErrors({});
    try {
      await api.resetPassword(token, newPassword, confirmation);
      return "reset";
    } catch (error) {
      if (!(error instanceof ApiHttpError)) {
        setProblem("UNKNOWN");
        return "failed";
      }
      const code = authErrorCode(error);
      if (code === "PASSWORD_RESET_TOKEN_INVALID") {
        setTokenInvalid(true);
      } else if (code === "WEAK_PASSWORD") {
        setServerErrors({ newPassword: "weak" });
      } else if (code === "NEW_PASSWORD_SAME_AS_CURRENT") {
        setServerErrors({ newPassword: "same" });
      } else if (code === "VALIDATION_FAILED" && error.fieldErrors.some((item) => item.field.startsWith("newPassword"))) {
        setServerErrors({ newPassword: "invalid" });
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

  return { pending, tokenInvalid, problem, serverErrors, retryAfter, cooldown, submit };
}
