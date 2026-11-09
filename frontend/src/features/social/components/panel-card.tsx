import type { ReactNode } from "react";
import { Icon, type IconName } from "@/shared/components/ui/icons";

type PanelCardProps = {
  icon: IconName;
  title: string;
  /** Başlık satırının sağı (ör. sayı rozeti, "Tümünü Gör"). */
  aside?: ReactNode;
  id?: string;
  children: ReactNode;
};

/** Sağ sütun kartı: ikonlu başlık + içerik. Başlık `h2`'dir (sayfada tek `h1` başlık kartındadır). */
export function PanelCard({ icon, title, aside, id, children }: PanelCardProps) {
  return (
    <section id={id} aria-labelledby={id ? `${id}-title` : undefined} className="rounded-panel border border-border bg-surface p-4 sm:p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 id={id ? `${id}-title` : undefined} className="flex items-center gap-2.5 font-display text-lg font-semibold text-text-primary">
          <Icon name={icon} className="size-5 text-brand-text" />
          {title}
        </h2>
        {aside}
      </div>
      {children}
    </section>
  );
}
