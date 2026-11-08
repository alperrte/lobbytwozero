"use client";

import Image from "next/image";
import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import type { Category, Product } from "../types/hardware";
import { approvedImagePath, productVisual, visualAssets, type ApprovedHardwareImage } from "../schemas/product-visual";
import { formatSpec, readSpec } from "../schemas/spec-profile";
import { CatalogProductArt } from "./catalog-product-art";
import "./hardware-product-visual.css";

export function HardwareProductVisual({ product, category, variant = "card", className = "", approvedImage }: {
  product?: Product; category?: Category; variant?: "card" | "detail" | "compact" | "category";
  className?: string; approvedImage?: ApprovedHardwareImage;
}) {
  const t = useTranslations("Hardware");
  const locale = useLocale();
  const [failed, setFailed] = useState<string>();
  const selected = productVisual(product, category);
  const approved = approvedImagePath(approvedImage);
  const real = approved && approved !== failed ? approved : undefined;
  const compact = variant === "compact" || variant === "category";
  const source = real ?? (selected.kind === "OTHER" ? undefined : `/assets/hardware/illustrations/${visualAssets[selected.kind]}.svg`);
  const notice = t("productVisual.representative");
  return <div className={`hardwareVisual hardwareVisual--${variant} ${className}`} data-testid="hardware-product-visual"
    data-kind={selected.kind} data-gpu-vendor={selected.vendor} data-real-image={Boolean(real)} title={compact ? notice : undefined}>
    {!compact ? <div className="hardwareVisual__brand" title={selected.manufacturer ?? t("catalog.brandIndependent")}>
      {selected.manufacturer ?? t("catalog.brandIndependent")}
    </div> : null}
    {source ? <Image className="hardwareVisual__image" src={source} width={640} height={360}
      alt={real ? approvedImage!.alt : ""} unoptimized loading={variant === "detail" ? "eager" : "lazy"}
      onError={real ? () => setFailed(real) : undefined} />
      : <CatalogProductArt category={product?.category ?? category ?? "OTHER"} className="hardwareVisual__image" />}
    {!compact && product ? <>
      <div className="hardwareVisual__facts">{selected.badgeKeys.map((key) => <span key={key}
        title={t.has(`fields.${product.category}.${key}`) ? t(`fields.${product.category}.${key}`) : t.has(`specs.${key}`) ? t(`specs.${key}`) : undefined}>
        {formatSpec(readSpec(product, key), locale, { yes: t("visual.yes"), no: t("visual.no") })}
      </span>)}</div>
      {selected.vendor ? <span className="hardwareVisual__vendor" aria-label={t("productVisual.gpuVendor", { vendor: selected.vendor })}>
        <span>GPU</span> {selected.vendor}
      </span> : null}
    </> : null}
    {!real && variant !== "category" ? <span className={`hardwareVisual__notice${compact ? " sr-only" : ""}`}>{notice}</span> : null}
  </div>;
}
