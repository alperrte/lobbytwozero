import type { ReactNode } from "react";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";

export default function PerformanceDashboardLayout({ children }: { children: ReactNode }) {
  return <AppShell footer={<Footer />}>{children}</AppShell>;
}
