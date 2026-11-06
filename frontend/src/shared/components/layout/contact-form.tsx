"use client";

import { useId, useState, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import {
  contactLimits,
  contactSubjects,
  emptyContactValues,
  firstInvalidField,
  validateContactForm,
  type ContactErrors,
  type ContactField,
  type ContactValues
} from "@/shared/lib/contact/contact-form";
import { demoContactSubmit, type ContactSubmitAdapter } from "@/shared/lib/contact/contact-submit";

type ContactFormProps = {
  /** Gönderim adaptörü. Backend olmadığı için varsayılan demo adaptördür (yan etkisiz). */
  submitAdapter?: ContactSubmitAdapter;
};

const maxLength: Record<ContactField, number> = {
  name: contactLimits.nameMax,
  email: contactLimits.emailMax,
  subject: 0,
  message: contactLimits.messageMax
};

const controlClass =
  "block w-full rounded-control border border-control-border bg-surface px-3.5 py-2.5 text-base text-text-primary transition-colors duration-150 placeholder:text-text-secondary hover:border-text-secondary aria-[invalid=true]:border-primary aria-[invalid=true]:ring-1 aria-[invalid=true]:ring-primary motion-reduce:transition-none";

type FieldProps = {
  id: string;
  label: string;
  error?: string;
  hint?: ReactNode;
  children: (aria: { id: string; "aria-invalid": boolean; "aria-describedby": string | undefined }) => ReactNode;
};

/** Etiket + alan + (varsa) ipucu + hata. Hata rengin yanı sıra simge ve metinle de bildirilir. */
function Field({ id, label, error, hint, children }: FieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(" ");

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-text-primary">
        {label}
        <span aria-hidden="true" className="ms-0.5 text-brand-text">
          *
        </span>
      </label>
      {children({
        id,
        "aria-invalid": error !== undefined,
        "aria-describedby": describedBy === "" ? undefined : describedBy
      })}
      {hint ? (
        <div id={hintId} className="mt-1.5 text-xs text-text-secondary">
          {hint}
        </div>
      ) : null}
      {error ? (
        <p id={errorId} className="mt-1.5 flex items-start gap-1.5 text-sm font-medium text-brand-text">
          <Icon name="info" className="mt-0.5 size-4 shrink-0" />
          <span>{error}</span>
        </p>
      ) : null}
    </div>
  );
}

/**
 * İletişim formu (YALNIZ DEMO). Doğrulama başarılı olunca "doğrulama başarılı, demo olduğu için gönderilmedi"
 * bilgisi gösterilir; veri hiçbir yere iletilmez, saklanmaz ve günlüğe yazılmaz. Gönderim `submitAdapter`
 * arkasındadır; şu an yalnız yan etkisiz demo adaptör vardır.
 *
 * Erişilebilirlik: alan başına hata, `aria-invalid` + `aria-describedby`, ilk hatalı alana odak ve hata özeti.
 */
