"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { authApi, type SteamApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

export type SteamServerErrors = { username?: "taken" | "invalid"; email?: "invalid" };
export type SteamRegistrationOutcome = { status: "registered"; verificationProof: string } | { status: "failed" };

/**
 * Steam ile yeni hesap: bağlantıdaki tek kullanımlık kayıt token'ı + e-posta + kullanıcı adı gönderilir. Başarıda sunucu
 * (her durumda, e-posta kayıtlı olsa bile) bir doğrulama kanıtı verir; hesabın varlığı sızmaz. `REGISTRATION_TOKEN_INVALID`
 * `tokenInvalid` olur (süresi dolmuş/kullanılmış): kullanıcı Steam'den yeniden başlar.
 */
export function useSteamRegistration(api: Pick<SteamApi, "completeSteamRegistration"> = authApi) {
  const [pending, setPending] = useState(false);
  const [tokenInvalid, setTokenInvalid] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [serverErrors, setServerErrors] = useState<SteamServerErrors>({});
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(token: string, email: string, username: string): Promise<SteamRegistrationOutcome> {
    if (busy.current || cooldown > 0) {
      return { status: "failed" };
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    setServerErrors({});
    try {
      const { verificationProof } = await api.completeSteamRegistration(token, email.trim(), username.trim());
      return { status: "registered", verificationProof };
    } catch (error) {
      if (!(error instanceof ApiHttpError)) {
        setProblem("UNKNOWN");
        return { status: "failed" };
      }
      const code = authErrorCode(error);
      if (code === "REGISTRATION_TOKEN_INVALID") {
        setTokenInvalid(true);
      } else if (code === "USERNAME_TAKEN") {
        setServerErrors({ username: "taken" });
      } else if (code === "VALIDATION_FAILED") {
        const fields = new Set(error.fieldErrors.map((item) => item.field));
        if (fields.has("username") || fields.has("email")) {
          setServerErrors({
            ...(fields.has("username") ? { username: "invalid" as const } : {}),
            ...(fields.has("email") ? { email: "invalid" as const } : {})
          });
        } else {
          setProblem(code);
        }
      } else {
        setProblem(code);
        setRetryAfter(error.retryAfterSeconds);
        if (code === "TOO_MANY_ATTEMPTS" && error.retryAfterSeconds) {
          startCooldown(error.retryAfterSeconds);
        }
      }
      return { status: "failed" };
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return { pending, tokenInvalid, problem, serverErrors, retryAfter, cooldown, submit };
}
