import type { ReactNode } from "react";

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
 * Yerel radyo düğmeleriyle ayar seçimi (klavye okları, grup adı ve seçili durum tarayıcıdan gelir).
 * Seçim anında `onChange` ile uygulanır; "Kaydet" adımı yoktur.
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
            className="flex cursor-pointer items-start gap-3 rounded-control border border-border bg-surface px-3 py-2.5 transition-colors hover:bg-surface-elevated has-[:checked]:border-primary has-[:checked]:bg-surface-elevated has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus"
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
              className="mt-1 size-4 shrink-0 accent-primary"
            />
            {option.leading ? (
              <span className="mt-0.5 shrink-0">{option.leading}</span>
            ) : null}
            <span className="min-w-0">
              <span className="block text-sm font-medium">{option.label}</span>
              {option.hint ? (
                <span className="block text-xs text-text-secondary">{option.hint}</span>
              ) : null}
            </span>
          </label>
        ))}
      </div>
      {note ? <p className="mt-2 text-xs text-text-secondary">{note}</p> : null}
    </fieldset>
  );
}
