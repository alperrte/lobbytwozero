"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CardSnake } from "@/shared/components/ui/card-snake";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { Icon } from "@/shared/components/ui/icons";
import { ADMIN_GROUPS, ADMIN_SECTIONS } from "../sections/registry";
import type { AdminGroup, AdminSection } from "../sections/types";
import { labelClass, linkButton } from "./ui";

type Params = Record<string, string> | null;

type AdminShellProps = {
  onSignOut: () => void;
  /** Test için; varsayılan: `sections/registry.ts`. */
  sections?: AdminSection[];
  groups?: AdminGroup[];
};

/**
 * Yönetim paneli iskeleti: solda servis gruplarına ayrılmış yan menü, sağda seçili bölüm. Bölümler `sections/registry.ts`
 * kaydından gelir; yeni bölüm eklemek bu dosyayı değiştirmez. Açılan bölüm bir daha kapanmaz (durumu korunur, yalnız
 * gizlenir); açılmamış bölüm yüklenmez (`eager` olanlar hariç, ör. rozet gösterenler). Dar ekranda menü çekmeceye döner.
 */
export function AdminShell({ onSignOut, sections = ADMIN_SECTIONS, groups = ADMIN_GROUPS }: AdminShellProps) {
  const first = sections[0]?.id ?? null;
  const [active, setActive] = useState<string | null>(first);
  const [paramsById, setParamsById] = useState<Readonly<Record<string, Params>>>({});
  const [mounted, setMounted] = useState<ReadonlySet<string>>(
    () => new Set([...(first ? [first] : []), ...sections.filter((section) => section.eager).map((section) => section.id)])
  );
  const [badges, setBadges] = useState<Readonly<Record<string, number | null>>>({});
  const [drawer, setDrawer] = useState(false);
  const [confirmOut, setConfirmOut] = useState(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const navigated = useRef(false);

  const open = useCallback(
    (id: string, params?: Record<string, string>) => {
      if (!sections.some((section) => section.id === id)) {
        return;
      }
      navigated.current = true;
      setActive(id);
      setParamsById((current) => (params ? { ...current, [id]: params } : current));
      setMounted((current) => (current.has(id) ? current : new Set(current).add(id)));
      setDrawer(false);
    },
    [sections]
  );

  const setBadge = useCallback((id: string, count: number | null) => {
    setBadges((current) => (current[id] === count ? current : { ...current, [id]: count }));
  }, []);

  // Bölüm değişince odak başlığa taşınır (ekran okuyucu ve klavye); ilk açılışta çalınmaz.
  useEffect(() => {
    if (navigated.current) {
      headingRef.current?.focus();
    }
  }, [active]);

  useEffect(() => {
    if (!drawer) {
      return;
    }
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setDrawer(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [drawer]);

  const current = sections.find((section) => section.id === active) ?? null;

  return (
    <div className="min-h-screen md:flex">
      <header className="flex items-center justify-between gap-3 border-b border-border border-t-2 border-t-primary bg-surface px-4 py-3 md:hidden">
        <span className="font-display text-sm font-semibold uppercase tracking-[0.2em] text-text-primary">
          LTZ <span className="text-primary">{"//"}</span> Yönetim
        </span>
        <button
          type="button"
          onClick={() => setDrawer((value) => !value)}
          aria-expanded={drawer}
          aria-controls="admin-nav"
          aria-label={drawer ? "Menüyü kapat" : "Menüyü aç"}
          className="inline-flex size-11 items-center justify-center border border-control-border text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
        >
          <Icon name={drawer ? "close" : "menu"} className="size-5" />
        </button>
      </header>

      {drawer && (
        <button
          type="button"
          aria-label="Menüyü kapat"
          tabIndex={-1}
          onClick={() => setDrawer(false)}
          className="fixed inset-0 z-30 bg-black/60 md:hidden"
        />
      )}

      <aside
        id="admin-nav"
        aria-label="Yönetim menüsü"
        className={`${drawer ? "flex" : "hidden"} fixed inset-y-0 left-0 z-40 w-64 flex-col border-r border-border border-t-2 border-t-primary bg-surface md:sticky md:top-0 md:flex md:h-screen md:shrink-0`}
      >
        <div className="hidden px-5 py-4 md:block">
          <span className="font-display text-sm font-semibold uppercase tracking-[0.2em] text-text-primary">
            LTZ <span className="text-primary">{"//"}</span> Yönetim
          </span>
        </div>
        <nav aria-label="Bölümler" className="flex flex-1 flex-col gap-5 overflow-y-auto px-3 py-4 md:pt-2">
          {groups.map((group) => {
            const items = sections.filter((section) => section.group === group.id);
            return (
              <div key={group.id} role="group" aria-label={group.label} className="flex flex-col gap-1">
                <p className={`${labelClass} px-2 pb-1`}>{group.label}</p>
                {items.length === 0 && <p className="px-2 text-xs text-text-secondary/70">Henüz bölüm yok</p>}
                {items.map((section) => {
                  const selected = section.id === active;
                  const count = badges[section.id];
                  return (
                    <button
                      key={section.id}
                      type="button"
                      aria-current={selected ? "page" : undefined}
                      onClick={() => open(section.id)}
                      className={`flex min-h-11 items-center gap-3 border-l-2 px-3 text-left text-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-focus ${
                        selected
                          ? "border-primary bg-surface-elevated text-text-primary"
                          : "border-transparent text-text-secondary hover:bg-surface-elevated hover:text-text-primary"
                      }`}
                    >
                      <Icon name={section.icon} className="size-4 shrink-0" />
                      <span className="flex-1">{section.label}</span>
                      {count ? (
                        <span
                          aria-label={`${count} bekleyen`}
                          className="min-w-5 bg-primary px-1.5 text-center font-mono text-[11px] font-semibold leading-5 text-white"
                        >
                          {count}
                        </span>
                      ) : null}
                    </button>
                  );
                })}
              </div>
            );
          })}
        </nav>
        <div className="border-t border-border px-5 py-3">
          <button type="button" onClick={() => setConfirmOut(true)} className={linkButton}>Çıkış</button>
        </div>
      </aside>

      <main className="min-w-0 flex-1">
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 md:px-8">
          {current ? (
            <h1
              ref={headingRef}
              tabIndex={-1}
              className="font-display text-xl font-semibold uppercase tracking-[0.12em] text-text-primary focus:outline-none"
            >
              {current.label}
            </h1>
          ) : (
            <p role="status" className={labelClass}>Henüz yönetim bölümü yok.</p>
          )}
          {sections
            .filter((section) => mounted.has(section.id))
            .map((section) => (
              <SectionHost
                key={section.id}
                section={section}
                visible={section.id === active}
                params={paramsById[section.id] ?? null}
                open={open}
                setBadge={setBadge}
              />
            ))}
        </div>
      </main>
      {confirmOut ? (
        <ConfirmDialog
          title="Yönetim panelinden çıkılsın mı?"
          description="Oturumun kapanır; tekrar girmek için kimlik bilgilerini ve 2FA kodunu yeniden girmen gerekir."
          confirmLabel="Evet, çık"
          cancelLabel="Vazgeç"
          tone="danger"
          frame={<CardSnake initialDelayMs={250} />}
          onCancel={() => setConfirmOut(false)}
          onConfirm={() => {
            setConfirmOut(false);
            onSignOut();
          }}
        />
      ) : null}
    </div>
  );
}

/** Bir bölümü barındırır; `setBadge`i bölüme bağlı ve kararlı tutar (bölüm bunu efekt bağımlılığı yapabilir). */
function SectionHost({
  section,
  visible,
  params,
  open,
  setBadge
}: {
  section: AdminSection;
  visible: boolean;
  params: Params;
  open: (id: string, params?: Record<string, string>) => void;
  setBadge: (id: string, count: number | null) => void;
}) {
  const report = useCallback((count: number | null) => setBadge(section.id, count), [setBadge, section.id]);
  const Body = section.Component;
  return (
    <section aria-label={section.label} hidden={!visible}>
      <Body open={open} setBadge={report} params={params} />
    </section>
  );
}
