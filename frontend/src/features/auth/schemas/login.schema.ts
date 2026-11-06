/**
 * Giriş formu: sınırlar ve elle yazılmış doğrulama (ek bağımlılık yok). Hata MESAJI değil hata KODU döner;
 * metin `Auth.fields.<alan>.<kod>` i18n anahtarından gelir. Parola kırpılmaz ve hiçbir yerde saklanmaz.
 */
export const loginLimits = { identifierMax: 254, passwordMax: 128 } as const;

export const loginFieldOrder = ["identifier", "password"] as const;
export type LoginField = (typeof loginFieldOrder)[number];

export type LoginValues = { identifier: string; password: string };
export const emptyLoginValues: LoginValues = { identifier: "", password: "" };

export type LoginErrors = {
  identifier?: "required" | "tooLong";
  password?: "required" | "tooLong";
};

export function validateLogin(values: LoginValues): LoginErrors {
  const errors: LoginErrors = {};
  const identifier = values.identifier.trim();
  if (identifier === "") {
    errors.identifier = "required";
  } else if (identifier.length > loginLimits.identifierMax) {
    errors.identifier = "tooLong";
  }
  if (values.password === "") {
    errors.password = "required";
  } else if (values.password.length > loginLimits.passwordMax) {
    errors.password = "tooLong";
  }
  return errors;
}

/** Hatalı alanlardan formdaki sıraya göre ilkini verir (odak için). */
export function firstInvalidLoginField(errors: LoginErrors): LoginField | null {
  return loginFieldOrder.find((field) => errors[field] !== undefined) ?? null;
}
