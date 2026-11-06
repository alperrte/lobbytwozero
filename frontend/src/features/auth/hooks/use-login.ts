"use client";

import { useEffect, useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import { authApi, type LoginApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";

export type LoginProblem = { code: AuthErrorCode; retryAfterSeconds?: number };
export type LoginOutcome = "authenticated" | "failed";

/**
 * Giriş gönderimi: çift gönderim engeli, hata kodu, 429 geri sayımı. Parola yalnız `submit` çağrısının
 * parametresidir; durumda, günlükte ya da depoda tutulmaz.
 *
 * İki aşamalı doğrulama (MFA) bu sürümde desteklenmez: sunucu çerez yazmadığı için oturum açılmış SAYILMAZ,
 * kullanıcıya `MFA_NOT_SUPPORTED` gösterilir.
 */
export function useLogin(api: LoginApi = authApi) {
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<LoginProblem | null>(null);
  const [cooldown, setCooldown] = useState(0);
  const busy = useRef(false);

  useEffect(() => {
    if (cooldown <= 0) {
      return;
    }
    const timer = setTimeout(() => setCooldown((value) => value - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  async function submit(identifier: string, password: string): Promise<LoginOutcome> {
    if (busy.current || cooldown > 0) {
      return "failed";
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    try {
      const result = await api.login(identifier.trim(), password);
      if (result.status === "AUTHENTICATED") {
        return "authenticated";
      }
      setProblem({ code: "MFA_NOT_SUPPORTED" });
      return "failed";
    } catch (error) {
      if (error instanceof ApiHttpError) {
        const code = authErrorCode(error);
        setProblem({ code, retryAfterSeconds: error.retryAfterSeconds });
        if (code === "TOO_MANY_ATTEMPTS" && error.retryAfterSeconds) {
          setCooldown(error.retryAfterSeconds);
        }
      } else {
        setProblem({ code: "UNKNOWN" });
      }
      return "failed";
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return { pending, problem, cooldown, submit, clearProblem: () => setProblem(null) };
}
