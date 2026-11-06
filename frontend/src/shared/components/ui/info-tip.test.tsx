import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { InfoTip } from "./info-tip";

function renderTip() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <InfoTip subject="Tema">Temanın açık mı koyu mu olacağını seçer.</InfoTip>
    </NextIntlClientProvider>
  );
}

describe("InfoTip", () => {
  it("erişilebilir ad taşır ve başlangıçta açıklama göstermez", () => {
    renderTip();
    expect(screen.getByRole("button", { name: "Tema hakkında bilgi" })).toBeInTheDocument();
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("odaklanınca açıklamayı gösterir ve aria-describedby ile bağlar", () => {
    renderTip();
    const button = screen.getByRole("button", { name: "Tema hakkında bilgi" });
    fireEvent.focus(button);
    const tip = screen.getByRole("tooltip");
    expect(tip).toHaveTextContent("Temanın açık mı koyu mu olacağını seçer.");
    expect(button).toHaveAttribute("aria-describedby", tip.id);
  });

  it("Escape ve fare ayrılması açıklamayı kapatır", () => {
    renderTip();
    const button = screen.getByRole("button", { name: "Tema hakkında bilgi" });
    fireEvent.focus(button);
    act(() => {
      fireEvent.keyDown(document, { key: "Escape" });
    });
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();

    fireEvent.mouseEnter(button);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();
    fireEvent.mouseLeave(button);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });
});
