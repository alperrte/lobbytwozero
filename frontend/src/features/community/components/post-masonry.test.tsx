import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PostMasonry } from "./post-masonry";

const columns = vi.hoisted(() => ({ count: 3 }));
vi.mock("../hooks/use-column-count", () => ({ useColumnCount: () => columns.count }));

const items = Array.from({ length: 7 }, (_, index) => ({ id: `p${index + 1}` }));
const mount = () => render(<PostMasonry items={items} render={(item) => <span>{item.id}</span>} />);
const lists = () => screen.getAllByRole("list");
const labels = (list: HTMLElement) => within(list).getAllByRole("listitem").map((node) => node.textContent);

beforeEach(() => {
  columns.count = 3;
});

describe("PostMasonry (doğal yükseklikli sütunlar)", () => {
  it("öğeleri soldan sağa sırayla sütunlara dağıtır", () => {
    mount();
    expect(lists()).toHaveLength(3);
    expect(labels(lists()[0])).toEqual(["p1", "p4", "p7"]);
    expect(labels(lists()[1])).toEqual(["p2", "p5"]);
    expect(labels(lists()[2])).toEqual(["p3", "p6"]);
  });

  it("tek sütunda sıra korunur; sütunlar satır yüksekliğine eşitlenmez", () => {
    columns.count = 1;
    mount();
    expect(lists()).toHaveLength(1);
    expect(labels(lists()[0])).toEqual(items.map((item) => item.id));
    expect(lists()[0].parentElement?.className).toContain("items-start");
  });
});
