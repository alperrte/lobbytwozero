import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Checkbox } from "./checkbox";

describe("Checkbox", () => {
  it("etiketle eşleşir ve tıklanınca durumu değiştirir", () => {
    const onChange = vi.fn();
    render(<Checkbox label="Tümleşik grafik" onChange={onChange} />);
    const box = screen.getByRole("checkbox", { name: "Tümleşik grafik" }) as HTMLInputElement;
    expect(box.checked).toBe(false);
    fireEvent.click(screen.getByText("Tümleşik grafik"));
    expect(box.checked).toBe(true);
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it("işaretliyken value ile gönderilir, işaretli değilken forma girmez", () => {
    render(<form aria-label="f"><Checkbox label="Grafik" name="integratedGraphics" value="true" /></form>);
    const form = screen.getByRole("form") as HTMLFormElement;
    expect(new FormData(form).has("integratedGraphics")).toBe(false);
    fireEvent.click(screen.getByRole("checkbox"));
    expect(new FormData(form).get("integratedGraphics")).toBe("true");
  });

  it("defaultChecked ile işaretli başlar; devre dışıyken değişmez", () => {
    render(<><Checkbox label="A" defaultChecked /><Checkbox label="B" disabled /></>);
    expect((screen.getByRole("checkbox", { name: "A" }) as HTMLInputElement).checked).toBe(true);
    expect((screen.getByRole("checkbox", { name: "B" }) as HTMLInputElement).disabled).toBe(true);
  });

  it("açıklama aria-describedby ile bağlanır", () => {
    render(<Checkbox label="Grafik" hint="Ekran kartı gerekmez" aria-describedby="dis" />);
    const described = screen.getByRole("checkbox").getAttribute("aria-describedby") ?? "";
    expect(described.split(" ")).toContain("dis");
    expect(document.getElementById(described.split(" ")[1])?.textContent).toBe("Ekran kartı gerekmez");
  });
});
