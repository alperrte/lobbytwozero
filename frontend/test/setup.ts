import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Vitest `globals` kapalı olduğundan RTL otomatik temizlik yapmaz.
afterEach(() => {
  cleanup();
});

// jsdom <dialog>.showModal()/close() uygulamıyor; yeterli davranışı taklit eder:
// `open` niteliğini yönetir ve kapanışta tarayıcı gibi `close` olayını gönderir.
if (typeof HTMLDialogElement !== "undefined") {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function showModal() {
      this.setAttribute("open", "");
    };
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function close() {
      if (this.hasAttribute("open")) {
        this.removeAttribute("open");
        this.dispatchEvent(new Event("close"));
      }
    };
  }
}
