"use client";

import "flag-icons/css/flag-icons.min.css";
import { cn } from "@/shared/utils/cn";

/**
 * Ülke bayrağı (yerel SVG, `flag-icons`; emoji bayrak Windows'ta harf olarak göründüğü için kullanılmaz). Dekoratiftir:
 * ülke adı her zaman yanında metin olarak yazılır. `code` ISO 3166-1 alpha-2'dir.
 */
export function CountryFlag({ code, className }: { code: string; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn("fi inline-block shrink-0 rounded-[2px] ring-1 ring-inset ring-border", `fi-${code.toLowerCase()}`, className)}
    />
  );
}
