"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { primaryNavigation } from "@/shared/config/navigation";
import { BrandLogo } from "./brand-logo";
import { NavItem } from "./nav-item";

export const SIDEBAR_ID = "app-sidebar";

type SidebarProps = {
  open: boolean;
  onClose: () => void;
  onNavigate: () => void;
  closeButtonRef: React.Ref<HTMLButtonElement>;
};

export function Sidebar({
  open,
  onClose,
  onNavigate,
  closeButtonRef
}: SidebarProps) {
  const t = useTranslations("Shell");
  const pathname = usePathname();

  return (
    <aside
      id={SIDEBAR_ID}
      data-open={open}
      className="invisible fixed inset-y-0 start-0 z-50 flex overscroll-contain w-72 max-w-[86vw] -translate-x-full flex-col border-e border-border bg-surface shadow-drawer transition-[translate,visibility] duration-200 ease-out delay-[0s,200ms] data-[open=true]:visible data-[open=true]:translate-x-0 data-[open=true]:transition-[translate] data-[open=true]:duration-200 lg:visible lg:sticky lg:top-0 lg:z-20 lg:h-dvh lg:w-72 lg:max-w-none lg:shrink-0 lg:translate-x-0 lg:shadow-none lg:transition-[width] lg:in-data-[sidebar=collapsed]:w-[4.5rem]"
    >
      <div className="flex h-header shrink-0 items-center justify-between border-b border-border px-4 lg:in-data-[sidebar=collapsed]:justify-center lg:in-data-[sidebar=collapsed]:px-0">
        <Link
          href="/"
          aria-label={t("brand")}
          onClick={onNavigate}
          className="flex items-center rounded-control"
        >
          <BrandLogo
            wordmarkClassName="h-auto w-28 lg:in-data-[sidebar=collapsed]:hidden"
            markClassName="hidden h-auto w-10 lg:in-data-[sidebar=collapsed]:block"
          />
        </Link>
        <button
          ref={closeButtonRef}
          type="button"
          onClick={onClose}
          aria-label={t("closeMenu")}
          className="inline-flex size-10 items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary lg:hidden"
        >
          <Icon name="close" />
        </button>
      </div>

      <nav
        aria-label={t("sidebarLabel")}
        className="flex-1 overflow-y-auto overscroll-contain px-3 py-4 lg:overflow-visible lg:in-data-[sidebar=collapsed]:px-2"
      >
        <ul className="space-y-1">
          {primaryNavigation.map((item) => (
            <NavItem
              key={item.id}
              item={item}
              pathname={pathname}
              onNavigate={onNavigate}
            />
          ))}
        </ul>
      </nav>
    </aside>
  );
}
