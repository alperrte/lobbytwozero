import { afterEach, describe, expect, it } from "vitest";
import { hasSessionHint } from "./session-hint";

const clear = () => {
  document.cookie = "ltz_session=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  document.cookie = "baska=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  document.cookie = "ltz_session_x=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
};

afterEach(clear);

describe("hasSessionHint", () => {
  it("çerez yokken false", () => {
    expect(hasSessionHint()).toBe(false);
  });

  it("değerli ipucu çerezi varken true", () => {
    document.cookie = "ltz_session=1; path=/";
    expect(hasSessionHint()).toBe(true);
  });

  it("boş değerli ipucu çerezi (silinmiş) true saymaz", () => {
    document.cookie = "ltz_session=; path=/";
    expect(hasSessionHint()).toBe(false);
  });

  it("başka adlı çerezler ipucu sayılmaz", () => {
    document.cookie = "baska=1; path=/";
    document.cookie = "ltz_session_x=1; path=/";
    expect(hasSessionHint()).toBe(false);
  });
});
