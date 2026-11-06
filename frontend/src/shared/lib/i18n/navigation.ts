import { createNavigation } from "next-intl/navigation";
import { routing } from "./routing";

/**
 * Dile duyarlı gezinme yardımcıları. `next/link` ve `next/navigation` yerine bunlar kullanılır:
 * dahili yolu (`/about`) geçerli dilin adresine (`/tr/hakkimizda`) çevirir, `usePathname` dil önekini
 * ve slug çevirisini geri alıp dahili yolu döndürür, `router.replace(path, { locale })` eşdeğer sayfaya geçirir.
 */
export const { Link, redirect, usePathname, useRouter, getPathname } =
  createNavigation(routing);
