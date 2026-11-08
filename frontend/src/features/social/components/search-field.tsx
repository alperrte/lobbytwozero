"use client";

import { useId } from "react";
import { Icon } from "@/shared/components/ui/icons";

type SearchFieldProps = {
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
};

/** Liste içi arama: görünmez etiket (`sr-only`) + ikonlu alan. Yalnız yüklenen öğeleri süzer; ağ isteği atmaz. */
export function SearchField({ label, placeholder, value, onChange }: SearchFieldProps) {
  const id = useId();
  return (
    <div className="relative min-w-0 flex-1">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <Icon name="search" className="pointer-events-none absolute start-3 top-1/2 size-5 -translate-y-1/2 text-text-secondary" />
      <input
        id={id}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        maxLength={64}
        autoComplete="off"
        className="h-10 w-full rounded-control border border-control-border/60 bg-surface-elevated ps-10 pe-3 text-sm text-text-primary placeholder:text-text-secondary hover:border-control-border focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-focus"
      />
    </div>
  );
}
