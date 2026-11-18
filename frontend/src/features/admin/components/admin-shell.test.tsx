import { act, fireEvent, render, screen } from "@testing-library/react";
import { useEffect } from "react";
import { describe, expect, it, vi } from "vitest";
import type { AdminGroup, AdminSection, SectionContext } from "../sections/types";
import { AdminShell } from "./admin-shell";

const mounts: string[] = [];

function Probe({ name, ctx, badge, jump }: { name: string; ctx: SectionContext; badge?: number; jump?: string }) {
  const { setBadge } = ctx;
  useEffect(() => {
    mounts.push(name);
  }, [name]);
  useEffect(() => {
    if (badge !== undefined) {
      setBadge(badge);
    }
  }, [badge, setBadge]);
  return (
    <div>
      <p>{`${name} içerik`}</p>
      <p data-testid={`${name}-params`}>{JSON.stringify(ctx.params)}</p>
      {jump && <button type="button" onClick={() => ctx.open(jump, { accountId: "abc" })}>{`${name} git`}</button>}
    </div>
  );
}

const groups: AdminGroup[] = [
  { id: "a", label: "Alfa" },
  { id: "b", label: "Beta" },
  { id: "c", label: "Boş grup" }
];

const sections: AdminSection[] = [
  { id: "a.one", group: "a", label: "Bir", icon: "users", Component: (ctx) => <Probe name="bir" ctx={ctx} jump="b.two" /> },
  { id: "b.two", group: "b", label: "İki", icon: "ban", Component: (ctx) => <Probe name="iki" ctx={ctx} /> },
  { id: "b.eager", group: "b", label: "Rozetli", icon: "ban", eager: true, Component: (ctx) => <Probe name="rozetli" ctx={ctx} badge={3} /> }
];

function setup() {
  mounts.length = 0;
  const onSignOut = vi.fn();
  render(<AdminShell onSignOut={onSignOut} sections={sections} groups={groups} />);
  return { onSignOut };
}

describe("AdminShell", () => {
  it("ilk bölümü açar, açılmamış bölümü yüklemez, eager olanı yükler", () => {
    setup();
    expect(screen.getByRole("heading", { level: 1, name: "Bir" })).toBeInTheDocument();
    expect(mounts).toContain("bir");
    expect(mounts).toContain("rozetli");
    expect(mounts).not.toContain("iki");
  });

  it("gruplar başlık altında listelenir, boş grup bilgi verir", () => {
    setup();
    expect(screen.getByRole("group", { name: "Alfa" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Boş grup" })).toHaveTextContent("Henüz bölüm yok");
  });

  it("menüden bölüm değişir; önceki bölüm kapanmaz yalnız gizlenir", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: /^İki/ }));
    expect(screen.getByRole("heading", { level: 1, name: "İki" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^İki/ })).toHaveAttribute("aria-current", "page");
    expect(screen.getByText("iki içerik")).toBeVisible();
    expect(screen.getByText("bir içerik").closest("section")).toHaveAttribute("hidden");
    expect(mounts.filter((name) => name === "bir")).toHaveLength(1);
  });

  it("rozet sayısı menüde görünür", () => {
    setup();
    expect(screen.getByLabelText("3 bekleyen")).toHaveTextContent("3");
  });

  it("bölüm başka bölümü parametreyle açabilir", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: "bir git" }));
    expect(screen.getByRole("heading", { level: 1, name: "İki" })).toBeInTheDocument();
    expect(screen.getByTestId("iki-params")).toHaveTextContent('{"accountId":"abc"}');
  });

  it("çıkış önce onay ister; Vazgeç çıkarmaz, Evet, çık onSignOut çağırır", () => {
    const { onSignOut } = setup();
    fireEvent.click(screen.getByRole("button", { name: "Çıkış" }));
    expect(screen.getByRole("dialog", { name: "Yönetim panelinden çıkılsın mı?" })).toBeInTheDocument();
    expect(onSignOut).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(onSignOut).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Çıkış" }));
    fireEvent.click(screen.getByRole("button", { name: "Evet, çık" }));
    expect(onSignOut).toHaveBeenCalledTimes(1);
  });

  it("dar ekran menüsü açılır ve Escape ile kapanır", () => {
    setup();
    const toggle = screen.getByRole("button", { name: "Menüyü aç" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(toggle);
    expect(screen.getByRole("button", { name: "Menüyü kapat", expanded: true })).toBeInTheDocument();
    act(() => {
      fireEvent.keyDown(document, { key: "Escape" });
    });
    expect(screen.getByRole("button", { name: "Menüyü aç" })).toHaveAttribute("aria-expanded", "false");
  });

  it("bölüm yoksa bilgi mesajı gösterir", () => {
    render(<AdminShell onSignOut={() => undefined} sections={[]} groups={groups} />);
    expect(screen.getByRole("status")).toHaveTextContent("Henüz yönetim bölümü yok.");
  });
});
