import { emailPattern, registerLimits } from "./register.schema";

/**
 * E-posta doğrulama formu (kaynak: `POST /email-verification/confirm`). Kod 6 hanelidir ve BAŞINDA 0 olabilir: sayıya
 * çevrilmez, metin olarak tutulur. Parolalı kayıtta doğrulama, kayıttaki parolayı da ister (kayıt sahibi kanıtı); kullanıcı
 * sayfayı yenilerse parola kaybolur ve yeniden sorulur. Parola kırpılmaz, hiçbir yerde saklanmaz.
 */
export const verifyCodeLength = 6;
export const verifyFieldOrder = ["email", "code", "password"] as const;
export type VerifyField = (typeof verifyFieldOrder)[number];

export type VerifyValues = { email: string; code: string; password: string };
export const emptyVerifyValues: VerifyValues = { email: "", code: "", password: "" };

export type VerifyErrors = {
  email?: "required" | "invalid" | "tooLong";
  code?: "required" | "pattern";
  password?: "required" | "tooLong";
};

/** Yapıştırılan "123 456" ya da "123-456" gibi girdilerden yalnız rakamları alır ve 6 haneye kırpar. */
export function normalizeVerificationCode(raw: string): string {
  return raw.replace(/\D/g, "").slice(0, verifyCodeLength);
}

function validateEmail(value: string): VerifyErrors["email"] | undefined {
  const email = value.trim();
  if (email === "") {
    return "required";
  }
  if (email.length > registerLimits.emailMax) {
    return "tooLong";
  }
  return emailPattern.test(email) ? undefined : "invalid";
}

export function validateVerifyEmail(values: VerifyValues): VerifyErrors {
  const errors: VerifyErrors = {};

  const email = validateEmail(values.email);
  if (email) {
    errors.email = email;
  }

  if (values.code === "") {
    errors.code = "required";
  } else if (!new RegExp(`^[0-9]{${verifyCodeLength}}$`).test(values.code)) {
    errors.code = "pattern";
  }

  if (values.password === "") {
    errors.password = "required";
  } else if (values.password.length > registerLimits.passwordMax) {
    errors.password = "tooLong";
  }

  return errors;
}

/** Yeniden gönderme yalnız e-posta ister. */
export function validateResendEmail(email: string): VerifyErrors["email"] | undefined {
  return validateEmail(email);
}

export function firstInvalidVerifyField(errors: VerifyErrors): VerifyField | null {
  return verifyFieldOrder.find((field) => errors[field] !== undefined) ?? null;
}
