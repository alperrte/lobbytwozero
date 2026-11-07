"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { authApi, type TwoFactorApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode, TotpEnrollment } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

export type TwoFactorOutcome = "authenticated" | "failed";
/** Challenge'ın artık kullanılamadığı durum (süre dolmuş / tüketilmiş / deneme hakkı bitmiş): kullanıcı girişe döner. */
export type TwoFactorProblem = { code: AuthErrorCode; retryAfterSeconds?: number };

/**
 * İkinci faktör gönderimi (giriş adımı ve zorunlu kurulum). Kod yalnız çağrı parametresidir; durumda/depoda tutulmaz.
 * `MFA_FAILED` challenge'ın bittiğini söyler (`expired`): yanlış kod ise `INVALID_TWO_FACTOR_CODE` (kurulumda) ya da yine
 * `MFA_FAILED` (giriş adımında, sunucu ayrım yapmaz). Sunucu metni gösterilmez; yalnız kod çevrilir.
 */
export function useTwoFactor(api: TwoFactorApi = authApi) {
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<TwoFactorProblem | null>(null);
  const [enrollment, setEnrollment] = useState<TotpEnrollment | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function run<T>(action: () => Promise<T>): Promise<{ ok: true; value: T } | { ok: false }> {
    if (busy.current || cooldown > 0) {
      return { ok: false };
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    try {
      return { ok: true, value: await action() };
    } catch (error) {
      if (error instanceof ApiHttpError) {
        const code = authErrorCode(error);
        setProblem({ code, retryAfterSeconds: error.retryAfterSeconds });
        if (code === "TOO_MANY_ATTEMPTS" && error.retryAfterSeconds) {
          startCooldown(error.retryAfterSeconds);
        }
      } else {
        setProblem({ code: "UNKNOWN" });
      }
      return { ok: false };
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  async function verify(challengeId: string, factor: { code: string } | { recoveryCode: string }): Promise<TwoFactorOutcome> {
    const result = await run(() => api.verifyTwoFactor(challengeId, factor));
    return result.ok ? "authenticated" : "failed";
  }

  async function startEnrollment(challengeId: string): Promise<boolean> {
    const result = await run(() => api.startTwoFactorEnrollment(challengeId));
    if (result.ok) {
      setEnrollment(result.value);
    }
    return result.ok;
  }

  /** Başarıda oturum açılmıştır; kurtarma kodları kullanıcı kaydedene kadar bellekte gösterilir. */
  async function completeEnrollment(challengeId: string, code: string): Promise<boolean> {
    const result = await run(() => api.completeTwoFactorEnrollment(challengeId, code));
    if (result.ok) {
      setRecoveryCodes(result.value.recoveryCodes);
    }
    return result.ok;
  }

  return { pending, problem, cooldown, enrollment, recoveryCodes, verify, startEnrollment, completeEnrollment };
}
