import type { ReactNode } from "react";
import { Icon } from "@/shared/components/ui/icons";
import { AuthIcon, type AuthIconName } from "./auth-icons";

/** Giriş/kayıt alanları için ortak giriş sınıfı (yalnız semantik tokenlar). */
export const authControlClass =
  "block w-full min-h-12 rounded-xl border border-control-border bg-surface-elevated/60 py-3 ps-11 pe-3.5 text-base text-text-primary transition-colors duration-150 placeholder:text-text-secondary hover:border-text-secondary aria-[invalid=true]:border-primary aria-[invalid=true]:ring-1 aria-[invalid=true]:ring-primary motion-reduce:transition-none";

type AriaProps = {
  id: string;
  "aria-invalid": boolean;
  "aria-describedby": string | undefined;
};

type FormFieldProps = {
  id: string;
  label: string;
  icon: AuthIconName;
  error?: string;
  hint?: string;
  /** Alanın sağında duran eylem (ör. şifre göster/gizle). */
  trailing?: ReactNode;
  children: (aria: AriaProps) => ReactNode;
};

/** Etiket + simgeli alan + (varsa) ipucu + hata. Hata rengin yanı sıra simge ve metinle bildirilir. */
export function FormField({ id, label, icon, error, hint, trailing, children }: FormFieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(" ");

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-text-primary">
        {label}
      </label>
      <div className="relative">
        <AuthIcon
          name={icon}
          className="pointer-events-none absolute start-3.5 top-1/2 size-5 -translate-y-1/2 text-text-secondary"
        />
        {children({
          id,
          "aria-invalid": error !== undefined,
          "aria-describedby": describedBy === "" ? undefined : describedBy
        })}
        {trailing ? <div className="absolute end-1.5 top-1/2 -translate-y-1/2">{trailing}</div> : null}
      </div>
      {hint ? (
        <p id={hintId} className="mt-1.5 text-xs text-text-secondary">
          {hint}
        </p>
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
