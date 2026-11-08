import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { GameCombobox, type ComboOption } from "./game-combobox";

const options: ComboOption[] = [{ value: "tr-tr", label: "Türkçe (Türkiye)" }, { value: "en-us", label: "İngilizce (ABD)" }, { value: "en-gb", label: "İngilizce (Birleşik Krallık)" }];
function Harness({ items = options, initial = "", onChange }: { items?: ComboOption[]; initial?: string; onChange?: (value: string) => void }) {
  const [value, setValue] = useState(initial);
  return <NextIntlClientProvider locale="tr" messages={messages}>
    <label htmlFor="c">Dil</label>
    <GameCombobox id="c" value={value} onValueChange={(next) => { setValue(next); onChange?.(next); }} options={items} />
    <button type="button" onClick={() => setValue("")}>temizle</button>
  </NextIntlClientProvider>;
}
const input = () => screen.getByLabelText("Dil") as HTMLInputElement;
afterEach(cleanup);

describe("Otomatik tamamlamalı açılır liste", () => {
  it("seçenek yokken düz metin girişidir: combobox rolü, liste ve ok düğmesi yoktur; metin olduğu gibi bildirilir", () => {
    const onChange = vi.fn();
    render(<Harness items={[]} onChange={onChange} />);
    expect(screen.queryByRole("combobox")).toBeNull();
    expect(screen.queryByRole("button", { name: messages.Game.comboboxShowOptions })).toBeNull();
    fireEvent.change(input(), { target: { value: "rpg" } });
    expect(onChange).toHaveBeenLastCalledWith("rpg");
    expect(screen.queryByRole("listbox")).toBeNull();
  });
  it("odaklanınca tüm seçenekleri açar; yazdıkça etiket ve koda göre süzer (Türkçe karakterden bağımsız)", () => {
    render(<Harness />);
    fireEvent.focus(input());
    expect(screen.getAllByRole("option")).toHaveLength(3);
    fireEvent.change(input(), { target: { value: "ingiliz" } });
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual(["İngilizce (ABD)en-us", "İngilizce (Birleşik Krallık)en-gb"]);
    fireEvent.change(input(), { target: { value: "tr-t" } });
    expect(screen.getAllByRole("option")).toHaveLength(1);
  });
  it("tıklayınca kodu bildirir ve etiketi gösterir; liste kapanır", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    fireEvent.focus(input());
    fireEvent.click(screen.getByRole("option", { name: /Türkçe/ }));
    expect(onChange).toHaveBeenLastCalledWith("tr-tr");
    expect(input()).toHaveValue("Türkçe (Türkiye)");
    expect(screen.queryByRole("listbox")).toBeNull();
  });
  it("klavye: ArrowDown listeyi açar/ilerler, Enter seçer, Esc kapatır", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    input().focus();
    fireEvent.keyDown(input(), { key: "Escape" });
    fireEvent.keyDown(input(), { key: "ArrowDown" });
    fireEvent.keyDown(input(), { key: "ArrowDown" });
    expect(input()).toHaveAttribute("aria-activedescendant", expect.stringMatching(/-1$/));
    fireEvent.keyDown(input(), { key: "Enter" });
    expect(onChange).toHaveBeenLastCalledWith("en-us");
    fireEvent.keyDown(input(), { key: "ArrowDown" });
    expect(screen.getByRole("listbox")).toBeVisible();
    fireEvent.keyDown(input(), { key: "Escape" });
    expect(screen.queryByRole("listbox")).toBeNull();
  });
  it("serbest metin kabul edilir; etiketle tam eşleşen metin koda çevrilir; eşleşme yoksa bilgi mesajı gösterir", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    fireEvent.change(input(), { target: { value: "xx-yy" } });
    expect(onChange).toHaveBeenLastCalledWith("xx-yy");
    expect(screen.getByText(messages.Game.comboboxNoOptions)).toBeVisible();
    fireEvent.change(input(), { target: { value: "türkçe (türkiye)" } });
    expect(onChange).toHaveBeenLastCalledWith("tr-tr");
  });
  it("ok düğmesi listeyi açıp kapatır; dışarıdan gelen değer (temizle) metni sıfırlar", () => {
    render(<Harness initial="en-us" />);
    expect(input()).toHaveValue("İngilizce (ABD)");
    fireEvent.click(screen.getByRole("button", { name: messages.Game.comboboxShowOptions }));
    expect(screen.getByRole("listbox")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: messages.Game.comboboxShowOptions }));
    expect(screen.queryByRole("listbox")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "temizle" }));
    expect(input()).toHaveValue("");
  });
});
