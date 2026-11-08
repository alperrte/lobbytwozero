import { describe, expect, it } from "vitest";
import { exitFor, screenForPath, transitionBetween, twistAngle, welcomeVariantFor } from "./auth-motion";

describe("auth-motion", () => {
  it("yolları ekrana çevirir", () => {
    expect(screenForPath("/login")).toBe("login");
    expect(screenForPath("/register")).toBe("register");
    expect(screenForPath("/forgot-password")).toBe("forgot");
    expect(screenForPath("/auth/callback")).toBe("other");
  });

  it("karşılama metni: kayıt akışı kayıt metnini, diğerleri giriş metnini kullanır", () => {
    expect(welcomeVariantFor("/register")).toBe("register");
    expect(welcomeVariantFor("/verify-email")).toBe("register");
    expect(welcomeVariantFor("/login")).toBe("login");
    expect(welcomeVariantFor("/forgot-password")).toBe("login");
  });

  it("ilk açılışta ve aynı yolda hareket yok", () => {
    expect(transitionBetween(null, "/login")).toBe("none");
    expect(transitionBetween("/login", "/login")).toBe("none");
  });

  it("giriş → kayıt sağdan, kayıt → giriş soldan gelir", () => {
    expect(transitionBetween("/login", "/register")).toBe("from-right");
    expect(transitionBetween("/register", "/login")).toBe("from-left");
    expect(transitionBetween("/register", "/verify-email")).toBe("from-right");
  });

  it("şifremi unuttum için sakin hareketler kullanılır", () => {
    expect(transitionBetween("/login", "/forgot-password")).toBe("unfold");
    expect(transitionBetween("/forgot-password", "/login")).toBe("settle");
  });

  it("diğer geçişler kısa solmadır", () => {
    expect(transitionBetween("/login", "/verify-email")).toBe("fade");
    expect(transitionBetween("/auth/callback", "/login")).toBe("fade");
  });

  it("harf açıları belirleyicidir ve küçük bir aralıkta kalır", () => {
    for (let index = 0; index < 40; index++) {
      expect(twistAngle(index)).toBe(twistAngle(index));
      expect(Math.abs(twistAngle(index))).toBeLessThanOrEqual(16);
    }
  });

  it("çıkış hareketi giriş hareketinin karşılığıdır", () => {
    expect(exitFor("from-right")).toBe("left");
    expect(exitFor("from-left")).toBe("right");
    expect(exitFor("unfold")).toBe("down");
    expect(exitFor("settle")).toBe("fold");
    expect(exitFor("fade")).toBe("fade");
    expect(exitFor("none")).toBe("fade");
  });
});
