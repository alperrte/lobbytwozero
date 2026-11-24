"use client";

import { useEffect, useState } from "react";
import { enrichmentApi } from "../services/enrichment-api";
import type { CatalogGame } from "../types/recommendation";

const DEBOUNCE_MS = 300;
const MIN_LENGTH = 2;

type SearchState = { query: string; items: CatalogGame[]; failed: boolean };

/**
 * Oyun kataloğunda ada göre arama (`GET /game/catalog?q`). Yazım bitince (300 ms) tek istek atılır, önceki istek iptal edilir.
 * 2 karakterden kısa sorguda istek atılmaz. Sonuç yalnız bellekte tutulur; sorgu URL'ye veya tarayıcı deposuna yazılmaz.
 */
export function useCatalogSearch(query: string) {
  const trimmed = query.trim();
  const [state, setState] = useState<SearchState>({ query: "", items: [], failed: false });
  useEffect(() => {
    if (trimmed.length < MIN_LENGTH) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      enrichmentApi.searchCatalog(trimmed, controller.signal).then(
        (items) => setState({ query: trimmed, items, failed: false }),
        (error: unknown) => {
          if (!controller.signal.aborted && !(error instanceof DOMException && error.name === "AbortError")) setState({ query: trimmed, items: [], failed: true });
        }
      );
    }, DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [trimmed]);

  const active = trimmed.length >= MIN_LENGTH;
  const settled = active && state.query === trimmed;
  return { items: settled ? state.items : [], failed: settled && state.failed, loading: active && !settled, tooShort: !active };
}
