"use client";
import { useSearchParams } from "next/navigation";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { readCatalogQuery } from "../schemas/hardware";

export function useCatalogLocation() {
  const search = useSearchParams();
  const router = useRouter();
  function update(values: Record<string, string>, reset = false) {
    const params = reset ? new URLSearchParams() : new URLSearchParams(search.toString());
    for (const [key, value] of Object.entries(values)) { if (value) params.set(key, value); else params.delete(key); }
    router.push({ pathname: "/hardware", query: Object.fromEntries(params) }, { scroll: false });
  }
  return { search, query: readCatalogQuery(new URLSearchParams(search.toString())), update };
}
