import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { SessionValue } from "./use-session";
import { SessionContext } from "./use-session";
import { useRequireSession } from "./use-require-session";

const replace = vi.fn();
let pathname = "/tr/ayarlar";

vi.mock("next/navigation", () => ({ usePathname: () => pathname }));
vi.mock("@/shared/lib/i18n/navigation", () => ({ useRouter: () => ({ replace }) }));

function Probe() {
  const session = useRequireSession();
  return <p data-testid="status">{session.status}</p>;
}

function renderWith(status: SessionValue["status"]) {
  const value = (
    status === "authenticated"
      ? { status, me: { accountId: "a1", roles: ["PLAYER"], authMethods: ["pwd"], accessTokenExpiresAt: "x" } }
      : { status, me: null }
  ) as SessionValue;
  return render(
    <SessionContext.Provider value={{ ...value, logout: vi.fn() }}>
      <Probe />
    </SessionContext.Provider>
  );
}

beforeEach(() => {
  replace.mockReset();
  pathname = "/tr/ayarlar";
});

describe("useRequireSession", () => {
  it("misafiri bulunduğu sayfayı next olarak taşıyıp girişe yönlendirir", () => {
    renderWith("guest");
    expect(replace).toHaveBeenCalledWith({ pathname: "/login", query: { next: "/tr/ayarlar" } });
  });

  it("güvenli olmayan yolda next eklemeden girişe yönlendirir", () => {
    pathname = "//evil.example";
    renderWith("guest");
    expect(replace).toHaveBeenCalledWith("/login");
  });

  it("oturum açıkken ve yüklenirken yönlendirmez; oturum değerini döndürür", () => {
    renderWith("authenticated");
    expect(screen.getByTestId("status").textContent).toBe("authenticated");
    expect(replace).not.toHaveBeenCalled();

    renderWith("loading");
    expect(replace).not.toHaveBeenCalled();
  });
});
