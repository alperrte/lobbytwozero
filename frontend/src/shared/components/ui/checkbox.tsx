import { useId, type InputHTMLAttributes, type ReactNode } from "react";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";

export type CheckboxProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "size" | "children"> & {
  /** Kutunun yanındaki görünen yazı; erişilebilir ad olarak kullanılır. */
  label: ReactNode;
  /** Etiketin altındaki kısa açıklama (`aria-describedby` ile bağlanır). */
  hint?: ReactNode;
  className?: string;
};

/**
 * LTZ'ye özgü onay kutusu: yerel `<input type="checkbox">` korunur (form, `FormData`, klavye ve
 * ekran okuyucu davranışı hazır gelir), yalnız görünümü özelleştirilir. İşaretliyken `value`
 * (varsayılan "on") `name` altında gönderilir; işaretli değilse form alanı hiç gönderilmez.
 * Kontrollü (`checked` + `onChange`) ya da kontrolsüz (`defaultChecked`) kullanılabilir.
 */
export function Checkbox({ label, hint, className, id, disabled, ...input }: CheckboxProps) {
  const autoId = useId();
  const inputId = id ?? `${autoId}-input`;
  const hintId = `${autoId}-hint`;
  return (
    <div className={cn("flex items-start gap-2.5", className)}>
      <span className="relative mt-px inline-flex size-[18px] shrink-0">
        <input
          {...input}
          id={inputId}
          type="checkbox"
          disabled={disabled}
          aria-describedby={cn(input["aria-describedby"], hint ? hintId : undefined) || undefined}
          className="peer size-[18px] cursor-pointer appearance-none rounded-control border border-control-border bg-[var(--ltz-select-bg,var(--ltz-surface))] transition-colors duration-150 checked:border-primary checked:bg-primary hover:border-text-secondary checked:hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-60"
        />
        <Icon
          name="check"
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 size-[18px] scale-75 text-primary-foreground opacity-0 transition-[opacity,transform] duration-150 peer-checked:scale-100 peer-checked:opacity-100"
        />
      </span>
      <span className="grid gap-0.5">
        <label htmlFor={inputId} className={cn("cursor-pointer text-sm leading-[18px] text-text-primary", disabled && "cursor-not-allowed opacity-60")}>
          {label}
        </label>
        {hint ? <span id={hintId} className="text-xs text-text-secondary">{hint}</span> : null}
      </span>
    </div>
  );
}
