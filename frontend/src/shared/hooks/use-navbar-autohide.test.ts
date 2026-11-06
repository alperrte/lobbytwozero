import { describe, expect, it } from "vitest";
import {
  AUTOHIDE_DELTA,
  AUTOHIDE_MIN_OFFSET,
  decideNavbarVisibility
} from "./use-navbar-autohide";

const visible = (anchor: number) => ({ hidden: false, anchor });
const hidden = (anchor: number) => ({ hidden: true, anchor });

describe("decideNavbarVisibility", () => {
  it("sayfa başındayken her zaman görünür", () => {
    expect(decideNavbarVisibility(hidden(400), 0).hidden).toBe(false);
    expect(decideNavbarVisibility(hidden(400), 10).hidden).toBe(false);
  });

  it("minimum ofsetin altında aşağı kaydırma gizlemez", () => {
    const next = decideNavbarVisibility(visible(30), AUTOHIDE_MIN_OFFSET - 1);
    expect(next.hidden).toBe(false);
  });

  it("eşiği aşan aşağı kaydırmada gizlenir", () => {
    const next = decideNavbarVisibility(visible(200), 200 + AUTOHIDE_DELTA);
    expect(next.hidden).toBe(true);
  });

  it("küçük titreşimler durumu değiştirmez", () => {
    expect(decideNavbarVisibility(visible(300), 300 + AUTOHIDE_DELTA - 1).hidden).toBe(
      false
    );
    expect(decideNavbarVisibility(hidden(300), 300 - AUTOHIDE_DELTA + 1).hidden).toBe(
      true
    );
  });

  it("eşiği aşan yukarı kaydırmada geri gelir", () => {
    const next = decideNavbarVisibility(hidden(500), 500 - AUTOHIDE_DELTA);
    expect(next.hidden).toBe(false);
  });

  it("gizliyken aşağı inerken anchor izlenir; dönüş eşiği o noktadan ölçülür", () => {
    const down = decideNavbarVisibility(hidden(300), 600);
    expect(down).toEqual({ hidden: true, anchor: 600 });
    expect(decideNavbarVisibility(down, 600 - AUTOHIDE_DELTA + 1).hidden).toBe(true);
    expect(decideNavbarVisibility(down, 600 - AUTOHIDE_DELTA).hidden).toBe(false);
  });
});
