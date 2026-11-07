"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { authApi, type AccountDeletionApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

export type DeleteReason = "confirmation" | "login" | "admin";

/**
 * Hesap silme onayı: e-postadaki token + kullanıcı adı. Oturum gerekir; 401 `login` (önce giriş), yanlış kullanıcı adı ya da
 * geçersiz/süresi dolmuş token `confirmation` (sunucu ikisini ayırmaz), yönetici hesabı `admin` olur. Token ve kullanıcı adı
 * yalnız `submit` parametresidir; durumda, günlükte ya da depoda tutulmaz.
 */
export function useDeleteAccount(api: AccountDeletionApi = authApi) {
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);
  const [reason, setReason] = useState<DeleteReason | null>(null);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(token: string, username: string): Promise<boolean> {
    if (busy.current || cooldown > 0) {
      return false;
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    setReason(null);
    try {
      await api.confirmAccountDeletion(token, username.trim());
      setDone(true);
      return true;
    } catch (error) {
      if (!(error instanceof ApiHttpError)) {
        setProblem("UNKNOWN");
        return false;
      }
      const code = authErrorCode(error);
      if (code === "DELETION_CONFIRMATION_INVALID") {
        setReason("confirmation");
      } else if (code === "ADMIN_DELETION_FORBIDDEN") {
        setReason("admin");
      } else if (error.status === 401 || code === "SESSION_INVALID") {
        setReason("login");
      } else {
        setProblem(code);
        setRetryAfter(error.retryAfterSeconds);
        if (code === "TOO_MANY_ATTEMPTS" && error.retryAfterSeconds) {
          startCooldown(error.retryAfterSeconds);
        }
      }
      return false;
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return { pending, done, reason, problem, retryAfter, cooldown, submit };
}
