import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { GameDatePicker, type GameDatePickerProps } from "./game-date-picker";

const t = messages.Game;
function Harness(props: Partial<GameDatePickerProps> & { onChange?: (value: string) => void }) {
  const [value, setValue] = useState(props.value ?? "");
  return <NextIntlClientProvider locale="tr" messages={messages}>
    <label htmlFor="d">Tarih</label>
    <GameDatePicker id="d" value={value} min={props.min} max={props.max} onValueChange={(next) => { setValue(next); props.onChange?.(next); }} />
    <button type="button" onClick={() => setValue("")}>dışarıdan temizle</button>
  </NextIntlClientProvider>;
}
const field = () => screen.getByLabelText("Tarih") as HTMLInputElement;
const openCalendar = () => fireEvent.click(screen.getByRole("button", { name: t.datePickerOpen }));

beforeEach(() => { vi.useFakeTimers({ toFake: ["Date"] }); vi.setSystemTime(new Date("2026-11-08T10:00:00Z")); });
afterEach(() => { cleanup(); vi.useRealTimers(); });

describe("Özel tarih seçici", () => {
  it("ISO değeri yerel biçimde (gg.aa.yyyy) gösterir ve yerel input[type=date] kullanmaz", () => {
    render(<Harness value="2022-02-25" />);
    expect(field()).toHaveValue("25.02.2022");
    expect(field()).toHaveAttribute("type", "text");
    expect(field()).toHaveAttribute("placeholder", t.datePickerPlaceholder);
  });
  it("elle yazılan ISO veya yerel biçimli tam tarihi ISO olarak bildirir; kısmi metin bildirilmez", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    fireEvent.change(field(), { target: { value: "2026-05-01" } });
    expect(onChange).toHaveBeenLastCalledWith("2026-05-01");
    fireEvent.change(field(), { target: { value: "03.07.2020" } });
    expect(onChange).toHaveBeenLastCalledWith("2020-07-03");
    onChange.mockClear();
    fireEvent.change(field(), { target: { value: "03.07" } });
    expect(onChange).not.toHaveBeenCalled();
  });
  it("geçersiz tarih (31.02.2020) bildirilmez ve odaktan çıkınca uyarı gösterir; boşaltmak değeri temizler", () => {
    const onChange = vi.fn();
    render(<Harness value="2020-01-01" onChange={onChange} />);
    fireEvent.change(field(), { target: { value: "31.02.2020" } });
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.blur(field());
    expect(screen.getByRole("alert")).toHaveTextContent("Geçerli bir tarih girin");
    expect(field()).toHaveAttribute("aria-invalid", "true");
    fireEvent.change(field(), { target: { value: "" } });
    expect(onChange).toHaveBeenLastCalledWith("");
  });
  it("takvimi açar: bugünü vurgular, günleri düğme olarak sunar ve bir gün seçince ISO bildirir ve kapanır", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    openCalendar();
    const dialog = screen.getByRole("dialog", { name: t.datePickerDialog });
    expect(within(dialog).getByRole("combobox", { name: t.datePickerMonth })).toHaveValue("10");
    expect(within(dialog).getByRole("combobox", { name: t.datePickerYear })).toHaveValue("2026");
    expect(within(dialog).getAllByRole("gridcell", { name: /8 Kasım 2026/ })[0]).toHaveAttribute("aria-current", "date");
    fireEvent.click(within(dialog).getAllByRole("gridcell", { name: /15 Kasım 2026/ })[0]);
    expect(onChange).toHaveBeenLastCalledWith("2026-11-15");
    expect(field()).toHaveValue("15.11.2026");
    expect(screen.queryByRole("dialog")).toBeNull();
  });
  it("ay/yıl seçimi ve önceki-sonraki ay düğmeleri görünümü değiştirir", () => {
    render(<Harness value="2022-02-25" />);
    openCalendar();
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("combobox", { name: t.datePickerMonth })).toHaveValue("1");
    fireEvent.click(within(dialog).getByRole("button", { name: t.datePickerNextMonth }));
    expect(within(dialog).getByRole("combobox", { name: t.datePickerMonth })).toHaveValue("2");
    fireEvent.change(within(dialog).getByRole("combobox", { name: t.datePickerYear }), { target: { value: "2015" } });
    expect(within(dialog).getAllByRole("gridcell", { name: /Mart 2015/ }).length).toBeGreaterThan(20);
    fireEvent.click(within(dialog).getByRole("button", { name: t.datePickerPrevMonth }));
    expect(within(dialog).getByRole("combobox", { name: t.datePickerMonth })).toHaveValue("1");
  });
  it("Bugün ve Temizle düğmeleri çalışır", () => {
    const onChange = vi.fn();
    render(<Harness value="2020-01-01" onChange={onChange} />);
    openCalendar();
    fireEvent.click(screen.getByRole("button", { name: t.datePickerToday }));
    expect(onChange).toHaveBeenLastCalledWith("2026-11-08");
    openCalendar();
    fireEvent.click(screen.getByRole("button", { name: t.datePickerClear }));
    expect(onChange).toHaveBeenLastCalledWith("");
    expect(field()).toHaveValue("");
  });
  it("min/max dışındaki günler devre dışıdır ve elle yazılsa da kabul edilmez", () => {
    const onChange = vi.fn();
    render(<Harness value="2026-11-10" min="2026-11-05" max="2026-11-20" onChange={onChange} />);
    openCalendar();
    expect(screen.getAllByRole("gridcell", { name: /4 Kasım 2026/ })[0]).toBeDisabled();
    expect(screen.getAllByRole("gridcell", { name: /21 Kasım 2026/ })[0]).toBeDisabled();
    expect(screen.getAllByRole("gridcell", { name: /5 Kasım 2026/ })[0]).toBeEnabled();
    fireEvent.change(field(), { target: { value: "2026-12-25" } });
    expect(onChange).not.toHaveBeenCalledWith("2026-12-25");
  });
  it("klavye: oklar odağı günler arasında taşır, PageDown ayı değiştirir, Esc kapatıp odağı alana verir", () => {
    render(<Harness value="2026-11-10" />);
    openCalendar();
    const grid = screen.getByRole("grid");
    fireEvent.keyDown(grid, { key: "ArrowRight" });
    expect(document.activeElement).toHaveAccessibleName(/11 Kasım 2026/);
    fireEvent.keyDown(grid, { key: "ArrowDown" });
    expect(document.activeElement).toHaveAccessibleName(/18 Kasım 2026/);
    fireEvent.keyDown(grid, { key: "PageDown" });
    expect(document.activeElement).toHaveAccessibleName(/18 Aralık 2026/);
    fireEvent.keyDown(grid, { key: "Escape" });
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(document.activeElement).toBe(field());
  });
  it("dışarıdan gelen değer (filtreleri temizle) metne yansır", () => {
    render(<Harness value="2022-02-25" />);
    expect(field()).toHaveValue("25.02.2022");
    act(() => { fireEvent.click(screen.getByRole("button", { name: "dışarıdan temizle" })); });
    expect(field()).toHaveValue("");
  });
  it("name verilirse form gönderiminde ISO değer gizli alanla gider (kontrolsüz kullanım)", () => {
    const { container } = render(<NextIntlClientProvider locale="tr" messages={messages}><form><GameDatePicker id="x" name="releaseDate" defaultValue="2026-03-05" /></form></NextIntlClientProvider>);
    const form = container.querySelector("form")!;
    expect(new FormData(form).get("releaseDate")).toBe("2026-03-05");
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "01.02.2024" } });
    expect(new FormData(form).get("releaseDate")).toBe("2024-02-01");
  });
});
