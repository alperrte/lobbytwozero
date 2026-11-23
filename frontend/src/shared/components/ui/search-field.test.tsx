import { fireEvent, render, screen } from "@testing-library/react";
import { createRef, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { SearchField } from "./search-field";

function Controlled({ onValue }: { onValue: (value: string) => void }) {
  const [value, setValue] = useState("");
  return (
    <SearchField
      label="Oyun ara"
      value={value}
      onChange={(event) => {
        setValue(event.target.value);
        onValue(event.target.value);
      }}
    />
  );
}

describe("SearchField", () => {
  it("etiketi görünmez ama erişilebilir; tür search; LTZ ovali ve hover snake taşır", () => {
    render(<SearchField label="Oyun ara" placeholder="Ara…" />);
    const input = screen.getByRole("searchbox", { name: "Oyun ara" });
    expect(input).toHaveAttribute("type", "search");
    expect(input).toHaveAttribute("autocomplete", "off");
    expect(input.className).toContain("rounded-xl");
    expect(screen.getByText("Oyun ara").className).toContain("sr-only");
    const snake = screen.getByTestId("control-snake");
    expect(snake.parentElement).toBe(input.parentElement);
    expect(snake.parentElement?.className).toContain("rounded-xl");
  });

  it("showLabel ile etiket görünür olur", () => {
    render(<SearchField label="Oyuncu ara" showLabel />);
    expect(screen.getByText("Oyuncu ara").className).not.toContain("sr-only");
  });

  it("kontrollü kullanımda değeri iletir", () => {
    const onValue = vi.fn();
    render(<Controlled onValue={onValue} />);
    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "elden" } });
    expect(onValue).toHaveBeenLastCalledWith("elden");
    expect(screen.getByRole("searchbox")).toHaveValue("elden");
  });

  it("form içinde name/defaultValue ile çalışır; ref input'a iletilir; className dış sarmalayıcıya gider", () => {
    const ref = createRef<HTMLInputElement>();
    render(
      <form data-testid="form">
        <SearchField ref={ref} label="Ara" name="q" defaultValue="rtx" maxLength={64} className="w-60" />
      </form>
    );
    const data = new FormData(screen.getByTestId("form") as HTMLFormElement);
    expect(data.get("q")).toBe("rtx");
    expect(ref.current).toBe(screen.getByRole("searchbox"));
    expect(ref.current).toHaveAttribute("maxlength", "64");
    expect(ref.current?.className).not.toContain("w-60");
    expect(screen.getByRole("searchbox").closest(".w-60")).not.toBeNull();
  });
});
