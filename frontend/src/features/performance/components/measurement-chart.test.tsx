import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import type { Series } from "../types/performance";
import { MeasurementChart } from "./measurement-chart";

describe("Measurement chart", () => {
  const base: Series = { sessionId: "session", metric: "CPU_USAGE_PERCENT", unit: "%", resolution: "BUCKET", downsampled: true, bucketMillis: 1000, aggregation: "AVG_MIN_MAX", totalSamples: 3, points: [{ at: "2026-01-01T10:00:00Z", value: 0, min: 0, max: 0, count: 3 }] };
  it("labels aggregation and keeps a real zero in both chart and accessible table", () => {
    render(<NextIntlClientProvider locale="en" messages={en} timeZone="UTC"><MeasurementChart series={base} /></NextIntlClientProvider>);
    expect(screen.getByRole("img", { name: "CPU usage (%)" })).toHaveAccessibleDescription(/3 real samples/);
    const rows = within(screen.getByRole("table")).getAllByRole("row");
    expect(rows).toHaveLength(2);
    expect(within(rows[1]).getAllByRole("cell").map((cell) => cell.textContent)).toEqual(["0", "0", "0", "3"]);
    expect(document.querySelector("circle")?.getAttribute("cy")).toBe("125");
  });
  it("does not render an empty series as a zero chart", () => {
    render(<NextIntlClientProvider locale="en" messages={en}><MeasurementChart series={{ ...base, totalSamples: 0, points: [] }} /></NextIntlClientProvider>);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByText(en.Performance.measurementsEmpty)).toBeInTheDocument();
  });
});
