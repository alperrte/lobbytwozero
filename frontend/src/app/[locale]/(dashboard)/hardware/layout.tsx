import type { ReactNode } from "react";
import { ComparisonProvider } from "@/features/hardware/components/comparison-provider";

/** Kabuk `(dashboard)` grubundan gelir; bu bölüm yalnız karşılaştırma bağlamını ekler. */
export default function HardwareLayout({ children }: { children: ReactNode }) {
  return <ComparisonProvider>{children}</ComparisonProvider>;
}
