import type { ReactNode } from "react";
import { cn } from "@/shared/utils/cn";

type PageHeaderProps = {
  title: string;
  description: string;
  /** Başlığın altına, açıklamadan sonra gelen isteğe bağlı içerik (ör. kategori bağlantıları). */
  children?: ReactNode;
  className?: string;
  /** Açıklamanın en büyük genişliği; verilmezse sayfalar arası ortak `max-w-2xl` geçerlidir. */
  descriptionClassName?: string;
};

/**
 * Dört public sayfanın ortak başlığı: tek `h1`, kısa açıklama ve altında sabit boşluk.
 * Başlık navbar'ın altında `<main>` üst boşluğundan başlar; ek üst boşluk eklemez.
 */
export function PageHeader({
  title,
  description,
  children,
  className,
  descriptionClassName = "max-w-2xl"
}: PageHeaderProps) {
  return (
    <header data-page-header className={cn("mb-8 sm:mb-10", className)}>
      <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">
        {title}
      </h1>
      <p
        className={cn(
          "mt-3 text-base leading-relaxed text-text-secondary sm:text-lg",
          descriptionClassName
        )}
      >
        {description}
      </p>
      {children ? <div className="mt-6">{children}</div> : null}
    </header>
  );
}
