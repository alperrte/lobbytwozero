import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { RoomEnvironment } from "three/addons/environments/RoomEnvironment.js";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { EffectComposer } from "three/addons/postprocessing/EffectComposer.js";
import { RenderPass } from "three/addons/postprocessing/RenderPass.js";
import { UnrealBloomPass } from "three/addons/postprocessing/UnrealBloomPass.js";
import { OutputPass } from "three/addons/postprocessing/OutputPass.js";
import {
  isCompleteBuild, parseRoute, playableSteps, qualityProfiles, ramNodes, routePoint,
  type AssemblyStep, type RoutePoint, type SceneQuality, type WorkshopBuild
} from "../schemas/workshop";
import type { CaseColor } from "../types/hardware";

export type { SceneQuality } from "../schemas/workshop";

/**
 * LTZ Atölyesi 3D sahnesi (three.js). Model, montaj rotaları ve çakışma denetimi Blender betiğindedir
 * (docs-hardware/workshop/ltz_workshop_case.py); bu sınıf yalnız malzeme rengini, parça görünürlüğünü, GLB'deki
 * `ltz_entry` rotalarını oynatmayı, ışığı ve render bütçesini yönetir. Modelde düzeltme burada transform hilesiyle yapılmaz.
 * glTF ekseni: +Y yukarı, +Z cam yan panel (kameraya bakar), +X kasanın ön yüzü.
 *
 * Aşağıdaki sayısal renkler WebGL malzeme değerleridir (CSS değil): kasa boyaları, LTZ marka tonları ve üretici vurguları.
 * Kasa yüzeyi temadan bağımsızdır; beyaz kasa düşük metalik, yüksek pürüzlü ve zayıf yansımalı "boyalı sac" olarak ayarlıdır.
 */
type Finish = { body: number; mesh: number; metalness: number; roughness: number; env: number };
const caseFinish: Record<CaseColor, Finish> = {
  BLACK: { body: 0x14161b, mesh: 0x0d0e11, metalness: 0.55, roughness: 0.38, env: 1 },
  WHITE: { body: 0xc9ccd3, mesh: 0xb3b6be, metalness: 0.06, roughness: 0.52, env: 0.55 },
  GRAPHITE: { body: 0x3b3f47, mesh: 0x24272d, metalness: 0.55, roughness: 0.4, env: 1 },
  RED: { body: 0x7c1520, mesh: 0x2a0b0f, metalness: 0.45, roughness: 0.4, env: 1 }
};
const vendorAccent = { AMD: 0xff5a1f, Intel: 0x0071c5, NVIDIA: 0x76b900 } as const;
/** LTZ accent, primary, secondary: RGB ışıkları yalnız marka tonlarında yanar. */
const brandGlow = [0xff2e88, 0xe11d2e, 0xdb1b60] as const;
const rgbTone: Record<string, number> = { RGB_Fan: 0, RGB_RAM: 0, RGB_Strip: 0, RGB_GPU: 2, RGB_Pump: 1 };
/** Bloom eşiği 1.0: yalnız HDR yayıcılar (RGB, ~3.2) parlar; beyaz kasa gibi parlak yüzeyler pembe pus üretmez. */
const rgbIntensity = 3.2;

