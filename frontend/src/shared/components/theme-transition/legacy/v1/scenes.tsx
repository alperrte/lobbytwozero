import type { CSSProperties } from "react";

/*
 * Tema geçişi sahnelerinin SVG parçaları. Tamamı özgün, sade vektör çizimdir; üçüncü taraf
 * marka veya karakter görseli içermez. Konumlar sabittir (rastgelelik yok), bu yüzden
 * çıktı deterministiktir. Renkler theme-transition.css içindeki --fx-* değişkenlerindendir.
 */

export function RidgeScene() {
  return (
    <svg
      className="fx-ridges"
      viewBox="0 0 1440 400"
      preserveAspectRatio="xMidYMax slice"
      focusable="false"
    >
      <path
        className="fx-ridge-3"
        d="M0 400V250l120-60 120 50 140-90 140 80 120-50 140 70 140-90 140 70 140-60 140 70 120-40v200z"
      />
      <path
        className="fx-ridge-2"
        d="M0 400V300l160-60 140 50 160-70 160 80 160-50 160 60 160-75 160 65 180-40v180z"
      />
      <path
        className="fx-ridge-1"
        d="M0 400V340l200-40 180 45 180-35 200 40 200-35 200 35 160-30 120 25v70z"
      />
    </svg>
  );
}

const cloudShapes = [
  { cx: 160, cy: 70, rx: 260, ry: 70, dark: true },
  { cx: 520, cy: 110, rx: 300, ry: 80, dark: false },
  { cx: 880, cy: 60, rx: 280, ry: 70, dark: true },
  { cx: 1240, cy: 120, rx: 300, ry: 85, dark: false },
  { cx: 700, cy: 190, rx: 360, ry: 70, dark: true },
  { cx: 1100, cy: 230, rx: 320, ry: 60, dark: false }
] as const;

export function CloudScene() {
  return (
    <svg
      className="fx-clouds"
      viewBox="0 0 1440 320"
      preserveAspectRatio="xMidYMin slice"
      focusable="false"
    >
      {cloudShapes.map((cloud) => (
        <ellipse
          key={`${cloud.cx}-${cloud.cy}`}
          cx={cloud.cx}
          cy={cloud.cy}
          rx={cloud.rx}
          ry={cloud.ry}
          className={cloud.dark ? "fx-cloud-a" : "fx-cloud-b"}
        />
      ))}
    </svg>
  );
}

const rainLines = Array.from({ length: 22 }, (_, index) => ({
  x: (index * 47 + 11) % 100,
  length: 5 + ((index * 3) % 4),
  delay: -((index * 83) % 520)
}));

export function RainScene() {
  return (
    <svg
      className="fx-rain"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      focusable="false"
    >
      {rainLines.map((line) => (
        <line
          key={line.x}
          x1={line.x}
          y1={0}
          x2={line.x - 0.8}
          y2={line.length}
          vectorEffect="non-scaling-stroke"
          style={{ animationDelay: `${line.delay}ms` } as CSSProperties}
        />
      ))}
    </svg>
  );
}

export function BoltScene() {
  return (
    <svg
      className="fx-bolt"
      viewBox="0 0 1440 800"
      preserveAspectRatio="xMidYMin slice"
      focusable="false"
    >
      <polyline points="1010,60 960,230 1005,250 930,430 975,445 900,610" />
      <polyline points="960,230 1040,320 1022,385" />
    </svg>
  );
}

const towers = [
  [0, 90, 150],
  [90, 70, 230],
  [160, 110, 180],
  [270, 60, 300],
  [330, 90, 200],
  [420, 120, 260],
  [540, 70, 170],
  [610, 100, 330],
  [710, 80, 210],
  [790, 120, 190],
  [910, 70, 280],
  [980, 100, 220],
  [1080, 90, 320],
  [1170, 80, 190],
  [1250, 100, 250],
  [1350, 90, 170]
] as const;

const CITY_HEIGHT = 360;

const windows = towers.flatMap(([x, width, height], tower) =>
  height < 240
    ? []
    : Array.from({ length: 6 }, (_, k) => ({
        key: `${tower}-${k}`,
        x: x + 12 + (k % 3) * Math.max(14, Math.floor((width - 24) / 3)),
        y: CITY_HEIGHT - height + 30 + Math.floor(k / 3) * 34 + ((tower * 5) % 3) * 12
      })).filter((_, k) => (tower * 7 + k * 3) % 5 < 2)
);

export function CityScene() {
  return (
    <svg
      className="fx-city"
      viewBox={`0 0 1440 ${CITY_HEIGHT}`}
      preserveAspectRatio="xMidYMax slice"
      focusable="false"
    >
      <g className="fx-city-far">
        {towers.map(([x, width, height]) => (
          <rect
            key={`far-${x}`}
            x={x + 20}
            y={CITY_HEIGHT - height - 36}
            width={width}
            height={height + 36}
          />
        ))}
      </g>
      <g className="fx-city-near">
        {towers.map(([x, width, height]) => (
          <rect
            key={`near-${x}`}
            x={x}
            y={CITY_HEIGHT - height}
            width={width}
            height={height}
          />
        ))}
        <path d="M640 30l10-30 10 30zM1110 40l8-40 8 40z" />
      </g>
      <g>
        {windows.map((lit) => (
          <rect
            key={lit.key}
            className="fx-window"
            x={lit.x}
            y={lit.y}
            width={7}
            height={11}
          />
        ))}
      </g>
    </svg>
  );
}
