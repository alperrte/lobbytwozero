import { useId } from "react";
import type { Category } from "../types/hardware";

function Fan({ x, y, radius = 34 }: { x: number; y: number; radius?: number }) {
  return <g transform={`translate(${x} ${y})`}>
    <circle r={radius} fill="var(--ltz-background)" stroke="var(--ltz-control-border)" strokeWidth="2" />
    <circle r={radius - 5} fill="none" stroke="var(--ltz-border)" />
    {[0, 60, 120, 180, 240, 300].map((angle) => <path key={angle} d={`M5-5Q${radius + 4}-${radius - 4} 4-${radius - 7}Q-2-${radius - 7}-4-6Z`} transform={`rotate(${angle})`} fill="color-mix(in srgb, var(--ltz-control-border) 40%, var(--ltz-background))" stroke="var(--ltz-control-border)" strokeWidth=".7" />)}
    <circle r="8" fill="var(--ltz-surface-elevated)" stroke="var(--ltz-primary)" />
  </g>;
}

// Brand-free vector diagrams. These are visibly labelled category illustrations,
// not photos, product renders, or inferred specifications of the selected model.
export function CatalogProductArt({ category, className }: { category: Category; className?: string }) {
  const id = useId();
  const board = `url(#${id}-metal)`;
  const edge = "var(--ltz-control-border)";
  const accent = "var(--ltz-primary)";
  let shape;
  switch (category) {
    case "GPU":
      shape = <g transform="translate(9 2) rotate(-9 155 85)"><path d="M24 38 49 25h236v91l-18 12H24Z" fill={board} stroke={edge} strokeWidth="2" /><path d="m49 25 5 15h212l19-15M266 40v88M24 38h30v78" fill="none" stroke={edge} /><path d="M62 43h188M54 113h207" stroke={accent} strokeWidth="3" /><Fan x={87} y={78} /><Fan x={159} y={78} /><Fan x={231} y={78} /><path d="M67 128h117v8H67z" fill="var(--ltz-warning)" opacity=".65" /><path d="M20 25v104m0-104h12M20 129h12" stroke={edge} strokeWidth="4" /></g>;
      break;
    case "CPU":
      shape = <g transform="translate(3 -5)"><path d="m94 36 98-18 72 59-98 37Z" fill={board} stroke={edge} strokeWidth="2" /><path d="m94 36 3 68 70 43 97-42V77l-98 37Z" fill="var(--ltz-background)" stroke={edge} strokeWidth="2" /><path d="m94 36 72 55 98-14M166 91v23m1 0v33" fill="none" stroke={edge} /><path d="m119 42 65-13 50 43-65 22Z" fill="var(--ltz-surface)" stroke={edge} strokeWidth="2" /><path d="m128 45 52-10 40 34-52 17Z" fill={board} stroke={accent} /><path d="m109 108 4 9m9-2 4 9m9-2 4 9m9-2 4 9m27 0v-9m15 2v-9m15 2v-9m15 2v-9m15 2v-9" stroke="var(--ltz-warning)" strokeWidth="3" opacity=".65" /></g>;
      break;
    case "RAM":
      shape = <g transform="rotate(-12 160 80)">{[0, 28].map((offset) => <g key={offset} transform={`translate(${offset / 2} ${offset})`}><path d="M41 39h238v48H41z" fill={board} stroke={edge} strokeWidth="2" /><path d="M41 39h238l-9 8H50z" fill="var(--ltz-surface)" stroke={accent} strokeWidth="2" />{[65, 100, 135, 170, 205, 240].map((x) => <rect key={x} x={x} y="55" width="23" height="22" fill="var(--ltz-background)" stroke={edge} />)}<path d="M50 87h218v7H50z" fill="var(--ltz-warning)" opacity=".6" /><path d="M158 87v8" stroke="var(--ltz-background)" strokeWidth="6" /></g>)}</g>;
      break;
    case "STORAGE":
      shape = <g transform="rotate(-11 160 80)"><path d="M27 55h264v55H27z" fill={board} stroke={edge} strokeWidth="2" /><path d="M37 62h230v41H37z" fill="var(--ltz-background)" stroke={accent} /><path d="M57 69h82v26H57zm95 0h47v26h-47zm59 0h47v26h-47z" fill={board} stroke={edge} /><path d="M280 57v51" stroke="var(--ltz-warning)" strokeWidth="7" opacity=".7" /><circle cx="37" cy="82" r="3" fill={edge} /></g>;
      break;
    case "MOTHERBOARD":
      shape = <g transform="rotate(-12 160 80)"><path d="M99 13h129v133H99z" fill={board} stroke={edge} strokeWidth="2" /><path d="M124 38h45v44h-45z" fill="var(--ltz-background)" stroke={edge} strokeWidth="2" /><path d="M131 45h31v30h-31zM180 27v66m9-66v66M116 105h91m-91 11h70m-70 11h91" fill="none" stroke={edge} strokeWidth="3" /><path d="m103 17 18 4v66l-18 7m75 7h32v30h-32z" fill="var(--ltz-background)" stroke={accent} strokeWidth="2" /><path d="M144 82v13h25m-34-69h33M104 139h21" fill="none" stroke={accent} /></g>;
      break;
    case "COOLING":
      shape = <g transform="rotate(-7 155 80)"><rect x="14" y="36" width="210" height="83" rx="4" fill={board} stroke={edge} strokeWidth="2" /><Fan x={52} y={77} radius={30} /><Fan x={120} y={77} radius={30} /><Fan x={188} y={77} radius={30} /><path d="M224 52c51-54 96 35 63 43m-63-28c38-25 80 30 58 39" fill="none" stroke={edge} strokeWidth="8" /><circle cx="275" cy="112" r="24" fill="var(--ltz-background)" stroke={accent} strokeWidth="3" /><circle cx="275" cy="112" r="16" fill={board} stroke={edge} /></g>;
      break;
    case "PSU": shape = <g><path d="m79 41 145-15 37 22v78l-148 15-34-26Z" fill={board} stroke={edge} strokeWidth="2" /><path d="m79 41 34 21 148-14M113 62v79" fill="none" stroke={edge} /><Fan x={178} y={94} radius={33} /><path d="M88 66v38m10-32v38M242 69v35" stroke={accent} strokeWidth="3" /></g>; break;
    case "MONITOR": shape = <g><path d="M51 15h222v113H51z" fill={board} stroke={edge} strokeWidth="2" /><path d="M58 22h208v97H58z" fill="var(--ltz-background)" stroke={edge} /><path d="m65 111 56-83m-17 83 57-83" stroke={accent} opacity=".6" /><path d="M163 128v15m-44 2h87" stroke={edge} strokeWidth="5" /></g>; break;
    case "KEYBOARD": shape = <g transform="rotate(-8 160 80)"><path d="M22 38h277v89H22z" fill={board} stroke={edge} strokeWidth="2" />{[0, 1, 2, 3].map((row) => <g key={row}>{Array.from({ length: 12 }, (_, column) => <rect key={column} x={32 + column * 22} y={49 + row * 16} width="16" height="11" rx="1" fill="var(--ltz-background)" stroke={row === 0 ? accent : edge} />)}</g>)}<path d="M98 117h120" stroke={accent} strokeWidth="4" /></g>; break;
    case "MOUSE": shape = <g transform="rotate(-17 160 80)"><path d="M114 65c0-60 93-60 93 0v35c0 66-93 66-93 0Z" fill={board} stroke={edge} strokeWidth="2" /><path d="M160 24v45m-44-5h89" stroke={edge} /><rect x="156" y="34" width="8" height="22" rx="4" fill={accent} /><path d="M127 101c0 41 67 41 67 0" fill="none" stroke={accent} /></g>; break;
    case "HEADSET": shape = <g><path d="M102 105V69c0-77 124-77 124 0v36" fill="none" stroke={edge} strokeWidth="14" /><path d="M110 66c0-60 108-60 108 0" fill="none" stroke={accent} strokeWidth="3" /><rect x="91" y="72" width="32" height="60" rx="11" fill={board} stroke={edge} strokeWidth="2" /><rect x="205" y="72" width="32" height="60" rx="11" fill={board} stroke={edge} strokeWidth="2" /><path d="M221 132c0 13-28 13-46 13" fill="none" stroke={edge} strokeWidth="4" /></g>; break;
    default: shape = <g><path d="m162 15 83 39v64l-83 36-83-36V54Z" fill={board} stroke={edge} strokeWidth="2" /><path d="m79 54 83 39 83-39m-83 39v61" fill="none" stroke={edge} strokeWidth="2" /><path d="m95 47 67 32 67-32" fill="none" stroke={accent} strokeWidth="3" /></g>;
  }
  return <svg className={className} viewBox="0 0 320 160" fill="none" aria-hidden="true" focusable="false"><defs><linearGradient id={`${id}-metal`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="var(--ltz-control-border)" stopOpacity=".5" /><stop offset=".36" stopColor="var(--ltz-surface-elevated)" /><stop offset=".7" stopColor="var(--ltz-background)" /><stop offset="1" stopColor="var(--ltz-control-border)" stopOpacity=".35" /></linearGradient></defs>{shape}</svg>;
}
