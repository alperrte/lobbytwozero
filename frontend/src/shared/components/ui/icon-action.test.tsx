import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { IconAction } from "./icon-action";

describe("IconAction", () => {
  it("href varsa yeni sekmede güvenli açılan bağlantı olur", () => {
    render(
      <IconAction label="Ada — GitHub" tooltip="Ada — GitHub" href="https://github.com/ada">
        <svg />
      </IconAction>
    );

    const link = screen.getByRole("link", { name: "Ada — GitHub" });
    expect(link).toHaveAttribute("href", "https://github.com/ada");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel")).toContain("noopener");
    expect(link.getAttribute("rel")).toContain("noreferrer");
  });

  it("href yoksa pasif düğme olur: bağlantı üretmez, aria-disabled taşır", () => {
    render(
      <IconAction label="Ada — CV (henüz eklenmedi)" tooltip="CV henüz eklenmedi" href={null}>
        <svg />
      </IconAction>
    );

    expect(screen.queryByRole("link")).toBeNull();
    const button = screen.getByRole("button", { name: "Ada — CV (henüz eklenmedi)" });
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(button).not.toHaveAttribute("href");
  });

  it("odaklanınca tooltip açılır, aria-describedby bağlanır ve Escape ile kapanır", () => {
    render(
      <IconAction label="Ada — CV (henüz eklenmedi)" tooltip="CV henüz eklenmedi" href={null}>
        <svg />
      </IconAction>
    );
    const button = screen.getByRole("button");

    expect(screen.queryByRole("tooltip")).toBeNull();
    fireEvent.focus(button);
    const tip = screen.getByRole("tooltip");
    expect(tip).toHaveTextContent("CV henüz eklenmedi");
    expect(button).toHaveAttribute("aria-describedby", tip.id);

    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    });
    expect(screen.queryByRole("tooltip")).toBeNull();
    expect(button).not.toHaveAttribute("aria-describedby");
  });

  it("fareyle gelince açılır, çıkınca kapanır", () => {
    render(
      <IconAction label="Ada — GitHub" tooltip="Ada — GitHub" href="https://github.com/ada">
        <svg />
      </IconAction>
    );
    const link = screen.getByRole("link");

    fireEvent.mouseEnter(link);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();
    fireEvent.mouseLeave(link);
    expect(screen.queryByRole("tooltip")).toBeNull();
  });
});
