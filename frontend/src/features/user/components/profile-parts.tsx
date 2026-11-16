"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { editHref, type EditSection } from "./edit-sections";

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
export function Chips({ items, addSection }: { items: string[]; addSection?: EditSection }) {
  return items.length === 0 ? (
    <Empty addSection={addSection} />
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

/** Boş değer. `addSection` verilirse (yalnız kendi profilinde) doğrudan ilgili düzenleme bölümüne giden "Ekle" bağlantısı gösterilir. */
export function Empty({ addSection }: { addSection?: EditSection }) {
  const t = useTranslations("Profile");
  return (
    <span className="inline-flex items-center gap-2 text-text-secondary">
      {t("none")}
      {addSection ? (
        <>
          <span aria-hidden="true">·</span>
          <Link href={editHref(addSection)} className="text-brand-text hover:underline">
            {t("add")}
          </Link>
        </>
      ) : null}
    </span>
  );
}
