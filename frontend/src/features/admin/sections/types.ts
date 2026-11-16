import type { ComponentType } from "react";
import type { IconName } from "@/shared/components/ui/icons";

/** Bölümün panelden aldığı araçlar. */
export type SectionContext = {
  /** Başka bir bölümü açar; `params` hedef bölüme `params` olarak iletilir (ör. hesap kimliği). */
  open: (sectionId: string, params?: Record<string, string>) => void;
  /** Menüdeki rozeti ayarlar (ör. bekleyen talep sayısı); `null` ya da `0` rozeti kaldırır. */
  setBadge: (count: number | null) => void;
  /** Bu bölümü açan `open` çağrısının parametreleri; yoksa `null`. */
  params: Record<string, string> | null;
};

/** Yan menüdeki bir grup (servis başlığı). Yeni servis için `registry.ts`teki `ADMIN_GROUPS` listesine eklenir. */
export type AdminGroup = { id: string; label: string };

/**
 * Yönetim paneli bölümü. Her ekip kendi bölümünü kendi dosyasında tanımlar ve `registry.ts`e ekler; panelin iskeletine
 * dokunmaz. Yetki kararı HER ZAMAN ilgili servisin sunucusundadır; bölüm yalnız arayüzdür.
 */
export type AdminSection = {
  /** Benzersiz anahtar: `<grup>.<ad>` (ör. `auth.accounts`). */
  id: string;
  /** `ADMIN_GROUPS` içindeki bir grubun kimliği. */
  group: string;
  /** Menüde görünen ad. */
  label: string;
  icon: IconName;
  /** `true`: bölüm hiç açılmasa da panel açılırken yüklenir (rozet göstermesi gerekenler için). Varsayılan: ilk açılışta. */
  eager?: boolean;
  Component: ComponentType<SectionContext>;
};
