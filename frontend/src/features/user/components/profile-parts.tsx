"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";

const chipClass =
  "inline-flex items-center rounded-full border border-border bg-surface-elevated px-3 py-1 text-xs font-medium text-text-primary";

/** Profil sayfalarındaki bölüm kartı: başlık ve içerik. */
export function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-panel border border-border bg-surface p-5 sm:p-6">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h2 className="font-display text-base font-semibold">{title}</h2>
      </div>
      {children}
    </section>
  );
}

/** Etiket + değer satırı (`<dl>` içinde kullanılır). */
export function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <dt className="text-sm text-text-secondary">{label}</dt>
      <dd className="text-sm text-text-primary">{children}</dd>
    </div>
  );
}

/** Çip listesi; boşsa "Yok" yazar. */
export function Chips({ items }: { items: string[] }) {
  return items.length === 0 ? (
    <Empty />
  ) : (
    <ul className="flex flex-wrap gap-2">
      {items.map((item) => (
        <li key={item} className={chipClass}>
          {item}
        </li>
      ))}
    </ul>
  );
}

export function Empty() {
  const t = useTranslations("Profile");
  return <span className="text-text-secondary">{t("none")}</span>;
}
