"use client";

import { useId, type ComponentProps } from "react";
import { CardSnake } from "@/shared/components/ui/card-snake";
import { Icon } from "@/shared/components/ui/icons";
import { ovalControlClass, ovalControlSize } from "@/shared/components/ui/styles";
import { cn } from "@/shared/utils/cn";

export type SearchFieldProps = Omit<ComponentProps<"input">, "type" | "size" | "className"> & {
  /** Erişilebilir ad. Varsayılan görünmezdir (`sr-only`); `showLabel` ile alanın üstünde görünür. */
  label: string;
  showLabel?: boolean;
  /** `md` 40px (varsayılan), `sm` 36px. */
  size?: "sm" | "md";
  /** Dış sarmalayıcıya uygulanır (genişlik/yerleşim). Görünüm sabittir: LTZ ovali. */
  className?: string;
  /** Input'un kendisine ek sınıf (nadiren: ör. sağda düğme için `pe-10`). */
  inputClassName?: string;
};

/**
 * Ortak arama alanı (frontend-layout.md §3). Tüm listelerde/kataloglarda aynı LTZ ovali: 12px yuvarlak, yükseltilmiş
 * yüzey, başta arama ikonu; fare üzerine gelince çerçevede bir tur kırmızı iz döner (`CardSnake trigger="hover"`).
 * Kontrollü (`value` + `onChange`) ve form (`name` + `defaultValue`) kullanımını destekler; `ref` input'a iletilir.
 */
export function SearchField({
  label,
  showLabel = false,
  size = "md",
  className,
  inputClassName,
  id,
  autoComplete = "off",
  ...inputProps
}: SearchFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  return (
    <div className={cn("min-w-0", className)}>
      <label htmlFor={inputId} className={showLabel ? "mb-1.5 block text-sm font-medium text-text-primary" : "sr-only"}>
        {label}
      </label>
      <div className="relative rounded-xl">
        <Icon
          name="search"
          className="pointer-events-none absolute start-3.5 top-1/2 z-[1] size-4 -translate-y-1/2 text-text-secondary"
        />
        <input
          {...inputProps}
          id={inputId}
          type="search"
          autoComplete={autoComplete}
          className={cn(
            ovalControlClass,
            ovalControlSize[size],
            "ps-10 placeholder:text-text-secondary aria-invalid:border-primary [&::-webkit-search-cancel-button]:cursor-pointer",
            inputClassName
          )}
        />
        <CardSnake trigger="hover" testId="control-snake" />
      </div>
    </div>
  );
}