type View = { position: [number, number, number]; target: [number, number, number] };
const views: Record<"overview" | "board" | "gpu" | "top" | "fans", View> = {
  overview: { position: [0.6, 0.5, 1.12], target: [0, 0.25, 0] },
  board: { position: [0.2, 0.42, 0.66], target: [-0.06, 0.31, -0.08] },
  gpu: { position: [0.3, 0.27, 0.72], target: [-0.05, 0.2, 0.02] },
  top: { position: [0.44, 0.64, 0.82], target: [0, 0.4, -0.01] },
  fans: { position: [0.84, 0.4, 0.8], target: [0.04, 0.26, 0] }
};
const stepView: Record<AssemblyStep, keyof typeof views> = {
  motherboard: "board", cpu: "board", ram: "board", ssd: "board", gpu: "gpu", aio: "top", fans: "fans", rgb: "overview", power: "overview"
};
const stepNodes: Record<AssemblyStep, string[]> = {
  motherboard: ["Motherboard"], cpu: ["CPU"], ram: [], ssd: ["SSD"], gpu: ["GPU"], aio: ["AIO_Radiator", "AIO_Pump", "AIO_Tubes"],
  fans: ["Fan_Top_1", "Fan_Top_2", "Fan_Top_3", "Fan_Front_1", "Fan_Front_2", "Fan_Front_3", "Fan_Rear"], rgb: [], power: ["Cables"]
};
const slotNodes = { MOTHERBOARD: ["Motherboard"], CPU: ["CPU"], STORAGE: ["SSD"], GPU: ["GPU"] } as const;
const autoNodes = [...stepNodes.aio, ...stepNodes.fans];
const routed = ["Motherboard", "CPU", "RAM_1", "RAM_2", "RAM_3", "RAM_4", "SSD", "GPU", ...autoNodes, "Cables"];
const animated = [...routed, "Glass"];
/** GLB'de rota yoksa (eski varlık) parça yalnız cam tarafından düz girer. */
const fallbackRoute: RoutePoint[] = [[0, 0, 0.1], [0, 0, 0]];
/** Rotadaki her kırılım (ör. önce cam tarafından, sonra yuvaya kaydırma) için ek süre. */
const bendTime = 260;

export type SceneOptions = {
  host: HTMLElement;
  url: string;
  quality: SceneQuality;
  /** Ölçülen kare süresi bütçeyi aşarsa kaliteyi bir kademe düşürür (yalnız kullanıcı "Otomatik" seçtiyse). */
  adaptive?: boolean;
  reducedMotion: boolean;
  /** İnce işaretçide sürükleyerek döndürme; dokunmatikte sayfa kaydırması engellenmesin diye kapalıdır. */
  interactive: boolean;
  onStep: (step: AssemblyStep | "done") => void;
  onMotion: (spinning: boolean) => void;
  onQuality?: (quality: SceneQuality) => void;
};
type Tween = { start: number; duration: number; apply: (progress: number) => void; begin?: () => void; begun?: boolean; done?: boolean };

const easeOut = (t: number) => 1 - Math.pow(1 - t, 3);
const easeInOut = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const clamp01 = (t: number) => Math.min(1, Math.max(0, t));
/** Uyarlamalı kalite: 45 aktif karelik pencere, ortalama aralık bütçenin %145'ini aşarsa bir kademe düşülür. */
const sampleWindow = 45;
const sampleWarmup = 12;
const slowFactor = 1.45;
const severeWindow = 8;
const severeFactor = 3;

