import type { ReactNode } from "react";
import {
  compiledLayers,
  SPRITE_HEIGHT,
  SPRITE_WIDTH,
  type SpriteLayerName
} from "./sprite-data";

/**
 * Tek bir sprite katmanı: yalnız renk başına bir <path> içeren, keskin kenarlı SVG.
 * Hangi katmanın ne zaman görüneceğini tactical.css zaman çizelgesi belirler (className ile).
 */
function SpriteLayer({ name, className }: { name: SpriteLayerName; className: string }) {
  return (
    <svg
      className={className}
      viewBox={`0 0 ${SPRITE_WIDTH} ${SPRITE_HEIGHT}`}
      shapeRendering="crispEdges"
      focusable="false"
    >
      {compiledLayers[name].map((run) => (
        <path key={run.key} className={`c-${run.key}`} d={run.d} />
      ))}
    </svg>
  );
}

/**
 * LTZ Operatörü. Gövde sabittir; bacak adımı, silah/poz ve gözlük durumu ayrı katmanlardır ve
 * V2 CSS'inde sahneye göre açılıp kapanır. `children`, sprite kutusuna göre konumlanan efektler içindir
 * (namlu ateşi, fişek gibi).
 */
export function OperatorSprite({ children }: { children?: ReactNode }) {
  return (
    <div className="v2-op">
      <div className="op-sprite">
        <SpriteLayer name="body" className="op-layer op-body" />
        <SpriteLayer name="legsStand" className="op-layer op-legs-stand" />
        <div className="op-legs-walk">
          <SpriteLayer name="legsA" className="op-layer op-legs-a" />
          <SpriteLayer name="legsB" className="op-layer op-legs-b" />
        </div>
        <SpriteLayer name="carry" className="op-layer op-pose op-carry" />
        <SpriteLayer name="aim" className="op-layer op-pose op-aim" />
        <SpriteLayer name="flare" className="op-layer op-pose op-flare" />
        <SpriteLayer name="salute" className="op-layer op-pose op-salute" />
        <SpriteLayer name="gogglesUp" className="op-layer op-goggles-up" />
        <SpriteLayer name="gogglesDown" className="op-layer op-goggles-down" />
      </div>
      {children}
    </div>
  );
}
