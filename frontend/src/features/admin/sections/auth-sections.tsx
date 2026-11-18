"use client";

import { AccountsPanel } from "../components/accounts-panel";
import { DeletionRequestsPanel } from "../components/deletion-requests-panel";
import type { AdminSection, SectionContext } from "./types";

function DeletionRequestsSection({ open, setBadge }: SectionContext) {
  return (
    <DeletionRequestsPanel
      onCount={setBadge}
      onOpenAccount={(accountId) => open("auth.accounts", { accountId })}
    />
  );
}

function AccountsSection({ params }: SectionContext) {
  const accountId = params?.accountId ?? null;
  return <AccountsPanel key={accountId ?? "none"} initialAccountId={accountId} />;
}

/** Auth servisi bölümleri (Auth'un `/api/v1/auth/admin/**` uçları). */
export const authSections: AdminSection[] = [
  { id: "auth.deletion-requests", group: "auth", label: "Silme talepleri", icon: "ban", eager: true, Component: DeletionRequestsSection },
  { id: "auth.accounts", group: "auth", label: "Hesaplar", icon: "users", Component: AccountsSection }
];
