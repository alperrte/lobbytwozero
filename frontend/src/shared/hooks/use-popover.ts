"use client";

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent
} from "react";

/**
 * Navbar açılır panelleri (bildirim, dil, profil) için ortak davranış:
 * dışarı tıklama, Escape (odak tetikleyiciye döner) ve odak panel dışına çıkınca kapanma.
 * `rootRef` tetikleyici + paneli saran öğeye bağlanır.
 */
export function usePopover() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const close = useCallback((restoreFocus = false) => {
    setOpen(false);
    if (restoreFocus) {
      triggerRef.current?.focus();
    }
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }

    function onPointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    function onFocusIn(event: FocusEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("focusin", onFocusIn);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("focusin", onFocusIn);
    };
  }, [open]);

  return { open, setOpen, close, rootRef, triggerRef };
}

const MENU_ITEM_SELECTOR =
  '[role="menuitem"], [role="menuitemradio"], [role="menuitemcheckbox"]';

/** Menü içinde ok tuşları / Home / End ile öğeler arası gezinme (APG menu pattern). */
export function onMenuKeyDown(event: ReactKeyboardEvent<HTMLElement>) {
  const keys = ["ArrowDown", "ArrowUp", "Home", "End"];
  if (!keys.includes(event.key)) {
    return;
  }

  const items = Array.from(
    event.currentTarget.querySelectorAll<HTMLElement>(MENU_ITEM_SELECTOR)
  );
  if (items.length === 0) {
    return;
  }

  event.preventDefault();
  const current = items.indexOf(document.activeElement as HTMLElement);
  let next = current;
  if (event.key === "ArrowDown") {
    next = current + 1 >= items.length ? 0 : current + 1;
  } else if (event.key === "ArrowUp") {
    next = current <= 0 ? items.length - 1 : current - 1;
  } else if (event.key === "Home") {
    next = 0;
  } else {
    next = items.length - 1;
  }
  items[next]?.focus();
}

/** Menü açıldığında ilk (varsa seçili) öğeye odaklanır. */
export function focusFirstMenuItem(container: HTMLElement | null) {
  if (!container) {
    return;
  }
  const items = container.querySelectorAll<HTMLElement>(MENU_ITEM_SELECTOR);
  const checked = container.querySelector<HTMLElement>('[aria-checked="true"]');
  (checked ?? items[0])?.focus();
}
