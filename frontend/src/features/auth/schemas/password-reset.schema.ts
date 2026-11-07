import { emailPattern, registerLimits } from "./register.schema";

/**
 * Şifremi unuttum / şifre sıfırlama formları (kaynak: `POST /password/forgot`, `POST /password/reset`). Hata MESAJI değil
 * KOD döner; metin `Auth.fields.<alan>.<kod>` anahtarından gelir. Parola kırpılmaz ve hiçbir yerde saklanmaz; gücünü
 * yalnız sunucu belirler (`WEAK_PASSWORD`).
 */
export type ForgotErrors = { email?: "required" | "invalid" | "tooLong" };

export function validateForgotPassword(rawEmail: string): ForgotErrors {
  const email = rawEmail.trim();
  if (email === "") {
    return { email: "required" };
  }
  if (email.length > registerLimits.emailMax) {
    return { email: "tooLong" };
  }
  return emailPattern.test(email) ? {} : { email: "invalid" };
}

export const resetFieldOrder = ["newPassword", "confirmation"] as const;
export type ResetField = (typeof resetFieldOrder)[number];

export type ResetValues = { newPassword: string; confirmation: string };
export const emptyResetValues: ResetValues = { newPassword: "", confirmation: "" };

export type ResetErrors = {
  newPassword?: "required" | "tooShort" | "tooLong";
  confirmation?: "required" | "mismatch";
};

export function validateReset(values: ResetValues): ResetErrors {
  const errors: ResetErrors = {};

  if (values.newPassword === "") {
    errors.newPassword = "required";
  } else if (values.newPassword.length < registerLimits.passwordMin) {
    errors.newPassword = "tooShort";
  } else if (values.newPassword.length > registerLimits.passwordMax) {
    errors.newPassword = "tooLong";
  }

  if (values.confirmation === "") {
    errors.confirmation = "required";
  } else if (values.confirmation !== values.newPassword) {
    errors.confirmation = "mismatch";
  }

  return errors;
}

export function firstInvalidResetField(errors: ResetErrors): ResetField | null {
  return resetFieldOrder.find((field) => errors[field] !== undefined) ?? null;
}
