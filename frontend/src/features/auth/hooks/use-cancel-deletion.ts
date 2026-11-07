"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { authApi, type DeletionCancelApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

export type CancelReason = "notPending" | "windowClosed" | "invalidCode";

/**
 * Silme talebinden vazgeçme: kimlik + parola (+ TOTP/kurtarma kodu). Parola ve kod yalnız `submit` parametresidir; durumda,
 * günlükte ya da depoda tutulmaz. Yanlış kimlik/parola tek mesajdır (`INVALID_CREDENTIALS`), hesabın varlığı sızmaz.
 */
export function useCancelDeletion(api: DeletionCancelApi = authApi) {
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);
  const [reason, setReason] = useState<CancelReason | null>(null);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(
    identifier: string,
    password: string,
    factor?: { code: string } | { recoveryCode: string }
  ): Promise<boolean> {
    if (busy.current || cooldown > 0) {
      return false;
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    setReason(null);
    try {
      await api.cancelAccountDeletion(identifier.trim(), password, factor);
      setDone(true);
      return true;
    } catch (error) {
      if (!(error instanceof ApiHttpError)) {
        setProblem("UNKNOWN");
        return false;
      }
      const code = authErrorCode(error);
      if (code === "DELETION_NOT_PENDING") {
        setReason("notPending");
      } else if (code === "DELETION_CANCEL_WINDOW_CLOSED") {
        setReason("windowClosed");
      } else if (code === "INVALID_TWO_FACTOR_CODE") {
        setReason("invalidCode");
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
