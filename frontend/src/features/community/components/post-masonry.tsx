"use client";

import { useRef, type ReactNode } from "react";
import { useColumnCount } from "../hooks/use-column-count";

/**
 * Doğal yükseklikli kart sütunları. Kartlar satır başına eşitlenmez: her sütun kendi kartlarını üst üste dizer, bu yüzden
 * yanındaki görselli kart yüzünden metin kartı uzamaz. Öğeler soldan sağa sırayla dağıtılır (1. kart 1. sütuna, 2. kart
 * 2. sütuna …); "daha fazla yükle" yalnız sütun sonlarına ekler, mevcut kartlar yer değiştirmez.
 */
export function PostMasonry<T extends { id: string }>({ items, render }: { items: readonly T[]; render: (item: T) => ReactNode }) {
  const rootRef = useRef<HTMLDivElement>(null);
  const count = useColumnCount(rootRef);
  const columns: T[][] = Array.from({ length: count }, () => []);
  items.forEach((item, index) => columns[index % count].push(item));

  return (
    <div ref={rootRef} className="flex items-start gap-4">
      {columns.map((column, index) => (
        <ul key={index} className="flex min-w-0 flex-1 flex-col gap-4">
          {column.map((item) => (
            <li key={item.id} className="min-w-0">
              {render(item)}
            </li>
          ))}
        </ul>
      ))}
    </div>
  );
}
