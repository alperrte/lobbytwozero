/**
 * İletişim formu: alan tanımları, sınırlar ve elle yazılmış doğrulama (ek bağımlılık yok).
 *
 * Form şu an DEMO'dur: veri hiçbir yere gönderilmez, saklanmaz (localStorage/sessionStorage/çerez yok)
 * ve günlüğe yazılmaz. Doğrulama saf bir fonksiyondur; hata MESAJI değil hata KODU döndürür, metni
 * arayüz `Contact.errors.<alan>.<kod>` i18n anahtarından alır.
 */
export const contactSubjects = [
  "general",
  "feedback",
  "bug",
  "collaboration",
  "other"
] as const;

export type ContactSubject = (typeof contactSubjects)[number];

export const contactFieldOrder = ["name", "email", "subject", "message"] as const;

export type ContactField = (typeof contactFieldOrder)[number];

export const contactLimits = {
  nameMax: 80,
  emailMax: 254,
  messageMin: 10,
  messageMax: 1000
} as const;

export type ContactValues = {
  name: string;
  email: string;
  /** Seçili konu ya da henüz seçilmediyse boş metin. */
  subject: ContactSubject | "";
  message: string;
};

export const emptyContactValues: ContactValues = {
  name: "",
  email: "",
  subject: "",
  message: ""
};

/** Alan başına olası hata kodları; her kod `Contact.errors.<alan>.<kod>` i18n anahtarına karşılık gelir. */
export type ContactErrorCodes = {
  name: "required" | "tooLong";
  email: "required" | "invalid" | "tooLong";
  subject: "required" | "invalid";
  message: "required" | "tooShort" | "tooLong";
};

export type ContactErrors = { [F in ContactField]?: ContactErrorCodes[F] };

/** Basit biçim denetimi: boşluksuz yerel kısım, tek `@`, noktalı alan adı. Teslim edilebilirlik doğrulanmaz. */
const emailPattern = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

export function validateContactForm(values: ContactValues): ContactErrors {
  const errors: ContactErrors = {};

  const name = values.name.trim();
  if (name === "") {
    errors.name = "required";
  } else if (name.length > contactLimits.nameMax) {
    errors.name = "tooLong";
  }

  const email = values.email.trim();
  if (email === "") {
    errors.email = "required";
  } else if (email.length > contactLimits.emailMax) {
    errors.email = "tooLong";
  } else if (!emailPattern.test(email)) {
    errors.email = "invalid";
  }

  if (values.subject === "") {
    errors.subject = "required";
  } else if (!contactSubjects.includes(values.subject)) {
    errors.subject = "invalid";
  }

  const message = values.message.trim();
  if (message === "") {
    errors.message = "required";
  } else if (message.length < contactLimits.messageMin) {
    errors.message = "tooShort";
  } else if (values.message.length > contactLimits.messageMax) {
    errors.message = "tooLong";
  }

  return errors;
}

/** Hatalı alanlardan formdaki sıraya göre ilkini verir (odak için). */
export function firstInvalidField(errors: ContactErrors): ContactField | null {
  return contactFieldOrder.find((field) => errors[field] !== undefined) ?? null;
}
