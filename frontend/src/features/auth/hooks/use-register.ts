"use client";

import { useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";
import type { RegisterValues } from "../schemas/register.schema";
import { authApi, type RegistrationApi } from "../services/auth-api";
import { authErrorCode } from "../services/auth-http";
import type { AuthErrorCode } from "../types/auth.types";
import { useCooldown } from "./use-cooldown";

/** Sunucunun alana bağladığı hatalar (istemci doğrulamasını aşan ya da yalnız sunucunun bildiği). */
export type RegisterServerErrors = {
  username?: "taken" | "invalid";
  email?: "invalid";
  password?: "weak" | "invalid";
};

export type RegisterOutcome = "registered" | "failed";

/** Sunucu `errors[].field` adını form alanına eşler; bilinmeyen alan yok sayılır. */
function mapFieldProblems(problems: readonly { field: string }[]): RegisterServerErrors {
  const mapped: RegisterServerErrors = {};
  for (const { field } of problems) {
    if (field === "username") mapped.username = "invalid";
    else if (field === "email") mapped.email = "invalid";
    else if (field === "password") mapped.password = "invalid";
  }
  return mapped;
}

/**
 * Kayıt gönderimi: çift gönderim engeli, sunucu hatalarının alana bağlanması (`USERNAME_TAKEN`, `WEAK_PASSWORD`,
 * `VALIDATION_FAILED`), genel hata kodu ve 429 geri sayımı. Başarıda (`202`) hesabın var olup olmadığı belli olmaz:
 * her durumda doğrulama ekranına geçilir. Parola yalnız `submit` parametresidir; durumda, günlükte ya da depoda tutulmaz.
 */
export function useRegister(api: RegistrationApi = authApi) {
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<AuthErrorCode | null>(null);
  const [serverErrors, setServerErrors] = useState<RegisterServerErrors>({});
  const [retryAfter, setRetryAfter] = useState<number | undefined>(undefined);
  const { remaining: cooldown, start: startCooldown } = useCooldown();
  const busy = useRef(false);

  async function submit(values: RegisterValues): Promise<RegisterOutcome> {
    if (busy.current || cooldown > 0) {
      return "failed";
    }
    busy.current = true;
    setPending(true);
    setProblem(null);
    setServerErrors({});
    try {
      await api.register(values.email.trim(), values.username.trim(), values.password);
      return "registered";
    } catch (error) {
      if (!(error instanceof ApiHttpError)) {
        setProblem("UNKNOWN");
        return "failed";
      }
      const code = authErrorCode(error);
      if (code === "USERNAME_TAKEN") {
        setServerErrors({ username: "taken" });
      } else if (code === "WEAK_PASSWORD") {
        setServerErrors({ password: "weak" });
      } else if (code === "VALIDATION_FAILED") {
        const mapped = mapFieldProblems(error.fieldErrors);
        if (Object.keys(mapped).length > 0) {
          setServerErrors(mapped);
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

  return { pending, problem, serverErrors, retryAfter, cooldown, submit };
}
