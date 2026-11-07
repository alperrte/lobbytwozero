/**
 * Kayıt formu: sınırlar ve elle yazılmış doğrulama (kaynak: docs/api.md → AUTH, backend `RegisterAccountRequest`).
 * Hata MESAJI değil hata KODU döner; metin `Auth.fields.<alan>.<kod>` i18n anahtarından gelir. Parolanın gücünü yalnız
 * sunucu belirler (`WEAK_PASSWORD`); burada yalnız uzunluk sınırı denetlenir. Parola kırpılmaz ve hiçbir yerde saklanmaz.
 */
export const registerLimits = {
  emailMax: 254,
  usernameMin: 3,
  usernameMax: 20,
  passwordMin: 15,
  passwordMax: 128
} as const;

export const usernamePattern = /^[A-Za-z0-9_]{3,20}$/;

/** Basit biçim denetimi: boşluksuz yerel kısım, tek `@`, noktalı alan adı. Teslim edilebilirlik doğrulanmaz. */
export const emailPattern = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

/** Formdaki sıra (tasarım): kullanıcı adı, e-posta, şifre, koşulların kabulü. */
export const registerFieldOrder = ["username", "email", "password", "acceptTerms"] as const;
export type RegisterField = (typeof registerFieldOrder)[number];

export type RegisterValues = {
  username: string;
  email: string;
  password: string;
  acceptTerms: boolean;
};

export const emptyRegisterValues: RegisterValues = {
  username: "",
  email: "",
  password: "",
  acceptTerms: false
};

export type RegisterErrors = {
  username?: "required" | "pattern";
  email?: "required" | "invalid" | "tooLong";
  password?: "required" | "tooShort" | "tooLong";
  acceptTerms?: "required";
};

export function validateRegister(values: RegisterValues): RegisterErrors {
  const errors: RegisterErrors = {};

  const username = values.username.trim();
  if (username === "") {
    errors.username = "required";
  } else if (!usernamePattern.test(username)) {
    errors.username = "pattern";
  }

  const email = values.email.trim();
  if (email === "") {
    errors.email = "required";
  } else if (email.length > registerLimits.emailMax) {
    errors.email = "tooLong";
  } else if (!emailPattern.test(email)) {
    errors.email = "invalid";
  }

  if (values.password === "") {
    errors.password = "required";
  } else if (values.password.length < registerLimits.passwordMin) {
    errors.password = "tooShort";
  } else if (values.password.length > registerLimits.passwordMax) {
    errors.password = "tooLong";
  }

  if (!values.acceptTerms) {
    errors.acceptTerms = "required";
  }

  return errors;
}

export function firstInvalidRegisterField(errors: RegisterErrors): RegisterField | null {
  return registerFieldOrder.find((field) => errors[field] !== undefined) ?? null;
}
