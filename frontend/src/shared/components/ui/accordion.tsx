"use client";

import { useId, useState, type ReactNode } from "react";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";

export type AccordionEntry = {
  id: string;
  title: string;
  content: ReactNode;
};

type AccordionProps = {
  items: readonly AccordionEntry[];
  /** Başlık seviyesi; sayfa hiyerarşisine göre ayarlanır (varsayılan h3). */
  headingLevel?: "h2" | "h3" | "h4";
  className?: string;
};

type AccordionRowProps = {
  entry: AccordionEntry;
  Heading: "h2" | "h3" | "h4";
};

function AccordionRow({ entry, Heading }: AccordionRowProps) {
  const [open, setOpen] = useState(false);
  const baseId = useId();
  const triggerId = `${baseId}-trigger`;
  const panelId = `${baseId}-panel`;

  return (
    <div data-accordion-item={entry.id} data-state={open ? "open" : "closed"}>
      <Heading>
        <button
          id={triggerId}
          type="button"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => setOpen((value) => !value)}
          className="flex w-full items-center justify-between gap-4 rounded-control px-4 py-4 text-left text-[0.9375rem] font-medium text-text-primary transition-colors duration-150 hover:bg-surface-elevated focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus sm:px-5"
        >
          <span className="min-w-0">{entry.title}</span>
          <Icon
            name="chevron"
            aria-hidden="true"
            className={cn(
              "size-4 shrink-0 text-text-secondary transition-transform duration-200 motion-reduce:transition-none",
              open && "rotate-180 text-brand-text"
            )}
          />
        </button>
      </Heading>
      {/* Kapalı panel `inert`: odaklanamaz ve ekran okuyucudan gizlenir; açılış grid satır geçişiyle akar. */}
      <div
        id={panelId}
        inert={!open}
        className={cn(
          "grid transition-[grid-template-rows] duration-200 ease-out motion-reduce:transition-none",
          open ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
        )}
      >
        <div className="overflow-hidden">
          <div className="px-4 pb-5 text-sm leading-relaxed text-text-secondary sm:px-5">
            {entry.content}
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Erişilebilir akordeon: her satır bağımsız açılır, hepsi varsayılan olarak kapalıdır.
 * Düğme `aria-expanded` ve `aria-controls` taşır; Enter/Space ile açılır ve odak halkası görünür.
 * Chevron açılınca döner; hareket azaltma tercihinde geçişler kapanır.
 */
export function Accordion({ items, headingLevel = "h3", className }: AccordionProps) {
  return (
    <div className={cn("divide-y divide-border", className)}>
      {items.map((entry) => (
        <AccordionRow key={entry.id} entry={entry} Heading={headingLevel} />
      ))}
    </div>
  );
}
