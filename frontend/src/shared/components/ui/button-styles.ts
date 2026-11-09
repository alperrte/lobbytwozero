import { cn } from "@/shared/utils/cn";

/**
 * Ortak düğme görünümü (LTZ tasarım standardı §4): yalnız semantik tokenlar.
 * - `primary`: marka kırmızısı dolgu, beyaz metin (≈4,75:1) — sayfadaki ana eylem.
 * - `secondary`: koyu yüzey, ince kenarlık; hover'da kırmızı kenar vurgusu.
 * - `ghost`: dolgusuz; hover'da ikincil yüzey.
 * Pasif hâl (`disabled` veya `aria-disabled`) kontrastı düşürür ve etkileşimi kapatır.
 */
export type ButtonVariant = "primary" | "secondary" | "ghost";
export type ButtonSize = "sm" | "md";

const base =
  "inline-flex min-w-0 items-center justify-center gap-2 rounded-control font-semibold whitespace-nowrap transition-[background-color,border-color,color,box-shadow] duration-150 ease-out disabled:cursor-not-allowed disabled:opacity-55 aria-disabled:cursor-not-allowed aria-disabled:opacity-55 motion-reduce:transition-none";

const variants: Record<ButtonVariant, string> = {
  primary:
    "bg-primary text-primary-foreground hover:bg-primary/88 hover:shadow-[0_0_0_3px_color-mix(in_srgb,var(--ltz-primary)_22%,transparent)] disabled:hover:bg-primary disabled:hover:shadow-none aria-disabled:hover:bg-primary aria-disabled:hover:shadow-none",
  secondary:
    "border border-border bg-surface-elevated text-text-primary hover:border-primary/60 disabled:hover:border-border aria-disabled:hover:border-border",
  ghost:
    "text-text-secondary hover:bg-surface-elevated hover:text-text-primary disabled:hover:bg-transparent aria-disabled:hover:bg-transparent"
};

const sizes: Record<ButtonSize, string> = {
  sm: "h-9 px-3 text-sm",
  md: "h-10 px-4 text-sm"
};

export function buttonClass(variant: ButtonVariant, size: ButtonSize = "md", className?: string): string {
  return cn(base, variants[variant], sizes[size], className);
}
