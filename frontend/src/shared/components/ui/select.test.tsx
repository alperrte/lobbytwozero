import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Select, type SelectOption } from "./select";

const options: SelectOption[] = [
  { value: "", label: "Tümü" },
  { value: "am5", label: "AM5" },
  { value: "lga", label: "LGA1700", disabled: true },
  { value: "am4", label: "AM4" }
];

function box() {
  return screen.getByRole("combobox", { name: "Soket" });
}
function labels() {
  return within(screen.getByRole("listbox")).getAllByRole("option").map((item) => item.textContent);
}

describe("Select", () => {
  it("kapalıyken seçili etiketi gösterir, açılınca seçenekleri listeler", () => {
    render(<Select aria-label="Soket" options={options} defaultValue="am5" />);
    expect(box().textContent).toBe("AM5");
    expect(box().getAttribute("aria-expanded")).toBe("false");
    expect(screen.queryByRole("listbox")).toBeNull();
    fireEvent.click(box());
    expect(box().getAttribute("aria-expanded")).toBe("true");
    expect(labels()).toEqual(["Tümü", "AM5", "LGA1700", "AM4"]);
    expect(screen.getByRole("option", { name: "AM5" }).getAttribute("aria-selected")).toBe("true");
    expect(screen.getByRole("option", { name: "LGA1700" }).getAttribute("aria-disabled")).toBe("true");
  });

  it("seçenek tıklanınca onChange çağrılır, liste kapanır ve odak tetikleyiciye döner", () => {
    const onChange = vi.fn();
    render(<Select aria-label="Soket" options={options} onChange={onChange} />);
    fireEvent.click(box());
    fireEvent.click(screen.getByRole("option", { name: "AM4" }));
    expect(onChange).toHaveBeenCalledWith("am4");
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(box().textContent).toBe("AM4");
    expect(document.activeElement).toBe(box());
  });

  it("devre dışı seçenek seçilmez", () => {
    const onChange = vi.fn();
    render(<Select aria-label="Soket" options={options} onChange={onChange} />);
    fireEvent.click(box());
    fireEvent.click(screen.getByRole("option", { name: "LGA1700" }));
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole("listbox")).toBeDefined();
  });

  it("aynı değer yeniden seçilirse onChange çağrılmaz", () => {
    const onChange = vi.fn();
    render(<Select aria-label="Soket" options={options} defaultValue="am5" onChange={onChange} />);
    fireEvent.click(box());
    fireEvent.click(screen.getByRole("option", { name: "AM5" }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it("kontrollü kullanımda değeri dışarıdan alır", () => {
    const onChange = vi.fn();
    const view = render(<Select aria-label="Soket" options={options} value="am4" onChange={onChange} />);
    expect(box().textContent).toBe("AM4");
    fireEvent.click(box());
    fireEvent.click(screen.getByRole("option", { name: "AM5" }));
    expect(onChange).toHaveBeenCalledWith("am5");
    expect(box().textContent).toBe("AM4");
    view.rerender(<Select aria-label="Soket" options={options} value="am5" onChange={onChange} />);
    expect(box().textContent).toBe("AM5");
  });

  it("ok tuşları devre dışı seçenekleri atlar, Enter seçer", () => {
    const onChange = vi.fn();
    render(<Select aria-label="Soket" options={options} defaultValue="am5" onChange={onChange} />);
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "AM5" }).id);
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "AM4" }).id);
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "Tümü" }).id);
    fireEvent.keyDown(box(), { key: "ArrowUp" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "AM4" }).id);
    fireEvent.keyDown(box(), { key: "Enter" });
    expect(onChange).toHaveBeenCalledWith("am4");
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("Home ve End ilk ve son etkin seçeneğe gider", () => {
    render(<Select aria-label="Soket" options={options} defaultValue="am5" />);
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    fireEvent.keyDown(box(), { key: "End" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "AM4" }).id);
    fireEvent.keyDown(box(), { key: "Home" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "Tümü" }).id);
  });

  it("Esc yalnız listeyi kapatır ve olayı yukarı iletmez; Tab listeyi kapatır", () => {
    const outer = vi.fn();
    const spy = (event: KeyboardEvent) => event.key === "Escape" && outer();
    document.addEventListener("keydown", spy);
    render(<Select aria-label="Soket" options={options} />);
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    fireEvent.keyDown(box(), { key: "Escape" });
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(outer).not.toHaveBeenCalled();
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    fireEvent.keyDown(box(), { key: "Tab" });
    expect(screen.queryByRole("listbox")).toBeNull();
    document.removeEventListener("keydown", spy);
  });

  it("harf yazınca eşleşen seçeneğe atlar", () => {
    render(<Select aria-label="Soket" options={options} defaultValue="" />);
    fireEvent.keyDown(box(), { key: "a" });
    fireEvent.keyDown(box(), { key: "m" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "AM5" }).id);
    fireEvent.keyDown(box(), { key: "z" });
    expect(box().getAttribute("aria-activedescendant")).toBe(screen.getByRole("option", { name: "AM5" }).id);
  });

  it("dışarı basınca kapanır", () => {
    render(<><Select aria-label="Soket" options={options} /><button type="button">Dış</button></>);
    fireEvent.click(box());
    fireEvent.pointerDown(screen.getByRole("button", { name: "Dış" }));
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("name verilirse seçili değer formda gizli girdi olarak gider", () => {
    render(<form aria-label="f"><Select aria-label="Soket" name="socket" options={options} defaultValue="am4" /></form>);
    const form = screen.getByRole("form") as HTMLFormElement;
    expect(new FormData(form).get("socket")).toBe("am4");
    fireEvent.click(box());
    fireEvent.click(screen.getByRole("option", { name: "AM5" }));
    expect(new FormData(form).get("socket")).toBe("am5");
  });

  it("devre dışıyken açılmaz ve forma girmez", () => {
    render(<form aria-label="f"><Select aria-label="Soket" name="socket" options={options} defaultValue="am4" disabled /></form>);
    expect((box() as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(box());
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(new FormData(screen.getByRole("form") as HTMLFormElement).has("socket")).toBe(false);
  });

  it("seçili değer listede yoksa placeholder gösterilir ve etiketle eşleşir", () => {
    render(<><label htmlFor="s">Soket</label><Select id="s" options={options} defaultValue="yok" placeholder="Seçin" /></>);
    expect(screen.getByRole("combobox", { name: "Soket" }).textContent).toBe("Seçin");
  });

  it("modal dialog içindeyse liste dialog'a bağlanır", () => {
    render(<dialog open data-testid="dlg"><Select aria-label="Soket" options={options} /></dialog>);
    fireEvent.click(box());
    expect(screen.getByTestId("dlg").contains(screen.getByRole("listbox"))).toBe(true);
  });
});
