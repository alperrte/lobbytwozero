/**
 * Yönetim paneli görsel dili: siyah zemin, ince çizgiler, köşesiz yüzeyler, tek vurgu (marka kırmızısı),
 * etiketlerde dar aralıklı büyük harf mono. Süs yok: gradyan, parıltı, yuvarlak kart ve emoji kullanılmaz.
 */
export const labelClass = "font-mono text-[11px] uppercase tracking-[0.14em] text-text-secondary";
export const inputClass =
  "min-h-11 w-full rounded-sm border border-control-border bg-background px-3 text-text-primary placeholder:text-text-secondary/60 focus-visible:border-accent focus-visible:outline-none";
export const primaryButton =
  "inline-flex min-h-11 items-center justify-center rounded-sm bg-primary px-5 font-mono text-xs font-semibold uppercase tracking-[0.14em] text-primary-foreground transition-colors duration-150 hover:bg-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-40";
export const outlineButton =
  "inline-flex min-h-11 items-center justify-center rounded-sm border border-control-border px-5 font-mono text-xs font-semibold uppercase tracking-[0.14em] text-text-primary transition-colors duration-150 hover:border-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-40";
export const linkButton =
  "font-mono text-[11px] uppercase tracking-[0.14em] text-brand-text hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus";
export const surfaceClass = "border border-border bg-surface p-4";
export const errorClass = "border-l-2 border-primary bg-primary/10 px-3 py-2 text-sm text-text-primary";
export const okClass = "border-l-2 border-success bg-success/10 px-3 py-2 text-sm text-text-primary";
