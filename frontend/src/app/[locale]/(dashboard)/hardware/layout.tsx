import type { ReactNode } from "react";
import { ComparisonProvider } from "@/features/hardware/components/comparison-provider";
import { AppShell } from "@/shared/components/layout/app-shell";
import { Footer } from "@/shared/components/layout/footer";

export default function HardwareLayout({ children }: { children: ReactNode }) {
  return <AppShell footer={<Footer />}><ComparisonProvider>{children}</ComparisonProvider></AppShell>;
}