export function ContactForm({ submitAdapter = demoContactSubmit }: ContactFormProps) {
  const t = useTranslations("Contact");
  const baseId = useId();
  const [values, setValues] = useState<ContactValues>(emptyContactValues);
  const [errors, setErrors] = useState<ContactErrors>({});
  const [attempted, setAttempted] = useState(false);
  const [validated, setValidated] = useState(false);

  const fieldId = (field: ContactField) => `${baseId}-${field}`;
  const errorText = (field: ContactField) => {
    const code = errors[field];
    if (code === undefined) {
      return undefined;
    }
    return t(`errors.${field}.${code}`, { max: maxLength[field], min: contactLimits.messageMin });
  };
  const errorCount = Object.keys(errors).length;

  function update(next: ContactValues) {
    setValues(next);
    setValidated(false);
    if (attempted) {
      setErrors(validateContactForm(next));
    }
  }

  function onChange(field: ContactField) {
    return (event: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
      update({ ...values, [field]: event.target.value });
    };
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const found = validateContactForm(values);
    setAttempted(true);
    setErrors(found);

    const invalid = firstInvalidField(found);
    if (invalid) {
      setValidated(false);
      const element = form.elements.namedItem(invalid);
      if (element instanceof HTMLElement) {
        element.focus();
      }
      return;
    }

    await submitAdapter(values);
    setValidated(true);
  }

  return (
    <form
      noValidate
      onSubmit={onSubmit}
      aria-label={t("formLabel")}
      className="rounded-panel border border-border bg-surface p-5 sm:p-6 lg:p-8"
    >
      <p
        role="note"
        className="mb-6 flex items-start gap-2.5 rounded-control border border-border bg-surface-elevated px-4 py-3 text-sm text-text-primary"
      >
        <Icon name="info" className="mt-0.5 size-4 shrink-0 text-text-secondary" />
        <span>{t("demoNotice")}</span>
      </p>

      {errorCount > 0 ? (
        <p
          role="alert"
          className="mb-6 flex items-start gap-2.5 rounded-control border border-primary/60 bg-primary/5 px-4 py-3 text-sm font-medium text-text-primary"
        >
          <Icon name="info" className="mt-0.5 size-4 shrink-0 text-brand-text" />
          <span>{t("errorSummary", { count: errorCount })}</span>
        </p>
      ) : null}

      <div className="grid gap-5 sm:grid-cols-2">
        <Field id={fieldId("name")} label={t("fields.name")} error={errorText("name")}>
          {(aria) => (
            <input
              {...aria}
              name="name"
              type="text"
              autoComplete="name"
              maxLength={contactLimits.nameMax}
              value={values.name}
              onChange={onChange("name")}
              className={controlClass}
            />
          )}
        </Field>

        <Field id={fieldId("email")} label={t("fields.email")} error={errorText("email")}>
          {(aria) => (
            <input
              {...aria}
              name="email"
              type="email"
              autoComplete="email"
              inputMode="email"
              maxLength={contactLimits.emailMax}
              value={values.email}
              onChange={onChange("email")}
              className={controlClass}
            />
          )}
        </Field>

        <div className="sm:col-span-2">
          <Field id={fieldId("subject")} label={t("fields.subject")} error={errorText("subject")}>
            {(aria) => (
              <div className="relative">
                <select
                  {...aria}
                  name="subject"
                  value={values.subject}
                  onChange={onChange("subject")}
                  className={`${controlClass} appearance-none pe-10`}
                >
                  <option value="">{t("subjectPlaceholder")}</option>
                  {contactSubjects.map((subject) => (
                    <option key={subject} value={subject}>
                      {t(`subjects.${subject}`)}
                    </option>
                  ))}
                </select>
                <Icon
                  name="chevron"
                  className="pointer-events-none absolute end-3 top-1/2 size-4 -translate-y-1/2 text-text-secondary"
                />
              </div>
            )}
          </Field>
        </div>

        <div className="sm:col-span-2">
          <Field
            id={fieldId("message")}
            label={t("fields.message")}
            error={errorText("message")}
            hint={
              <span className="flex justify-between gap-3">
                <span>{t("messageHint", { min: contactLimits.messageMin })}</span>
                <span data-testid="message-counter">
                  {t("counter", { count: values.message.length, max: contactLimits.messageMax })}
                </span>
              </span>
            }
          >
            {(aria) => (
              <textarea
                {...aria}
                name="message"
                rows={6}
                maxLength={contactLimits.messageMax}
                value={values.message}
                onChange={onChange("message")}
                className={`${controlClass} resize-y`}
              />
            )}
          </Field>
        </div>
      </div>

      <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs text-text-secondary">{t("requiredHint")}</p>
        <button
          type="submit"
          className="inline-flex min-h-11 items-center justify-center rounded-control bg-primary px-6 py-2.5 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 motion-reduce:transition-none"
        >
          {t("submit")}
        </button>
      </div>

      <div role="status" aria-live="polite" className="empty:hidden">
        {validated ? (
          <p className="mt-5 flex items-start gap-2.5 rounded-control border border-border bg-surface-elevated px-4 py-3 text-sm font-medium text-text-primary">
            <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand-text" />
            <span>{t("validated")}</span>
          </p>
        ) : null}
      </div>
    </form>
  );
}
