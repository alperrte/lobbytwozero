import type { ReactNode } from "react";

type NoticeProps = {
  title: string;
  children: ReactNode;
  headingLevel?: "h2" | "h3";
};

/** Sakin bilgi kutusu: "henüz hazır değil" gibi dürüst durum bildirimleri için. */
export function Notice({ title, children, headingLevel: Heading = "h2" }: NoticeProps) {
  return (
    <section className="max-w-prose rounded-panel border border-border bg-surface p-5 sm:p-6">
      <Heading className="font-display text-lg font-semibold text-text-primary">
        {title}
      </Heading>
      <div className="mt-2 text-text-secondary">{children}</div>
    </section>
  );
}
