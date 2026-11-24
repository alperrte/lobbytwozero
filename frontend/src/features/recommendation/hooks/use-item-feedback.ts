"use client";

import { useEffect, useRef, useState } from "react";
import { recommendationApi } from "../services/recommendation-api";
import { errorCode, isAbortError, isConsentRequired } from "../services/recommendation-errors";
import type { FeedbackValue, ItemFeedback } from "../types/recommendation";
import { useRecommendationScope } from "./recommendation-scope";

export type FeedbackEntry = { value: FeedbackValue | null; version: number; known: boolean };
export type FeedbackProblem = { itemId: string; kind: "conflict" | "failed"; error: unknown };

const READ_CONCURRENCY = 3;
const EMPTY: FeedbackEntry = { value: null, version: 0, known: false };

/** Görünen öğelerin geri bildirimini en fazla 3 eşzamanlı istekle okur (toplu okuma ucu yok; read kotası korunur). */
async function readAll(runId: string, itemIds: readonly string[], signal: AbortSignal, onRead: (feedback: ItemFeedback) => void) {
  const queue = [...itemIds];
  async function worker() {
    for (let itemId = queue.shift(); itemId !== undefined && !signal.aborted; itemId = queue.shift()) {
      try {
        onRead(await recommendationApi.getFeedback(runId, itemId, signal));
      } catch (error) {
        if (isAbortError(error) || signal.aborted) return;
        if (isConsentRequired(error)) throw error;
        // Tek öğenin okunamaması diğerlerini durdurmaz; o öğe "bilinmiyor" kalır (gönderimde sürüm 0 denenir).
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(READ_CONCURRENCY, queue.length) }, worker));
}

/**
 * Beğen / İlgilenmiyorum. İyimser güncelleme: seçim anında görünür, hata olursa önceki değere döner.
 * Sürüm çatışmasında (409 `FEEDBACK_VERSION_CONFLICT`) sunucudaki güncel değer okunur ve kullanıcıya bildirilir.
 * Aynı öğe için bekleyen istek varken ikinci tıklama yok sayılır. `fresh`: yeni üretilmiş çalışma (henüz geri bildirim yok,
 * sürüm 0 bilinir; okuma isteği atılmaz).
 */
export function useItemFeedback(runId: string, itemIds: readonly string[], fresh = false) {
  const { consentRequired } = useRecommendationScope();
  const [entries, setEntries] = useState<{ runId: string; map: Record<string, FeedbackEntry> }>({ runId, map: {} });
  const [pending, setPending] = useState<Record<string, boolean>>({});
  const [problem, setProblem] = useState<FeedbackProblem>();
  const locks = useRef(new Set<string>());
  const consentRef = useRef(consentRequired);
  useEffect(() => {
    consentRef.current = consentRequired;
  });

  const map = entries.runId === runId ? entries.map : {};
  const idsKey = itemIds.join(",");

  useEffect(() => {
    if (fresh || !idsKey) return;
    const controller = new AbortController();
    readAll(runId, idsKey.split(","), controller.signal, (feedback) => {
      setEntries((current) => {
        const base = current.runId === runId ? current.map : {};
        if (base[feedback.itemId]?.known) return current;
        return { runId, map: { ...base, [feedback.itemId]: { value: feedback.value, version: feedback.version, known: true } } };
      });
    }).catch((error: unknown) => {
      if (isConsentRequired(error)) consentRef.current();
    });
    return () => controller.abort();
  }, [runId, idsKey, fresh]);

  function write(itemId: string, entry: FeedbackEntry) {
    setEntries((current) => ({ runId, map: { ...(current.runId === runId ? current.map : {}), [itemId]: entry } }));
  }

  function entryOf(itemId: string): FeedbackEntry {
    return map[itemId] ?? (fresh ? { value: null, version: 0, known: true } : EMPTY);
  }

  async function submit(itemId: string, value: FeedbackValue) {
    const previous = entryOf(itemId);
    if (locks.current.has(itemId) || previous.value === value) return;
    locks.current.add(itemId);
    setPending((current) => ({ ...current, [itemId]: true }));
    setProblem(undefined);
    write(itemId, { ...previous, value });
    try {
      const saved = await recommendationApi.putFeedback(runId, itemId, value, previous.version);
      write(itemId, { value: saved.value, version: saved.version, known: true });
    } catch (error) {
      if (isConsentRequired(error)) {
        consentRef.current();
        return;
      }
      if (errorCode(error) === "FEEDBACK_VERSION_CONFLICT") {
        try {
          const latest = await recommendationApi.getFeedback(runId, itemId);
          write(itemId, { value: latest.value, version: latest.version, known: true });
        } catch {
          write(itemId, previous);
        }
        setProblem({ itemId, kind: "conflict", error });
      } else {
        write(itemId, previous);
        setProblem({ itemId, kind: "failed", error });
      }
    } finally {
      locks.current.delete(itemId);
      setPending((current) => ({ ...current, [itemId]: false }));
    }
  }

  return { entryOf, pending, problem, submit, dismissProblem: () => setProblem(undefined) };
}
