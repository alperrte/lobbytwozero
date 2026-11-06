import { notFound } from "next/navigation";

/**
 * Tanımsız her `/[locale]/...` adresini locale layout'u içindeki `not-found.tsx` sayfasına düşürür
 * (kök layout `[locale]` altında olduğundan genel bir `app/not-found.tsx` kullanılamaz).
 */
export default function CatchAllPage() {
  notFound();
}
