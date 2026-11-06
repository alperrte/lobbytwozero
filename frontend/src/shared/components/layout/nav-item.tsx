"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Icon, type IconName } from "@/shared/components/ui/icons";
import {
  isActivePath,
  type NavItem as NavItemModel
} from "@/shared/config/navigation";
import { cn } from "@/shared/utils/cn";

const collapsedCenter =
  "lg:in-data-[sidebar=collapsed]:justify-center lg:in-data-[sidebar=collapsed]:px-0";
const collapsedHidden = "lg:in-data-[sidebar=collapsed]:sr-only";
const row =
  "group relative flex h-11 w-full items-center gap-3 rounded-control px-3 font-display text-[0.9375rem] font-medium tracking-[0.01em]";
const idleRow =
  "text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary";

type NavItemProps = {
  item: NavItemModel;
  pathname: string;
  onNavigate: () => void;
};

/** Daraltılmış (yalnız ikon) kenar çubuğunda hover/odakta görünen ad etiketi. */
function Tooltip({ label }: { label: string }) {
  return (
    <span
      aria-hidden="true"
      className="pointer-events-none absolute start-full z-10 ms-3 hidden whitespace-nowrap rounded-control border border-border bg-surface-elevated px-2.5 py-1.5 text-xs font-medium text-text-primary shadow-drawer lg:in-data-[sidebar=collapsed]:group-hover:block lg:in-data-[sidebar=collapsed]:group-focus-visible:block"
    >
      {label}
    </span>
  );
}

export function NavItem({ item, pathname, onNavigate }: NavItemProps) {
  const nav = useTranslations("Navigation");
  const label = nav(item.labelKey);

  if (!item.href) {
    // Rota henüz yayınlanmadı: tıklanamaz, dürüstçe "Yakında" olarak gösterilir.
    return (
      <li>
        <div
          className={cn(row, "cursor-default text-text-secondary", collapsedCenter)}
        >
          <Icon name={item.icon} className="shrink-0" />
          <span className={cn("min-w-0 truncate", collapsedHidden)}>{label}</span>
          <span
            className={cn(
              "ms-auto shrink-0 rounded-control border border-border px-1.5 py-0.5 font-sans text-xs font-medium leading-none text-text-secondary",
              collapsedHidden
            )}
          >
            {nav("soon")}
          </span>
          <Tooltip label={`${label} · ${nav("soon")}`} />
        </div>
      </li>
    );
  }

  const active = isActivePath(pathname, item.href);

  return (
    <li>
      <Link
        href={item.href}
        aria-current={active ? "page" : undefined}
        onClick={onNavigate}
        className={cn(
          row,
          collapsedCenter,
          active ? "nav-indicator bg-surface-elevated text-text-primary" : idleRow
        )}
      >
        <Icon
          name={item.icon}
          className={cn("shrink-0", active && "text-brand-text")}
        />
        <span className={collapsedHidden}>{label}</span>
        <Tooltip label={label} />
      </Link>
    </li>
  );
}

type NavButtonProps = {
  icon: IconName;
  label: string;
  onClick: () => void;
  /** Daraltılmış durumda görünen ad (varsayılan: label). */
  tooltip?: string;
  className?: string;
  "aria-haspopup"?: "dialog";
  "aria-expanded"?: boolean;
  "aria-controls"?: string;
};

/** Sayfaya götürmeyen, eylem başlatan kenar çubuğu satırı (Ayarlar, daralt/genişlet). */
export function NavButton({
  icon,
  label,
  onClick,
  tooltip,
  className,
  ...aria
}: NavButtonProps) {
  return (
    <li className={className}>
      <button
        type="button"
        onClick={onClick}
        className={cn(row, idleRow, collapsedCenter)}
        {...aria}
      >
        <Icon name={icon} className="shrink-0" />
        <span className={cn("min-w-0 truncate text-start", collapsedHidden)}>
          {label}
        </span>
        <Tooltip label={tooltip ?? label} />
      </button>
    </li>
  );
}
