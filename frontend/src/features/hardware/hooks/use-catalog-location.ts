"use client";
import { useSearchParams } from "next/navigation";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { readCatalogQuery } from "../schemas/hardware";

export function useCatalogLocation() {
  const search = useSearchParams();
  const router = useRouter();
  /** Dizi değer çoklu seçimdir: anahtar tekrarlanır (`socket=AM5&socket=AM4`). Boş değer/dizi parametreyi siler. */
  function update(values: Record<string, string | string[]>, reset = false) {
    const params = reset ? new URLSearchParams() : new URLSearchParams(search.toString());
    for (const [key, value] of Object.entries(values)) {
      params.delete(key);
      for (const item of Array.isArray(value) ? value : [value]) { if (item) params.append(key, item); }
    }
    const query: Record<string, string | string[]> = {};
    for (const key of new Set(params.keys())) { const all = params.getAll(key); query[key] = all.length > 1 ? all : all[0]; }
    router.push({ pathname: "/hardware", query }, { scroll: false });
  }
  return { search, query: readCatalogQuery(new URLSearchParams(search.toString())), update };
}