function radialTexture(inner: string, outer: string) {
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = 128;
  const context = canvas.getContext("2d")!;
  const gradient = context.createRadialGradient(64, 64, 0, 64, 64, 64);
  gradient.addColorStop(0, inner);
  gradient.addColorStop(1, outer);
  context.fillStyle = gradient;
  context.fillRect(0, 0, 128, 128);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

export class WorkshopScene {
  private readonly options: SceneOptions;
  private readonly renderer: THREE.WebGLRenderer;
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.PerspectiveCamera(28, 1, 0.01, 10);
  private readonly target = new THREE.Vector3(...views.overview.target);
  private readonly controls?: OrbitControls;
  private quality: SceneQuality;
  private adaptive: boolean;
  private composer?: EffectComposer;
  private bloom?: UnrealBloomPass;
  private environment?: THREE.Texture;
  private model?: THREE.Group;
  private readonly nodes = new Map<string, THREE.Object3D>();
  private readonly homes = new Map<string, THREE.Vector3>();
  private readonly routes = new Map<string, RoutePoint[]>();
  private readonly materials = new Map<string, THREE.MeshStandardMaterial>();
  private readonly originals = new Map<string, { color: THREE.Color; emissive: THREE.Color; intensity: number }>();
  private readonly rotors: THREE.Object3D[] = [];
  private glass?: THREE.MeshPhysicalMaterial;
  /** Cam paneline bağlı vidalar; panel söküldüğünde (cam görünmezken) havada asılı kalmasınlar diye birlikte gizlenir. */
  private glassFixtures: THREE.Object3D[] = [];
  private glow?: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  private shadow?: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  private rim?: THREE.PointLight;
  private lightStage = false;
  private build?: WorkshopBuild;
  private tweens: Tween[] = [];
  private events: Array<{ at: number; fire: () => void; fired?: boolean }> = [];
  private elapsed = 0;
  private playing = false;
  private rgbLevel = 0;
  private rgbSweep = -1;
  private spin = 0;
  private spinTarget = 0;
  private spinUntil = 0;
  private dirty = true;
  private interacting = false;
  private damping = false;
  private visible = true;
  private disposed = false;
  private looping = false;
  /** Son render zamanı (animasyon dt) ve kare sınırı için kayan saat. */
  private last = 0;
  private tick = 0;
  private warm = 0;
  private samples = 0;
  private sampleSum = 0;
  private readonly observers: Array<{ disconnect: () => void }> = [];
  private readonly onVisibility = () => { this.dirty = true; this.schedule(); };

  constructor(options: SceneOptions) {
    this.options = options;
    this.quality = options.quality;
    this.adaptive = options.adaptive ?? false;
    // Gerçek zamanlı gölge yok: temas gölgesi tek dokulu düzlemdir. Low profilde MSAA da kapalıdır.
    this.renderer = new THREE.WebGLRenderer({ antialias: options.quality !== "low", powerPreference: options.quality === "high" ? "high-performance" : "default" });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.AgXToneMapping;
    this.renderer.toneMappingExposure = 1;
    const canvas = this.renderer.domElement;
    canvas.setAttribute("aria-hidden", "true");
    canvas.dataset.testid = "workshop-canvas";
    canvas.style.display = "block";
    canvas.style.width = "100%";
    canvas.style.height = "100%";
    options.host.appendChild(canvas);

    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    pmrem.dispose();
    this.scene.environment = this.environment;
    this.scene.environmentIntensity = 0.8;
    const key = new THREE.DirectionalLight(0xffffff, 1.2);
    key.position.set(1.2, 2, 1.6);
    this.scene.add(key);
    this.rim = new THREE.PointLight(brandGlow[0], 0, 1.4, 1.6);
    this.rim.position.set(-0.05, 0.25, 0.32);
    this.scene.add(this.rim);

    this.shadow = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.55), new THREE.MeshBasicMaterial({ map: radialTexture("rgba(0,0,0,0.55)", "rgba(0,0,0,0)"), transparent: true, depthWrite: false }));
    this.shadow.rotation.x = -Math.PI / 2;
    this.shadow.position.y = 0.001;
    this.scene.add(this.shadow);
    this.glow = new THREE.Mesh(new THREE.PlaneGeometry(1.3, 1), new THREE.MeshBasicMaterial({ map: radialTexture("rgba(255,255,255,1)", "rgba(255,255,255,0)"), color: brandGlow[0], transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false }));
    this.glow.rotation.x = -Math.PI / 2;
    this.glow.position.set(-0.05, 0.002, 0.12);
    this.scene.add(this.glow);

    if (options.interactive) {
      this.controls = new OrbitControls(this.camera, canvas);
      Object.assign(this.controls, {
        enablePan: false, enableZoom: false, enableDamping: !options.reducedMotion, dampingFactor: 0.08, rotateSpeed: 0.55,
        minPolarAngle: 0.55, maxPolarAngle: 1.52, minAzimuthAngle: -0.45, maxAzimuthAngle: 1.45
      });
      this.controls.target.copy(this.target);
      this.controls.addEventListener("change", () => this.invalidate());
      this.controls.addEventListener("start", () => { this.interacting = true; this.schedule(); });
      this.controls.addEventListener("end", () => { this.interacting = false; this.invalidate(); });
    }
    this.updateBackground();
    this.applyQuality();
    const resize = new ResizeObserver(() => this.resize());
    resize.observe(options.host);
    const intersection = new IntersectionObserver(([item]) => { this.visible = Boolean(item?.isIntersecting); this.dirty = true; this.schedule(); });
    intersection.observe(options.host);
    const theme = new MutationObserver(() => this.updateBackground());
    theme.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme", "class"] });
    this.observers.push(resize, intersection, theme);
    document.addEventListener("visibilitychange", this.onVisibility);
  }

  async load() {
    const gltf = await new GLTFLoader().loadAsync(this.options.url);
    if (this.disposed) { gltf.scene.traverse((object) => this.disposeObject(object)); return; }
    this.model = gltf.scene;
    this.model.traverse((object) => {
      if (object.name) this.nodes.set(object.name, object);
      if (object.name.endsWith("_Rotor")) this.rotors.push(object);
      // Optimize GLB'de vidalar malzemeye göre birleşir (Glass_MetalSilver); ham çıktıda Glass_Thumb{n} adlarıyla gelir.
      if (/^Glass_(Thumb|Metal)/.test(object.name)) this.glassFixtures.push(object);
      const mesh = object as THREE.Mesh;
      if (!mesh.isMesh) return;
      const material = mesh.material as THREE.MeshStandardMaterial;
      if (material.name === "Glass") {
        this.glass ??= new THREE.MeshPhysicalMaterial({ name: "GlassRuntime", color: 0x0e1016, roughness: 0.06, metalness: 0, transparent: true, opacity: 0.1, depthWrite: false, envMapIntensity: 0.12, specularIntensity: 0.12 });
        mesh.material = this.glass;
        mesh.renderOrder = 2;
        material.dispose();
        return;
      }
      if (!this.materials.has(material.name)) {
        this.materials.set(material.name, material);
        this.originals.set(material.name, { color: material.color.clone(), emissive: material.emissive.clone(), intensity: material.emissiveIntensity });
      }
    });
    for (const name of animated) {
      const node = this.nodes.get(name);
      if (!node) continue;
      this.homes.set(name, node.position.clone());
      if (name !== "Glass") this.routes.set(name, parseRoute(node.userData.ltz_entry) ?? fallbackRoute);
    }
    this.scene.add(this.model);
    if (this.build) this.applyBuild();
    this.invalidate();
  }

  /** Renk, üretici vurgusu ve parça listesi. Oynatma sürmüyorsa son durum hemen gösterilir. */
  setBuild(build: WorkshopBuild) {
    this.build = build;
    if (this.model) this.applyBuild();
  }

  /** Kullanıcının seçtiği veya uyarlamalı düşüşle gelen kalite profili; `adaptive` yalnız "Otomatik" seçimde açıktır. */
  setQuality(quality: SceneQuality, adaptive = this.adaptive) {
    if (this.disposed) return;
    this.adaptive = adaptive;
    if (quality === this.quality) return;
    this.quality = quality;
    this.applyQuality();
  }

  private applyQuality() {
    const profile = qualityProfiles[this.quality];
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, profile.pixelRatio));
    if (profile.bloom && !this.composer) {
      this.composer = new EffectComposer(this.renderer);
      this.composer.addPass(new RenderPass(this.scene, this.camera));
      this.bloom = new UnrealBloomPass(new THREE.Vector2(256, 256), 0.32, 0.2, 1);
      this.composer.addPass(this.bloom);
      this.composer.addPass(new OutputPass());
    } else if (!profile.bloom && this.composer) {
      this.bloom?.dispose();
      this.composer.dispose();
      this.composer = undefined;
      this.bloom = undefined;
    }
    this.samples = 0;
    this.sampleSum = 0;
    this.warm = 0;
    this.renderer.domElement.dataset.quality = this.quality;
    this.resize();
    this.options.onQuality?.(this.quality);
  }

  private applyBuild() {
    const build = this.build!;
    const finish = caseFinish[build.caseColor];
    this.finish("CaseBody", finish.body, finish.metalness, finish.roughness, finish.env);
    this.finish("CaseMesh", finish.mesh, finish.metalness, Math.min(1, finish.roughness + 0.12), finish.env);
    this.accent("CPU_Accent", build.cpuVendor && vendorAccent[build.cpuVendor]);
    this.accent("Pump_Display", build.cpuVendor && vendorAccent[build.cpuVendor]);
    this.accent("GPU_Accent", build.gpuVendor && vendorAccent[build.gpuVendor]);
    if (!this.playing) this.showFinal();
    this.setRgb(this.rgbLevel);
    this.invalidate();
  }

  private finish(name: string, color: number, metalness: number, roughness: number, env: number) {
    const material = this.materials.get(name);
    if (!material) return;
    material.color.setHex(color);
    material.metalness = metalness;
    material.roughness = roughness;
    material.envMapIntensity = env;
  }

  private accent(name: string, hex?: number) {
    const material = this.materials.get(name);
    const original = this.originals.get(name);
    if (!material || !original) return;
    if (hex === undefined) { material.color.copy(original.color); material.emissive.copy(original.emissive); return; }
    material.color.setHex(hex);
    if (original.emissive.getHex() !== 0) material.emissive.setHex(hex);
  }

  /**
   * Montaj tamamlanmış hâl: kullanıcının parçaları ve sistem parçaları yerinde; güç yalnız tam kurulumda.
   * Montaj bitti bildirimi yalnız oynatma atlandığında veya istenen oynatma azaltılmış harekette tamamlandığında yapılır;
   * parça/renk değişikliğiyle son görünüme geçmek montaj oynatılmış sayılmaz.
   */
  showFinal(announce = this.playing) {
    if (!this.model || !this.build) return;
    this.playing = false;
    this.tweens = [];
    this.events = [];
    if (this.controls) this.controls.enabled = true;
    const complete = isCompleteBuild(this.build);
    for (const [name, home] of this.homes) this.nodes.get(name)!.position.copy(home);
    const ram = new Set(ramNodes(this.build.ramSticks));
    for (const index of [1, 2, 3, 4]) this.setVisible(`RAM_${index}`, ram.has(`RAM_${index}`));
    for (const [slot, names] of Object.entries(slotNodes)) for (const name of names) this.setVisible(name, this.build.parts[slot as keyof typeof slotNodes]);
    for (const name of autoNodes) this.setVisible(name, true);
    this.setVisible("Cables", complete);
    this.setVisible("Glass", true);
    this.setGlass(0.1);
    this.rgbSweep = -1;
    this.setRgb(complete ? 1 : 0);
    if (!complete) { this.spinTarget = 0; this.spin = 0; this.options.onMotion(false); }
    this.moveCamera(views.overview, 0);
    if (announce) this.options.onStep("done");
    this.invalidate();
  }

  /** Montajı baştan oynatır. Azaltılmış harekette son durum doğrudan gösterilir. */
  play() {
    if (!this.model || !this.build) return;
    if (this.options.reducedMotion) { this.showFinal(true); return; }
    const build = this.build;
    const steps = playableSteps(build);
    this.tweens = [];
    this.events = [];
    this.elapsed = 0;
    this.playing = true;
    this.spin = 0;
    this.spinTarget = 0;
    this.options.onMotion(false);
    this.rgbSweep = -1;
    this.setRgb(0);
    if (this.controls) this.controls.enabled = false;
    for (const name of animated) if (name !== "Glass") this.setVisible(name, false);
    const glass = this.nodes.get("Glass");
    const glassHome = this.homes.get("Glass");
    if (glass && glassHome) {
      this.tween(0, 450, (p) => { glass.position.set(glassHome.x, glassHome.y, glassHome.z + 0.14 * easeInOut(p)); this.setGlass(0.1 * (1 - p)); });
    }
    let time = 420;
    for (const step of steps) {
      const at = time;
      this.events.push({ at, fire: () => this.options.onStep(step) });
      this.moveCamera(views[stepView[step]], 820, at);
      const names = step === "ram" ? ramNodes(build.ramSticks) : stepNodes[step];
      let longest = 0;
      names.forEach((name, index) => {
        const delay = index * 110;
        longest = Math.max(longest, delay + this.enter(name, at + 120 + delay, step === "fans" ? 560 : 680));
      });
      let duration = 120 + longest + 80;
      if (step === "rgb") {
        this.events.push({ at, fire: () => { this.rgbSweep = 0; } });
        this.tween(at, 900, (p) => this.setRgb(easeOut(p)));
        duration = 1100;
      }
      if (step === "power") {
        this.events.push({ at: at + 500, fire: () => { this.spinTarget = 1; this.spinUntil = this.elapsed + 5000; this.options.onMotion(true); } });
        duration = Math.max(duration, 1500);
      }
      time += duration;
    }
    if (glass && glassHome) {
      this.tween(time, 600, (p) => { glass.position.set(glassHome.x, glassHome.y, glassHome.z + 0.14 * (1 - easeInOut(p))); this.setGlass(0.1 * p); });
    }
    this.moveCamera(views.overview, 900, time);
    this.events.push({ at: time + 900, fire: () => { this.playing = false; if (this.controls) this.controls.enabled = true; this.options.onStep("done"); } });
    this.invalidate();
  }

  /** Kullanıcı kontrolündeki fan hareketi (birkaç saniye döner, sonra kendiliğinden durur). */
  setMotion(on: boolean) {
    if (!this.build || !isCompleteBuild(this.build) || this.options.reducedMotion) on = false;
    this.spinTarget = on ? 1 : 0;
    this.spinUntil = on ? this.elapsed + 6000 : 0;
    this.options.onMotion(on);
    this.invalidate();
  }

  /** Parçayı GLB rotası boyunca ev konumuna getirir; toplam süreyi döndürür. */
  private enter(name: string, start: number, duration: number) {
    const node = this.nodes.get(name);
    const home = this.homes.get(name);
    if (!node || !home) return 0;
    const route = this.routes.get(name) ?? fallbackRoute;
    const bends = route.length - 2;
    const total = duration + bends * bendTime;
    const ease = bends > 0 ? easeInOut : easeOut;
    const place = (p: number) => {
      const [x, y, z] = routePoint(route, ease(p));
      node.position.set(home.x + x, home.y + y, home.z + z);
    };
    place(0);
    this.tween(start, total, place, () => { node.visible = true; });
    return total;
  }

  private tween(start: number, duration: number, apply: (progress: number) => void, begin?: () => void) {
    this.tweens.push({ start, duration, apply, begin });
  }

  private moveCamera(view: View, duration: number, start = 0) {
    const scale = Math.max(1, 0.95 / Math.max(this.camera.aspect, 0.1));
    const target = new THREE.Vector3(...view.target);
    const position = new THREE.Vector3(...view.position).sub(target).multiplyScalar(scale).add(target);
    if (duration === 0 || this.options.reducedMotion) {
      this.camera.position.copy(position);
      this.target.copy(target);
      this.controls?.target.copy(target);
      this.camera.lookAt(target);
      return;
    }
    const fromPosition = new THREE.Vector3();
    const fromTarget = new THREE.Vector3();
    this.tween(start, duration, (p) => {
      const k = easeInOut(p);
      this.camera.position.lerpVectors(fromPosition, position, k);
      this.target.lerpVectors(fromTarget, target, k);
      this.controls?.target.copy(this.target);
      this.camera.lookAt(this.target);
    }, () => { fromPosition.copy(this.camera.position); fromTarget.copy(this.target); });
  }

  private setGlass(opacity: number) {
    if (this.glass) this.glass.opacity = opacity;
    for (const fixture of this.glassFixtures) fixture.visible = opacity > 0.04;
  }

  private setVisible(name: string, visible: boolean) {
    const node = this.nodes.get(name);
    if (node) node.visible = visible;
  }

  private setRgb(level: number) {
    this.rgbLevel = level;
    for (const [name, material] of this.materials) {
      if (!name.startsWith("RGB_")) continue;
      const original = this.originals.get(name)!;
      const base = new THREE.Color(brandGlow[rgbTone[name] ?? 0]);
      if (this.rgbSweep >= 0) base.lerp(new THREE.Color(brandGlow[(rgbTone[name] + 1) % 3]), 0.5 + 0.5 * Math.sin(this.rgbSweep * 6 + (rgbTone[name] ?? 0)));
      material.emissive.copy(base);
      material.color.copy(level > 0 ? base : original.color).multiplyScalar(level > 0 ? 0.6 : 1);
      material.emissiveIntensity = Math.max(original.intensity, rgbIntensity) * level;
    }
    // Açık zeminde toplamalı (additive) parıltı görünmez/kirli durur; orada hafif normal karışım kullanılır.
    if (this.glow) this.glow.material.opacity = (this.lightStage ? 0.1 : 0.2) * level;
    if (this.rim) this.rim.intensity = (this.build?.caseColor === "WHITE" ? 0.22 : 0.45) * level;
  }

  /** Sahne zemini CSS semantik token'ından (--ltz-stage) okunur; açık temada açık zemin, gölge ve parıltı ona göre ayarlanır. */
  private updateBackground() {
    const background = new THREE.Color(0x0b0b0f);
    try {
      const style = getComputedStyle(this.options.host).backgroundColor;
      if (style) background.setStyle(style, THREE.SRGBColorSpace);
    } catch { /* hesaplanmış stil yoksa koyu sahne zemini kalır */ }
    this.scene.background = background;
    const hsl = { h: 0, s: 0, l: 0 };
    background.getHSL(hsl, THREE.SRGBColorSpace);
    this.lightStage = hsl.l > 0.5;
    if (this.glow) {
      this.glow.material.blending = this.lightStage ? THREE.NormalBlending : THREE.AdditiveBlending;
      this.glow.material.needsUpdate = true;
    }
    if (this.shadow) this.shadow.material.opacity = this.lightStage ? 0.75 : 1;
    this.setRgb(this.rgbLevel);
    this.invalidate();
  }

  private resize() {
    const { clientWidth: width, clientHeight: height } = this.options.host;
    if (!width || !height) return;
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
    this.composer?.setPixelRatio(this.renderer.getPixelRatio());
    this.composer?.setSize(width, height);
    this.bloom?.resolution.set(width / 2, height / 2);
    if (!this.playing) this.moveCamera(views.overview, 0);
    this.invalidate();
  }

  private invalidate() { this.dirty = true; this.schedule(); }

  /** Talep üzerine render: sahne durağansa animasyon döngüsü tamamen durur (boşta 0 rAF). */
  private needsFrame() {
    return this.dirty || this.playing || this.spin > 0 || this.spinTarget > 0 || this.rgbSweep >= 0 || this.interacting || this.damping;
  }

  private schedule() {
    if (this.disposed) return;
    const run = this.visible && document.visibilityState !== "hidden" && this.needsFrame();
    if (run === this.looping) return;
    this.looping = run;
    this.renderer.setAnimationLoop(run ? (time) => this.frame(time) : null);
    if (!run) { this.last = 0; this.tick = 0; this.warm = 0; this.samples = 0; this.sampleSum = 0; }
  }

  private frame(time: number) {
    const budget = 1000 / qualityProfiles[this.quality].fps;
    // Kare sınırı: yüksek tazeleme hızlı ekranlarda da profil FPS'i aşılmaz; kalan süre bir sonraki kareye taşınır.
    const since = time - this.tick;
    if (this.tick && since < budget - 1.5) return;
    this.tick = this.tick && since < budget * 2 ? this.tick + budget : time;
    const interval = this.last ? time - this.last : 0;
    // Montaj zamanı gerçek zamanı izler: yavaş cihazda (≈80–300 ms kare) ağır çekime düşmez; 250 ms üstü duraklamadır.
    const dt = this.last ? Math.min(250, interval) : 16;
    this.last = time;
    this.elapsed += dt;
    if (this.playing) this.advance();
    if (this.rgbSweep >= 0) {
      this.rgbSweep += dt / 1000;
      if (this.rgbSweep > 2.6) this.rgbSweep = -1;
      this.setRgb(this.rgbLevel);
    }
    if (this.spinTarget > 0 && this.spinUntil && this.elapsed > this.spinUntil) { this.spinTarget = 0; this.options.onMotion(false); }
    // Fanlar ~1 sn içinde durur; 0,01 altı dönüş gözle seçilmez, sahne çizimi bırakır.
    this.spin += (this.spinTarget - this.spin) * Math.min(1, dt / 450);
    if (this.spin > 0.01) for (const rotor of this.rotors) rotor.rotation.y -= this.spin * dt * 0.022;
    else this.spin = 0;
    this.damping = this.controls?.update() ?? false;
    const active = this.playing || this.spin > 0 || this.rgbSweep >= 0 || this.interacting || this.damping;
    if (active || this.dirty) {
      this.dirty = false;
      if (this.composer) this.composer.render();
      else this.renderer.render(this.scene, this.camera);
      if (this.adaptive && interval && active) this.sample(interval, budget);
    }
    this.schedule();
  }

  private sample(interval: number, budget: number) {
    // 1 sn üstü aralık sekme/sistem duraklamasıdır, cihaz hızını göstermez.
    if (interval > 1000) return;
    // Isınma kare sayısıyla değil bütçe cinsinden sayılır: yavaş cihazda (yazılım render) birkaç karede biter.
    if (this.warm < sampleWarmup) { this.warm += Math.min(4, Math.max(1, interval / budget)); return; }
    this.samples += 1;
    this.sampleSum += interval;
    const average = this.sampleSum / this.samples;
    // Bütçenin 3 katından yavaş kareler tam pencereyi beklemeden bir kademe düşürür.
    const severe = this.samples >= severeWindow && average > budget * severeFactor;
    if (!severe && this.samples < sampleWindow) return;
    this.samples = 0;
    this.sampleSum = 0;
    const next = qualityProfiles[this.quality].next;
    if (next && (severe || average > budget * slowFactor)) this.setQuality(next);
  }

  private advance() {
    for (const event of this.events) if (!event.fired && this.elapsed >= event.at) { event.fired = true; event.fire(); }
    for (const tween of this.tweens) {
      if (tween.done || this.elapsed < tween.start) continue;
      if (!tween.begun) { tween.begun = true; tween.begin?.(); }
      const progress = tween.duration ? clamp01((this.elapsed - tween.start) / tween.duration) : 1;
      tween.apply(progress);
      if (progress >= 1) tween.done = true;
    }
  }

  private disposeObject(object: THREE.Object3D) {
    const mesh = object as THREE.Mesh;
    if (!mesh.isMesh) return;
    mesh.geometry.dispose();
    for (const material of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
      for (const value of Object.values(material)) if (value instanceof THREE.Texture) value.dispose();
      material.dispose();
    }
  }

  dispose() {
    this.disposed = true;
    this.looping = false;
    this.renderer.setAnimationLoop(null);
    document.removeEventListener("visibilitychange", this.onVisibility);
    for (const observer of this.observers) observer.disconnect();
    this.controls?.dispose();
    this.scene.traverse((object) => this.disposeObject(object));
    this.environment?.dispose();
    this.bloom?.dispose();
    this.composer?.dispose();
    this.renderer.dispose();
    this.renderer.domElement.remove();
  }
}
