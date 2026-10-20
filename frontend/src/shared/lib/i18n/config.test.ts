import { describe, expect, it } from "vitest";
import { defaultLocale, locales } from "./config";

describe("Dil yapilandirmasi", () => {
  it("varsayilan dil Turkcedir", () => {
    expect(defaultLocale).toBe("tr");
  });

  it("TR ve EN desteklenir", () => {
    expect(locales).toEqual(["tr", "en"]);
  });
});
