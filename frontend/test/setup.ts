import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Vitest `globals` kapalı olduğundan RTL otomatik temizlik yapmaz.
afterEach(() => {
  cleanup();
});
