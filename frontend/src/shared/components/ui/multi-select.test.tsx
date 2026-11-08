import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MultiSelect } from "./multi-select";
import { Select, type SelectOption } from "./select";

const options: SelectOption[] = [
  { value: "am5", label: "AM5" },
  { value: "lga", label: "LGA1700", disabled: true },
  { value: "am4", label: "AM4" },
  { value: "tr5", label: "sTR5" }
];

const box = () => screen.getByRole("combobox", { name: "Soket" });
const option = (name: string) => screen.getByRole("option", { name });

describe("MultiSelect", () => {
  it("boşken yer tutucuyu gösterir, açılınca onay kutulu çoklu seçim listesi sunar", () => {
    render(<MultiSelect aria-label="Soket" options={options} placeholder="Tümü" />);
    expect(box().textContent).toBe("Tümü");
    fireEvent.click(box());
    expect(screen.getByRole("listbox").getAttribute("aria-multiselectable")).toBe("true");
    expect(within(screen.getByRole("listbox")).getAllByRole("option")).toHaveLength(4);
  });

  it("seçim yapınca liste açık kalır, birden çok değer işaretlenebilir ve onChange sırayla çağrılır", () => {
    const onChange = vi.fn();
    render(<MultiSelect aria-label="Soket" options={options} onChange={onChange} placeholder="Tümü" />);
    fireEvent.click(box());
    fireEvent.click(option("AM5"));
    fireEvent.click(option("AM4"));
    expect(onChange).toHaveBeenNthCalledWith(1, ["am5"]);
    expect(onChange).toHaveBeenNthCalledWith(2, ["am5", "am4"]);
    expect(screen.getByRole("listbox")).toBeDefined();
    expect(option("AM5").getAttribute("aria-selected")).toBe("true");
    fireEvent.click(option("AM5"));
    expect(onChange).toHaveBeenLastCalledWith(["am4"]);
  });

  it("tetikleyici seçimi özetler: tek etiket, birden çokta ilk etiket ve +N", () => {
    render(<MultiSelect aria-label="Soket" options={options} defaultValue={["am4", "am5", "tr5"]} placeholder="Tümü" />);
    expect(box().textContent).toContain("AM5");
    expect(box().textContent).toContain("+2");
  });

  it("form için her değeri aynı adlı ayrı gizli girdide taşır", () => {
    render(<form data-testid="form"><MultiSelect aria-label="Soket" name="socket" options={options} defaultValue={["am5", "am4"]} /></form>);
    const data = new FormData(screen.getByTestId("form") as HTMLFormElement);
    expect(data.getAll("socket")).toEqual(["am5", "am4"]);
  });

  it("devre dışı seçenek işaretlenmez", () => {
    const onChange = vi.fn();
    render(<MultiSelect aria-label="Soket" options={options} onChange={onChange} />);
    fireEvent.click(box());
    fireEvent.click(option("LGA1700"));
    expect(onChange).not.toHaveBeenCalled();
  });

  it("klavye: ok tuşları gezinir, Boşluk işaretler ve açık bırakır, Esc kapatır", () => {
    const onChange = vi.fn();
    render(<MultiSelect aria-label="Soket" options={options} onChange={onChange} />);
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    expect(box().getAttribute("aria-activedescendant")).toBe(option("AM5").id);
    fireEvent.keyDown(box(), { key: " " });
    fireEvent.keyDown(box(), { key: "ArrowDown" });
    expect(box().getAttribute("aria-activedescendant")).toBe(option("AM4").id);
    fireEvent.keyDown(box(), { key: "Enter" });
    expect(onChange).toHaveBeenLastCalledWith(["am5", "am4"]);
    expect(screen.getByRole("listbox")).toBeDefined();
    fireEvent.keyDown(box(), { key: "Escape" });
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("harf yazarak seçeneğe atlar", () => {
    render(<MultiSelect aria-label="Soket" options={options} />);
    fireEvent.keyDown(box(), { key: "s" });
    expect(box().getAttribute("aria-activedescendant")).toBe(option("sTR5").id);
  });

  it("kontrollü kullanımda değeri dışarıdan alır", () => {
    const view = render(<MultiSelect aria-label="Soket" options={options} value={["am4"]} />);
    expect(box().textContent).toBe("AM4");
    view.rerender(<MultiSelect aria-label="Soket" options={options} value={["am5"]} />);
    expect(box().textContent).toBe("AM5");
  });

  it("oval görünüm 12px yuvarlak çerçeve sınıfı kullanır", () => {
    render(<MultiSelect aria-label="Soket" options={options} variant="oval" />);
    expect(box().className).toContain("rounded-xl");
  });
});

describe("Select oval", () => {
  it("oval görünüm MultiSelect ile aynı yuvarlaklıkta", () => {
    render(<Select aria-label="Soket" options={options} variant="oval" />);
    expect(box().className).toContain("rounded-xl");
  });
});
