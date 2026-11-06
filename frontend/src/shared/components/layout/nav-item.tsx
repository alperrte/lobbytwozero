"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import {
  isActivePath,
  type NavItem as NavItemModel
} from "@/shared/config/navigation";
import { cn } from "@/shared/utils/cn";

const collapsedCenter =
  "lg:in-data-[sidebar=collapsed]:justify-center lg:in-data-[sidebar=collapsed]:px-0";
const collapsedHidden = "lg:in-data-[sidebar=collapsed]:sr-only";
const row =
  "group relative flex h-11 items-center gap-3 rounded-control px-3 font-display text-[0.9375rem] font-medium tracking-[0.01em]";

type NavItemProps = {
  item: NavItemModel;
  pathname: string;
  onNavigate: () => void;
};

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

  if (item.children) {
    return (
      <li>
        <div className={cn(row, "text-text-primary", collapsedCenter)}>
          <Icon name={item.icon} className="shrink-0 text-text-secondary" />
          <span className={collapsedHidden}>{label}</span>
        </div>
        <ul className="ms-4 mt-1 space-y-1 border-s border-border ps-2 lg:in-data-[sidebar=collapsed]:hidden">
          {item.children.map((child) => (
            <NavItem
              key={child.id}
              item={child}
              pathname={pathname}
              onNavigate={onNavigate}
            />
          ))}
        </ul>
      </li>
    );
  }

  if (!item.href) {
    // Rota henüz yayınlanmadı: tıklanamaz, dürüstçe "Yakında" olarak gösterilir.
    return (
      <li>
        <div
          className={cn(
            row,
            "cursor-default text-text-secondary",
            collapsedCenter
          )}
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
          "transition-colors",
          collapsedCenter,
          active
            ? "nav-indicator bg-surface-elevated text-text-primary"
            : "text-text-secondary hover:bg-surface-elevated hover:text-text-primary"
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
