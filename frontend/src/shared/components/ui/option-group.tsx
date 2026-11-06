import type { ReactNode } from "react";
import { Icon } from "@/shared/components/ui/icons";

export type Option<T extends string> = {
  value: T;
  label: string;
  hint?: string;
  leading?: ReactNode;
};

type OptionGroupProps<T extends string> = {
  legend: string;
  name: string;
  value: T;
  options: ReadonlyArray<Option<T>>;
  onChange: (value: T) => void;
  disabled?: boolean;
  note?: string;
  /** Başlık sayfada başka yerde gösteriliyorsa görsel olarak gizler (ekran okuyucu için kalır). */
  hideLegend?: boolean;
};

/**
 * Tam genişlikli seçim satırlarıyla ayar seçimi: solda simge, ortada başlık ve kısa açıklama,
 * sağda seçili durum işareti. Altta yerel radyo düğmeleri durur (klavye okları, grup adı ve
 * seçili durum tarayıcıdan gelir); girdi görsel olarak gizlidir ama odaklanabilir.
 *
 * Seçili satır kontrollü bir kırmızı vurgu alır (ince kenar, hafif yüzey, dolu işaret); kenarlıkların
 * hepsi kırmızı değildir. Seçim anında `onChange` ile uygulanır; "Kaydet" adımı yoktur.
 */
export function OptionGroup<T extends string>({
  legend,
  name,
  value,
  options,
  onChange,
  disabled,
  note,
  hideLegend
}: OptionGroupProps<T>) {
  return (
    <fieldset disabled={disabled} className="min-w-0">
      <legend
        className={hideLegend ? "sr-only" : "mb-2 font-display text-sm font-semibold"}
      >
        {legend}
      </legend>
      <div className="space-y-2">
        {options.map((option) => (
          <label
            key={option.value}
            className="group flex cursor-pointer items-center gap-3 rounded-control border border-border bg-surface px-3 py-3 transition-colors duration-150 hover:bg-surface-elevated has-[:checked]:border-primary/60 has-[:checked]:bg-primary/5 has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus"
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
              className="peer sr-only"
            />
            {option.leading ? (
              <span
                aria-hidden="true"
                className="flex size-9 shrink-0 items-center justify-center rounded-control bg-surface-elevated text-text-secondary transition-colors duration-150 group-has-[:checked]:text-brand-text"
              >
                {option.leading}
              </span>
            ) : null}
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-medium text-text-primary">{option.label}</span>
              {option.hint ? (
                <span className="mt-0.5 block text-xs leading-snug text-text-secondary">
                  {option.hint}
                </span>
              ) : null}
            </span>
            <span
              aria-hidden="true"
              className="flex size-5 shrink-0 items-center justify-center rounded-full border border-control-border text-primary-foreground transition-colors duration-150 group-has-[:checked]:border-primary group-has-[:checked]:bg-primary"
            >
              <Icon
                name="check"
                className="size-3 opacity-0 transition-opacity duration-150 group-has-[:checked]:opacity-100"
              />
            </span>
          </label>
        ))}
      </div>
      {note ? <p className="mt-3 text-xs leading-relaxed text-text-secondary">{note}</p> : null}
    </fieldset>
  );
}
