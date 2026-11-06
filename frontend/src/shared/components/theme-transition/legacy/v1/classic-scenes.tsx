import { BoltScene, CityScene, CloudScene, RainScene, RidgeScene } from "./scenes";

/**
 * V1 (klasik) sahneler. İçerik ve sınıf adları V1'in ilk halinden değişmeden taşınmıştır;
 * stiller v1/classic.css içindedir. Katmanın kendisi (popover, key, --fx-dur) theme-transition-layer.tsx'tedir.
 */

/** Dark → Light: gün doğumu. */
export function SunriseScene() {
  return (
    <>
      <div className="fx-sky-night" />
      <div className="fx-sky-dawn" />
      <div className="fx-glow" />
      <div className="fx-sun" />
      <RidgeScene />
    </>
  );
}

/** Light → Dark: yağmurlu, şimşekli gece. */
export function StormScene() {
  return (
    <>
      <div className="fx-sky-day" />
      <div className="fx-sky-storm" />
      <div className="fx-sky-night" />
      <div className="fx-moon" />
      <CloudScene />
      <RainScene />
      <div className="fx-flash" />
      <BoltScene />
      <CityScene />
    </>
  );
}
