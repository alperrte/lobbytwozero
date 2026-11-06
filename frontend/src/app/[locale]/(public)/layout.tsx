import type { ReactNode } from "react";
import { SessionProvider } from "@/features/auth/components/session-provider";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";

export default function PublicLayout({
  children
}: Readonly<{ children: ReactNode }>) {
  return (
    <SessionProvider>
      <AppShell footer={<Footer />}>{children}</AppShell>
    </SessionProvider>
  );
}
