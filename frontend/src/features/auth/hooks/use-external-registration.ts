"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { authApi, type ExternalRegistrationApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode, MfaChallenge } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

export type ExternalProvider = "google" | "steam";
export type UsernameError = "taken" | "invalid";
export type ExternalRegistrationOutcome = "authenticated" | "mfa" | "failed";

/**
 * Google/Steam ile ilk girişte kullanıcı adı seçerek hesabı tamamlama. Bağlantıdaki tek kullanımlık kayıt token'ı ve
 * kullanıcı adı gönderilir; e-posta hiçbir zaman gönderilmez. Sonuç giriş gibidir: oturum açıldıysa `"authenticated"`,
 * MFA gerekiyorsa (`"mfa"`) challenge bellekte tutulur ve oturum AÇILMAMIŞ sayılır.
 * `REGISTRATION_TOKEN_INVALID` `tokenInvalid` olur (süresi dolmuş/kullanılmış): kullanıcı sağlayıcıdan yeniden başlar.
 * `ACCOUNT_LINK_REQUIRED` `linkRequired` olur (e-posta bu arada başka hesaba ait). Çift gönderim engellenir.
 */
export function useExternalRegistration(api: ExternalRegistrationApi = authApi) {
  const [pending, setPending] = useState(false);
  const [tokenInvalid, setTokenInvalid] = useState(false);
  const [linkRequired, setLinkRequired] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [usernameError, setUsernameError] = useState<UsernameError | undefined>(undefined);
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const [challenge, setChallenge] = useState<MfaChallenge | null>(null);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(provider: ExternalProvider, token: string, username: string): Promise<ExternalRegistrationOutcome> {
    if (busy.current || cooldown > 0) {
      return "failed";
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    setUsernameError(undefined);
    try {
      const result = await api.completeExternalRegistration(provider, token, username.trim());
      if (result.status === "AUTHENTICATED") {
        return "authenticated";
      }
      setChallenge({ kind: result.status === "MFA_REQUIRED" ? "verify" : "enroll", id: result.challengeId });
      return "mfa";
    } catch (error) {
      if (!(error instanceof ApiHttpError)) {
        setProblem("UNKNOWN");
        return "failed";
      }
      const code = authErrorCode(error);
      if (code === "REGISTRATION_TOKEN_INVALID") {
        setTokenInvalid(true);
      } else if (code === "ACCOUNT_LINK_REQUIRED") {
        setLinkRequired(true);
      } else if (code === "USERNAME_TAKEN") {
        setUsernameError("taken");
      } else if (code === "VALIDATION_FAILED") {
        if (error.fieldErrors.some((item) => item.field === "username")) {
          setUsernameError("invalid");
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
      return "failed";
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return {
    pending,
    tokenInvalid,
    linkRequired,
    problem,
    usernameError,
    retryAfter,
    cooldown,
    challenge,
    clearChallenge: () => setChallenge(null),
    submit
  };
}
