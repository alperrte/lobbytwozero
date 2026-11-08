"use client";

import { useCallback, useEffect, useState, type SyntheticEvent } from "react";
import { createPortal } from "react-dom";
import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import {
  findActiveItem,
  isNavItemAvailable,
  type NavItem as NavItemModel
} from "@/shared/config/navigation";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";

/** Panelin dışında, tooltip ile panel kenarı arasındaki boşluk (px). */
const TOOLTIP_GAP = 10;
/** Tooltip, ekranın üst ve alt kenarına bu kadar yaklaşamaz (px). */
const TOOLTIP_EDGE = 24;

const collapsedCenter =
  "lg:in-data-[sidebar=collapsed]:justify-center lg:in-data-[sidebar=collapsed]:px-0";
const collapsedHidden = "lg:in-data-[sidebar=collapsed]:sr-only";
const row =
  "group relative flex h-11 w-full lg:h-10 items-center gap-3 whitespace-nowrap rounded-[0.625rem] px-3 font-display text-[0.9375rem] font-medium tracking-[0.01em] transition-colors duration-200 ease-out focus-visible:-outline-offset-2";
const idleRow = "text-text-secondary hover:bg-surface-elevated hover:text-text-primary";

type TooltipPosition = { top: number; left: number };

/**
 * Tooltip'i tetikleyicinin hover/odak olaylarına bağlar. Konum, `position: fixed` ile
 * kenar çubuğunun DIŞINDA (sağ kenarın yanında) hesaplanır; böylece kaydırılan nav alanı onu kırpmaz.
 * Escape tuşu tooltip'i kapatır (WCAG 1.4.13 "dismissible").
 */
function useRailTooltip() {
  const [position, setPosition] = useState<TooltipPosition | null>(null);
  const visible = position !== null;

  const show = useCallback((event: SyntheticEvent<HTMLElement>) => {
    const element = event.currentTarget;
    const rect = element.getBoundingClientRect();
    const panel = element.closest("[data-rail]")?.getBoundingClientRect();
    const center = rect.top + rect.height / 2;
    const top = Math.min(
      Math.max(center, TOOLTIP_EDGE),
      Math.max(window.innerHeight - TOOLTIP_EDGE, TOOLTIP_EDGE)
    );
    setPosition({ top, left: (panel?.right ?? rect.right) + TOOLTIP_GAP });
  }, []);

  const hide = useCallback(() => setPosition(null), []);

  useEffect(() => {
    if (!visible) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setPosition(null);
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [visible]);

  return {
    position,
    handlers: {
      onMouseEnter: show,
      onMouseLeave: hide,
      onFocus: show,
      onBlur: hide
    }
  };
}

/**
 * Kenar çubuğu dışına, `document.body` altına çizilen etiket (kırpılma ve z-index sorunu yok).
 * `always`: masaüstünde her zaman (Yakında öğeleri); aksi hâlde yalnız daraltılmış rayda.
 * Dar ekranda (drawer) hiç görünmez. Ad, tetikleyicinin erişilebilir adında zaten vardır.
 */
function RailTooltip({
  position,
  label,
  always
}: {
  position: TooltipPosition | null;
  label: string;
  always: boolean;
}) {
  if (!position) {
    return null;
  }
  return createPortal(
    <span
      aria-hidden="true"
      style={{ top: position.top, left: position.left }}
      className={cn(
        "pointer-events-none fixed z-[70] -translate-y-1/2 whitespace-nowrap rounded-lg border border-border bg-surface-elevated px-2.5 py-1.5 text-xs font-medium text-text-primary shadow-popover max-lg:hidden",
        !always && "hidden lg:in-data-[sidebar=collapsed]:block"
      )}
    >
      {label}
    </span>,
    document.body
  );
}

type NavItemProps = {
  item: NavItemModel;
  pathname: string;
  onNavigate: () => void;
};

/**
 * Tek menü öğesi. `href` varsa gerçek bağlantı (aktif sayfa `aria-current` ile işaretlenir),
 * yoksa tıklanamayan "Yakında" satırı. Öğeyi açmak için navigation.ts'e `href` yazmak yeterlidir.
 */
export function NavItem({ item, pathname, onNavigate }: NavItemProps) {
  const nav = useTranslations("Navigation");
  const label = nav(item.labelKey);
  const linkTooltip = useRailTooltip();
  const soonTooltip = useRailTooltip();

  if (!isNavItemAvailable(item)) {
    // Rota henüz yayınlanmadı: sahte sayfa yok. Odaklanabilir ama etkinleşmez; durum metinle de bildirilir.
    const soonLabel = nav("soonTooltip", { label });
    return (
      <li>
        <button
          type="button"
          aria-disabled="true"
          aria-label={soonLabel}
          {...soonTooltip.handlers}
          className={cn(
            row,
            collapsedCenter,
            "cursor-not-allowed text-text-secondary hover:bg-surface-elevated/60"
          )}
        >
          <Icon name={item.icon} className="shrink-0" />
          <span className={cn("min-w-0 flex-1 truncate text-start", collapsedHidden)}>
            {label}
          </span>
          <span
            className={cn(
              "shrink-0 rounded-full border border-border px-2 py-[0.1875rem] font-sans text-[0.6875rem] font-medium leading-none text-text-secondary",
              collapsedHidden
            )}
          >
            {nav("soon")}
          </span>
        </button>
        <RailTooltip position={soonTooltip.position} label={soonLabel} always />
      </li>
    );
  }

  const active = findActiveItem(pathname)?.id === item.id;

  return (
    <li>
      <Link
        href={item.href}
        aria-current={active ? "page" : undefined}
        onClick={onNavigate}
        {...linkTooltip.handlers}
        className={cn(
          row,
          collapsedCenter,
          active
            ? "bg-surface-elevated font-semibold text-text-primary"
            : idleRow
        )}
      >
        {active && (
          <span
            aria-hidden="true"
            className="absolute inset-y-2 start-0 w-[3px] rounded-e-full bg-primary"
          />
        )}
        <Icon
          name={item.icon}
          className={cn("shrink-0", active && "text-brand-text")}
        />
        <span className={cn("min-w-0 flex-1 truncate", collapsedHidden)}>{label}</span>
      </Link>
      <RailTooltip position={linkTooltip.position} label={label} always={false} />
    </li>
  );
}

type NavButtonProps = {
  icon: IconName;
  label: string;
  onClick: () => void;
  className?: string;
  "aria-expanded"?: boolean;
  "aria-controls"?: string;
};

/** Sayfaya götürmeyen, eylem başlatan kenar çubuğu satırı (daralt/genişlet). */
export function NavButton({
  icon,
  label,
  onClick,
  className,
  ...aria
}: NavButtonProps) {
  const { position, handlers } = useRailTooltip();

  return (
    <li className={className}>
      <button
        type="button"
        onClick={onClick}
        {...handlers}
        {...aria}
        className={cn(row, idleRow, collapsedCenter)}
      >
        <Icon name={icon} className="shrink-0" />
        <span className={cn("min-w-0 flex-1 truncate text-start", collapsedHidden)}>
          {label}
        </span>
      </button>
      <RailTooltip position={position} label={label} always={false} />
    </li>
  );
}
