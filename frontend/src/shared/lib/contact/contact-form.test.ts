import { describe, expect, it } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import {
  contactLimits,
  contactSubjects,
  emptyContactValues,
  firstInvalidField,
  validateContactForm,
  type ContactValues
} from "./contact-form";

const valid: ContactValues = {
  name: "Ada Lovelace",
  email: "ada@example.com",
  subject: "feedback",
  message: "Bu bir deneme mesajıdır."
};

describe("validateContactForm", () => {
  it("boş formda dört alan için de zorunlu hatası verir", () => {
    expect(validateContactForm(emptyContactValues)).toEqual({
      name: "required",
      email: "required",
      subject: "required",
      message: "required"
    });
  });

  it("geçerli değerlerde hata vermez", () => {
    expect(validateContactForm(valid)).toEqual({});
  });

  it("yalnız boşluktan oluşan değerleri boş sayar", () => {
    expect(validateContactForm({ ...valid, name: "   ", message: "\n  \t" })).toEqual({
      name: "required",
      message: "required"
    });
  });

  it.each(["abc", "ada@", "@example.com", "ada@example", "ada@@example.com", "a da@example.com", "ada@.com", "ada@example..com"])(
    "geçersiz e-posta biçimini reddeder: %s",
    (email) => {
      expect(validateContactForm({ ...valid, email }).email).toBe("invalid");
    }
  );

  it.each(["ada@example.com", "ada.lovelace+test@mail.example.co.uk", " ada@example.com "])(
    "geçerli e-posta biçimini kabul eder: %s",
    (email) => {
      expect(validateContactForm({ ...valid, email }).email).toBeUndefined();
    }
  );

  it("ad, e-posta ve mesaj üst sınırlarını uygular", () => {
    expect(validateContactForm({ ...valid, name: "a".repeat(contactLimits.nameMax) }).name).toBeUndefined();
    expect(validateContactForm({ ...valid, name: "a".repeat(contactLimits.nameMax + 1) }).name).toBe("tooLong");
    expect(
      validateContactForm({ ...valid, email: `${"a".repeat(contactLimits.emailMax)}@example.com` }).email
    ).toBe("tooLong");
    expect(
      validateContactForm({ ...valid, message: "a".repeat(contactLimits.messageMax) }).message
    ).toBeUndefined();
    expect(
      validateContactForm({ ...valid, message: "a".repeat(contactLimits.messageMax + 1) }).message
    ).toBe("tooLong");
  });

  it("mesaj alt sınırını uygular", () => {
    expect(
      validateContactForm({ ...valid, message: "a".repeat(contactLimits.messageMin - 1) }).message
    ).toBe("tooShort");
    expect(
      validateContactForm({ ...valid, message: "a".repeat(contactLimits.messageMin) }).message
    ).toBeUndefined();
  });

  it("listede olmayan konuyu reddeder", () => {
    expect(
      validateContactForm({ ...valid, subject: "hacked" as unknown as ContactValues["subject"] }).subject
    ).toBe("invalid");
  });

  it("ilk hatalı alanı form sırasına göre verir", () => {
    expect(firstInvalidField({ message: "required", email: "invalid" })).toBe("email");
    expect(firstInvalidField({})).toBeNull();
  });
});

describe("contact i18n", () => {
  it.each([
    ["tr", tr],
    ["en", en]
  ] as const)("%s: konu etiketleri ve hata metinleri tamdır", (_, messages) => {
    for (const subject of contactSubjects) {
      expect(messages.Contact.subjects[subject].trim()).not.toBe("");
    }
    expect(Object.keys(messages.Contact.errors.name)).toEqual(["required", "tooLong"]);
    expect(Object.keys(messages.Contact.errors.email)).toEqual(["required", "invalid", "tooLong"]);
    expect(Object.keys(messages.Contact.errors.subject)).toEqual(["required", "invalid"]);
    expect(Object.keys(messages.Contact.errors.message)).toEqual(["required", "tooShort", "tooLong"]);
  });

  it("başarı metni gönderilmediğini söyler ve gönderildi iddiası içermez", () => {
    expect(tr.Contact.validated).toBe(
      "Form doğrulaması başarılı. Bu bir demo olduğu için mesajınız gönderilmedi."
    );
    for (const messages of [tr, en]) {
      const all = JSON.stringify(messages.Contact);
      expect(all).not.toMatch(/başarıyla gönderildi|ekibimiz size dönecek|talebiniz oluşturuldu/i);
      expect(all).not.toMatch(/successfully sent|our team will get back|request (has been )?created/i);
    }
  });
});
