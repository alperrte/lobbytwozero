"""LTZ Atölyesi V1 — prosedürel tam donanımlı kasa (Blender 4.5, headless).

Çalıştırma (depo kökünden):
    blender -b --factory-startup -P docs-hardware/workshop/ltz_workshop_case.py -- \
        --out frontend/public/assets/hardware/workshop [--no-render]

Üretilenler:
    ltz-workshop-case.glb                 three.js sahnesi (adlandırılmış düğümler)
    ltz-workshop-fallback-<renk>.webp     WebGL olmayan / düşük güçlü cihaz için durağan görüntü

Tasarım kuralları:
    * Tamamen özgün geometri: üçüncü taraf logo, wordmark veya gerçek ürün modeli yok.
    * Üretici yalnız vurgu rengiyle anlatılır (CPU_Accent, GPU_Accent, Pump_Display).
    * Koordinatlar Blender Z-yukarı, metre. Cam yan panel -Y yönüne bakar; glTF dışa aktarımda
      -Y → +Z olur, yani cam three.js kamerasına bakar.
    * Fan rotorlarının dönme ekseni yerel Z'dir; glTF'te yerel Y olur.
    * Montaj rotası (ROUTES) Blender'da tanımlanır, düğüm extras'ına `ltz_entry` olarak yazılır ve three.js
      yalnız bu rotayı oynatır. Her parça gerçek bağlantı noktasına (ray, braket, soket, yuva) oturur; rotalar
      dışa aktarımdan önce BVH çakışma denetiminden geçer (çıktıda CHECK satırları).
    * Draw call bütçesi: her düğümün altındaki meshler malzemeye göre birleştirilir, aynı geometriler paylaşılır.
"""

import json
import math
import os
import sys
import tempfile

import bmesh
import bpy
import numpy as np
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


def arg(name, default=None):
    if name in ARGS:
        index = ARGS.index(name)
        if index + 1 < len(ARGS) and not ARGS[index + 1].startswith("--"):
            return ARGS[index + 1]
        return True
    return default


OUT = os.path.abspath(arg("--out", "frontend/public/assets/hardware/workshop"))
RENDER = not arg("--no-render", False)
os.makedirs(OUT, exist_ok=True)

# --------------------------------------------------------------------------- sahne
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.unit_settings.system = "METRIC"
MODEL = bpy.data.collections.new("LTZ_Workshop")
scene.collection.children.link(MODEL)
STAGE = bpy.data.collections.new("Stage")
scene.collection.children.link(STAGE)

# --------------------------------------------------------------------------- malzemeler
MATERIALS = {}


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(srgb_to_linear(int(value[i:i + 2], 16) / 255) for i in (0, 2, 4))


def material(name, color, metallic=0.0, roughness=0.5, emission=None, strength=0.0, alpha=1.0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*hex_rgb(color), 1)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*hex_rgb(emission), 1)
        bsdf.inputs["Emission Strength"].default_value = strength
    if alpha < 1:
        bsdf.inputs["Alpha"].default_value = alpha
        bsdf.inputs["Specular IOR Level"].default_value = 0.18
        mat.surface_render_method = "BLENDED"
    MATERIALS[name] = mat
    return mat


M = {
    "case": material("CaseBody", "#14161b", 0.55, 0.38),
    "mesh": material("CaseMesh", "#0b0c0f", 0.4, 0.55),
    "glass": material("Glass", "#c9d4e2", 0.0, 0.0, alpha=0.06),
    "pcb": material("PCB", "#16181d", 0.15, 0.62),
    "pcbTrace": material("PCB_Trace", "#2a2e36", 0.5, 0.4),
    "silver": material("MetalSilver", "#b8bec8", 1.0, 0.24),
    "darkMetal": material("MetalDark", "#2b2f37", 0.9, 0.32),
    "plastic": material("PlasticDark", "#1d2026", 0.0, 0.48),
    "socket": material("Socket", "#3a3f48", 0.6, 0.35),
    "cpuSub": material("CPU_Substrate", "#20402c", 0.1, 0.5),
    "ihs": material("CPU_IHS", "#cfd4db", 1.0, 0.18),
    "cpuAccent": material("CPU_Accent", "#e11d2e", 0.2, 0.35, emission="#e11d2e", strength=0.6),
    "ramSpreader": material("RAM_Heatspreader", "#2a2d34", 0.85, 0.3),
    "ramPcb": material("RAM_PCB", "#122015", 0.1, 0.6),
    "rgbRam": material("RGB_RAM", "#ffffff", 0.0, 0.3, emission="#ff2e88", strength=3.2),
    "rgbFan": material("RGB_Fan", "#ffffff", 0.0, 0.3, emission="#ff2e88", strength=3.2),
    "rgbGpu": material("RGB_GPU", "#ffffff", 0.0, 0.3, emission="#ff2e88", strength=3.2),
    "rgbPump": material("RGB_Pump", "#ffffff", 0.0, 0.3, emission="#ff2e88", strength=3.2),
    "rgbStrip": material("RGB_Strip", "#ffffff", 0.0, 0.3, emission="#ff2e88", strength=3.2),
    "pumpDisplay": material("Pump_Display", "#0d0f13", 0.0, 0.2, emission="#e11d2e", strength=1.6),
    "gpuShroud": material("GPU_Shroud", "#1b1e24", 0.6, 0.34),
    "gpuAccent": material("GPU_Accent", "#e11d2e", 0.3, 0.3, emission="#e11d2e", strength=0.9),
    "gpuBackplate": material("GPU_Backplate", "#262a31", 0.95, 0.28),
    "fanFrame": material("FanFrame", "#15171c", 0.0, 0.5),
    "fanBlade": material("FanBlade", "#2a2e36", 0.0, 0.32),
    "radiator": material("Radiator", "#101216", 0.7, 0.45),
    "tube": material("Tube", "#0e0f12", 0.0, 0.42),
    "cable": material("CableSleeve", "#17191e", 0.0, 0.6),
    "logo": material("LTZ_Logo", "#e11d2e", 0.0, 0.3, emission="#e11d2e", strength=3.0),
    "ssd": material("SSD_Heatsink", "#30343c", 0.9, 0.28),
    "ssdLabel": material("SSD_Label", "#e11d2e", 0.1, 0.4, emission="#e11d2e", strength=0.5),
    "psu": material("PSU", "#121418", 0.6, 0.4),
    "rubber": material("Rubber", "#08090b", 0.0, 0.8),
    "gold": material("Gold", "#c9a227", 1.0, 0.3),
}

# --------------------------------------------------------------------------- yardımcılar
ORIGINS = {}  # grup adı -> dünya orijini (dönmesiz gruplar)


def group(name, origin=(0, 0, 0), parent=None, rotation=(0, 0, 0)):
    empty = bpy.data.objects.new(name, None)
    empty.empty_display_size = 0.02
    MODEL.objects.link(empty)
    world = Vector(origin)
    if parent is not None:
        empty.parent = parent
        empty.location = world - Vector(ORIGINS.get(parent.name, (0, 0, 0)))
    else:
        empty.location = world
    empty.rotation_euler = rotation
    ORIGINS[name] = tuple(world)
    return empty


def finish(name, bm, mat, parent, local=False, smooth=False, origin=(0, 0, 0)):
    """bm köşeleri dünya (local=False) ya da ebeveyn (local=True) koordinatındadır."""
    if not local and parent is not None:
        offset = Vector(ORIGINS.get(parent.name, (0, 0, 0)))
        bmesh.ops.translate(bm, verts=bm.verts, vec=-offset)
    mesh = bpy.data.meshes.new(name)
    bm.normal_update()
    bm.to_mesh(mesh)
    bm.free()
    if smooth:
        mesh.shade_smooth()
    else:
        mesh.shade_flat()
    mesh.materials.append(mat)
    obj = bpy.data.objects.new(name, mesh)
    MODEL.objects.link(obj)
    if parent is not None:
        obj.parent = parent
    obj.location = origin
    return obj


def box(name, mn, mx, mat, parent=None, bevel=0.0, local=False):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    size = Vector(mx) - Vector(mn)
    center = (Vector(mx) + Vector(mn)) / 2
    bmesh.ops.scale(bm, vec=size, verts=bm.verts)
    bmesh.ops.translate(bm, vec=center, verts=bm.verts)
    if bevel > 0:
        bmesh.ops.bevel(bm, geom=list(bm.edges), offset=min(bevel, min(size) * 0.45), segments=2,
                        affect="EDGES", profile=0.5)
    return finish(name, bm, mat, parent, local)


def boxes(name, items, mat, parent=None, local=False):
    """Tek nesnede çok sayıda kutu (ızgara çubukları, radyatör kanatları)."""
    bm = bmesh.new()
    for mn, mx in items:
        part = bmesh.ops.create_cube(bm, size=1.0)["verts"]
        size = Vector(mx) - Vector(mn)
        bmesh.ops.scale(bm, vec=size, verts=part)
        bmesh.ops.translate(bm, vec=(Vector(mx) + Vector(mn)) / 2, verts=part)
    return finish(name, bm, mat, parent, local)


def orient(axis):
    """Yerel Z'yi verilen eksene çeviren matris."""
    return {
        "X": Matrix.Rotation(math.radians(90), 4, "Y"),
        "-X": Matrix.Rotation(math.radians(-90), 4, "Y"),
        "Y": Matrix.Rotation(math.radians(-90), 4, "X"),
        "-Y": Matrix.Rotation(math.radians(90), 4, "X"),
        "Z": Matrix.Identity(4),
    }[axis]


def cylinder(name, center, radius, depth, mat, parent=None, axis="Z", segments=40, local=False, radius2=None):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=segments, radius1=radius,
                          radius2=radius if radius2 is None else radius2, depth=depth)
    bmesh.ops.transform(bm, matrix=Matrix.Translation(center) @ orient(axis), verts=bm.verts)
    return finish(name, bm, mat, parent, local, smooth=True)


def torus(name, center, major, minor, mat, parent=None, axis="Z", segments=40, ring=6, local=False):
    bm = bmesh.new()
    rows = []
    for i in range(segments):
        a = 2 * math.pi * i / segments
        row = []
        for j in range(ring):
            b = 2 * math.pi * j / ring
            r = major + minor * math.cos(b)
            row.append(bm.verts.new((r * math.cos(a), r * math.sin(a), minor * math.sin(b))))
        rows.append(row)
    for i in range(segments):
        for j in range(ring):
            bm.faces.new((rows[i][j], rows[(i + 1) % segments][j],
                          rows[(i + 1) % segments][(j + 1) % ring], rows[i][(j + 1) % ring]))
    bmesh.ops.transform(bm, matrix=Matrix.Translation(center) @ orient(axis), verts=bm.verts)
    return finish(name, bm, mat, parent, local, smooth=True)


def square_ring(name, half, inner, depth, mat, parent, segments=32):
    """Fan çerçevesi: kare dış kenar, dairesel iç delik (yerel, eksen Z)."""
    bm = bmesh.new()
    top_o, top_i, bot_o, bot_i = [], [], [], []
    for i in range(segments):
        a = 2 * math.pi * i / segments
        d = Vector((math.cos(a), math.sin(a)))
        outer = d / max(abs(d.x), abs(d.y)) * half
        for z, lo, li in ((depth / 2, top_o, top_i), (-depth / 2, bot_o, bot_i)):
            lo.append(bm.verts.new((outer.x, outer.y, z)))
            li.append(bm.verts.new((d.x * inner, d.y * inner, z)))
    for i in range(segments):
        n = (i + 1) % segments
        bm.faces.new((top_o[i], top_o[n], top_i[n], top_i[i]))
        bm.faces.new((bot_i[i], bot_i[n], bot_o[n], bot_o[i]))
        bm.faces.new((bot_o[i], bot_o[n], top_o[n], top_o[i]))
        bm.faces.new((top_i[i], top_i[n], bot_i[n], bot_i[i]))
    return finish(name, bm, mat, parent, local=True)


def blades(name, count, r0, r1, pitch, mat, parent, thickness=0.0016):
    """Eğimli, hafif kıvrık fan kanatları (yerel, eksen Z)."""
    bm = bmesh.new()
    radial, angular = 5, 7
    span = 2 * math.pi / count * 0.78
    for k in range(count):
        base = 2 * math.pi * k / count
        for side in (1, -1):
            grid = []
            for i in range(radial + 1):
                t = i / radial
                r = r0 + (r1 - r0) * t
                row = []
                sweep = 0.35 * t  # uç kısım dönüş yönüne kıvrılır
                for j in range(angular + 1):
                    s = j / angular
                    a = base + sweep + span * (s - 0.5)
                    z = pitch * (0.5 - s) + side * thickness / 2
                    row.append(bm.verts.new((r * math.cos(a), r * math.sin(a), z)))
                grid.append(row)
            for i in range(radial):
                for j in range(angular):
                    quad = (grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1])
                    bm.faces.new(quad if side > 0 else tuple(reversed(quad)))
    return finish(name, bm, mat, parent, local=True, smooth=True)


def fan(name, center, axis, parent=None, size=0.12, depth=0.025, rgb=True, frame=True):
    """Kasa fanı (kare çerçeve + 4 vida) ya da çerçevesiz GPU fanı (soğutucu örtüsünün deliğine oturur)."""
    rotation = orient(axis).to_euler()
    root = group(name, center, parent, rotation)
    if frame:
        square_ring(name + "_Frame", size / 2, size * 0.47, depth, M["fanFrame"], root)
    rotor = bpy.data.objects.new(name + "_Rotor", None)
    MODEL.objects.link(rotor)
    rotor.parent = root
    # göbek, kapak ve kanatlar tek malzeme: rotor tek draw call
    cylinder(name + "_Hub", Vector((0, 0, 0)), size * 0.17, depth * 0.62, M["fanBlade"], rotor, local=True, segments=24)
    cylinder(name + "_Cap", Vector((0, 0, depth * 0.31)), size * 0.12, 0.0015, M["fanBlade"], rotor, local=True, segments=24)
    blades(name + "_Blades", 9, size * 0.16, size * 0.455, depth * 0.42, M["fanBlade"], rotor)
    if rgb:
        torus(name + "_RGB", Vector((0, 0, depth * 0.36)), size * 0.465, 0.0022, M["rgbFan"], root, local=True)
        if frame:
            torus(name + "_RGBBack", Vector((0, 0, -depth * 0.36)), size * 0.465, 0.0016, M["rgbFan"], root, local=True)
    if frame:
        # dört köşe vida: ön yüzde (yerel +Z), kasa rayına/ızgarasına oturur
        for sx in (-1, 1):
            for sy in (-1, 1):
                cylinder(name + f"_Screw{sx}{sy}", Vector((sx * size * 0.43, sy * size * 0.43, depth / 2)), 0.003,
                         0.002, M["fanFrame"], root, local=True, segments=8)
    return root


def plate_yz(name, xa, xb, ya, yb, za, zb, holes, mat, parent=None):
    """YZ düzleminde dikdörtgen delikli levha (arka panel). Delikler (y0, y1, z0, z1); levha hücrelere bölünür,
    delik içindeki hücreler atlanır. Boolean gerektirmez, temiz düşük poligonlu sonuç verir."""
    ys = sorted({ya, yb, *[v for h in holes for v in h[:2]]})
    zs = sorted({za, zb, *[v for h in holes for v in h[2:]]})
    items = []
    for i in range(len(ys) - 1):
        for j in range(len(zs) - 1):
            cy, cz = (ys[i] + ys[i + 1]) / 2, (zs[j] + zs[j + 1]) / 2
            if any(h[0] < cy < h[1] and h[2] < cz < h[3] for h in holes):
                continue
            items.append(((xa, ys[i], zs[j]), (xb, ys[i + 1], zs[j + 1])))
    return boxes(name, items, mat, parent)


def cut_holes(obj, cutters):
    """Boolean (EXACT) fark: delikler kaynak geometride açılır, dışa aktarılan mesh gerçek deliklidir."""
    holder = bpy.data.collections.new(obj.name + "_Cutters")
    scene.collection.children.link(holder)
    for cutter in cutters:
        for coll in list(cutter.users_collection):
            coll.objects.unlink(cutter)
        holder.objects.link(cutter)
    mod = obj.modifiers.new("Holes", "BOOLEAN")
    mod.operation = "DIFFERENCE"
    mod.operand_type = "COLLECTION"
    mod.collection = holder
    mod.solver = "EXACT"
    bpy.context.view_layer.update()
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = bpy.data.meshes.new_from_object(evaluated)
    obj.modifiers.remove(mod)
    old = obj.data
    obj.data = mesh
    bpy.data.meshes.remove(old)
    for cutter in cutters:
        data = cutter.data
        bpy.data.objects.remove(cutter)
        bpy.data.meshes.remove(data)
    bpy.data.collections.remove(holder)
    return obj


def curve_mesh(name, points, radius, mat, parent=None, resolution=12):
    """Bezier eğri → borulu mesh (AIO hortumu, kablo)."""
    data = bpy.data.curves.new(name + "_Curve", type="CURVE")
    data.dimensions = "3D"
    data.bevel_depth = radius
    data.bevel_resolution = 3
    data.resolution_u = resolution
    data.use_fill_caps = True
    spline = data.splines.new("BEZIER")
    spline.bezier_points.add(len(points) - 1)
    for bp, p in zip(spline.bezier_points, points):
        bp.co = p
        bp.handle_left_type = bp.handle_right_type = "AUTO"
    tmp = bpy.data.objects.new(name + "_Tmp", data)
    scene.collection.objects.link(tmp)
    bpy.context.view_layer.update()
    evaluated = tmp.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = bpy.data.meshes.new_from_object(evaluated)
    bpy.data.objects.remove(tmp)
    bpy.data.curves.remove(data)
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bpy.data.meshes.remove(mesh)
    return finish(name, bm, mat, parent, smooth=True)


BRAND_DIR = os.path.abspath(arg("--brand", "frontend/public/assets/brand"))


def logo_material(name, file_name, glow):
    """Onaylı LTZ logo PNG'si (alfa kanallı) doku olarak GLB'ye gömülür; metinle sahte logo üretilmez."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = nodes["Principled BSDF"]
    image = nodes.new("ShaderNodeTexImage")
    image.image = bpy.data.images.load(os.path.join(BRAND_DIR, file_name))
    image.interpolation = "Linear"
    links = mat.node_tree.links
    links.new(image.outputs["Color"], bsdf.inputs["Base Color"])
    links.new(image.outputs["Alpha"], bsdf.inputs["Alpha"])
    links.new(image.outputs["Color"], bsdf.inputs["Emission Color"])
    bsdf.inputs["Emission Strength"].default_value = glow
    bsdf.inputs["Roughness"].default_value = 0.35
    mat.surface_render_method = "BLENDED"
    MATERIALS[name] = mat
    return mat


def logo_plane(name, center, width, height, mat, parent, facing="-Y"):
    """UV'li tek dörtgen; ön yüzü `facing` yönüne bakar (cam tarafı -Y)."""
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    hw, hh = width / 2, height / 2
    corners = [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)]
    verts = [bm.verts.new((x, 0, z)) for x, z in corners]
    face = bm.faces.new(verts)
    for loop, (u, v) in zip(face.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
        loop[uv].uv = (u, v)
    if facing != "-Y":
        raise ValueError(facing)
    bmesh.ops.translate(bm, verts=bm.verts, vec=Vector(center))
    return finish(name, bm, mat, parent)


# --------------------------------------------------------------------------- ölçüler
X0, X1 = -0.23, 0.23     # arka ↔ ön
Y0, Y1 = -0.115, 0.115   # cam ↔ anakart tablası
Z0, Z1 = 0.0, 0.50
T = 0.006

# --------------------------------------------------------------------------- kasa
case = group("Case", (0, 0, 0))
box("Case_Tray", (X0, Y1 - T, Z0), (X1, Y1, Z1), M["case"], case, bevel=0.003)
box("Case_Bottom", (X0, Y0, Z0), (X1, Y1, Z0 + T), M["case"], case, bevel=0.002)
box("Case_TopFrontRail", (X1 - 0.03, Y0, Z1 - T), (X1, Y1, Z1), M["case"], case, bevel=0.002)
box("Case_TopRearRail", (X0, Y0, Z1 - T), (X0 + 0.022, Y1, Z1), M["case"], case, bevel=0.002)
box("Case_TopSideRail", (X0 + 0.022, Y0, Z1 - T), (X1 - 0.03, Y0 + 0.018, Z1), M["case"], case)
box("Case_TopTrayRail", (X0 + 0.022, Y1 - 0.018, Z1 - T), (X1 - 0.03, Y1, Z1), M["case"], case)
boxes("Case_TopMesh", [((X0 + 0.022 + i * 0.0105, Y0 + 0.018, Z1 - 0.004), (X0 + 0.022 + i * 0.0105 + 0.006, Y1 - 0.018, Z1 - 0.001))
                       for i in range(39)], M["mesh"], case)
# ön panel: çerçeve + dikey mesh + LTZ kırmızısı RGB şerit
box("Case_FrontFrameL", (X1 - T, Y0, Z0), (X1, Y0 + 0.02, Z1), M["case"], case, bevel=0.002)
box("Case_FrontFrameR", (X1 - T, Y1 - 0.02, Z0), (X1, Y1, Z1), M["case"], case, bevel=0.002)
box("Case_FrontFrameB", (X1 - T, Y0, Z0), (X1, Y1, Z0 + 0.03), M["case"], case, bevel=0.002)
boxes("Case_FrontMesh", [((X1 - 0.004, Y0 + 0.02 + i * 0.0095, Z0 + 0.03), (X1 - 0.001, Y0 + 0.02 + i * 0.0095 + 0.0055, Z1 - T))
                         for i in range(20)], M["mesh"], case)
box("Case_FrontStrip", (X1 - 0.001, Y0 + 0.004, Z0 + 0.04), (X1 + 0.0015, Y0 + 0.008, Z1 - 0.02), M["rgbStrip"], case)
# ön fan rayları: 3 × 120 mm fan bu iki dikey raya vidalanır (vidalar yerel ±0.0516 → y -0.0556 / 0.0476).
# Fan ön yüzü x=0.2175'te rayın arka yüzüne oturur; ön mesh (x 0.226) ile arada 8.5 mm hava boşluğu kalır.
FRONT_FAN_X, FRONT_FAN_Y = 0.205, -0.004
for name, (ya, yb) in {"Case_FrontFanRailA": (-0.066, -0.046), "Case_FrontFanRailB": (0.038, 0.058)}.items():
    box(name, (0.2175, ya, 0.04), (X1 - T + 0.0005, yb, 0.41), M["darkMetal"], case, bevel=0.001)
# arka panel: tek levha; arka fan, anakart I/O ve dikey GPU braketi için gerçek açıklıklar
REAR_FAN = Vector((X0 + T + 0.0125, 0.0, 0.365))          # fan ön yüzü levhanın iç yüzüne (x=-0.224) yaslanır
REAR_FAN_HOLE = (-0.056, 0.056, REAR_FAN.z - 0.056, REAR_FAN.z + 0.056)
REAR_IO_HOLE = (0.068, 0.1, 0.305, 0.425)
REAR_GPU_HOLE = (-0.09, -0.028, 0.105, 0.28)
plate_yz("Case_RearPanel", X0, X0 + T, Y0, Y1, Z0, Z1, [REAR_FAN_HOLE, REAR_IO_HOLE, REAR_GPU_HOLE], M["case"], case)
boxes("Case_RearFanGrille", [((X0 + 0.001, REAR_FAN_HOLE[0], z), (X0 + 0.004, REAR_FAN_HOLE[1], z + 0.0018))
                             for z in np.arange(REAR_FAN_HOLE[2] + 0.004, REAR_FAN_HOLE[3] - 0.002, 0.008)], M["mesh"], case)
boxes("Case_RearFanGrilleV", [((X0 + 0.001, y, REAR_FAN_HOLE[2]), (X0 + 0.004, y + 0.0018, REAR_FAN_HOLE[3]))
                              for y in (-0.0289, -0.0009, 0.0271)], M["mesh"], case)
# I/O kalkanı (levha kalınlığı içinde) + port açıklıkları
box("Case_IOShield", (X0 + 0.0015, REAR_IO_HOLE[0], REAR_IO_HOLE[2]), (X0 + 0.0055, REAR_IO_HOLE[1], REAR_IO_HOLE[3]), M["darkMetal"], case)
boxes("Case_IOPorts", [((X0 + 0.001, 0.074 + c * 0.011, 0.312 + r * 0.019), (X0 + 0.0016, 0.082 + c * 0.011, 0.324 + r * 0.019))
                       for r in range(6) for c in range(2)], M["plastic"], case)
# yatay genişleme yuvası kapakları (dikey GPU kullanıldığı için kapalı)
boxes("Case_RearSlots", [((X0 - 0.001, -0.02, 0.122 + i * 0.0205), (X0 + 0.0008, 0.06, 0.122 + i * 0.0205 + 0.016))
                         for i in range(7)], M["darkMetal"], case)
# üst radyatör rayları: 360 mm radyatör tavana bu iki raya asılır
for name, (ya, yb) in {"Case_TopRadRailA": (-0.064, -0.056), "Case_TopRadRailB": (0.056, 0.064)}.items():
    box(name, (X0 + 0.022, ya, 0.493), (X1 - 0.03, yb, Z1 - 0.004), M["darkMetal"], case)
# köşe dikmeleri (cam çerçevesi)
for name, (xa, xb) in {"Case_PostRear": (X0, X0 + 0.012), "Case_PostFront": (X1 - 0.014, X1 - T)}.items():
    box(name, (xa, Y0, Z0 + T), (xb, Y0 + 0.012, Z1 - T), M["case"], case, bevel=0.002)
# ayaklar
for i, x in enumerate((X0 + 0.04, X1 - 0.04)):
    for j, y in enumerate((Y0 + 0.03, Y1 - 0.03)):
        cylinder(f"Case_Foot{i}{j}", Vector((x, y, -0.006)), 0.014, 0.012, M["rubber"], case, segments=24)
# kablo yönetim lastikleri (anakart sağında)
for i, z in enumerate((0.17, 0.27, 0.37)):
    box(f"Case_Grommet{i}", (0.05, Y1 - T - 0.001, z - 0.035), (0.068, Y1 - T, z + 0.035), M["rubber"], case, bevel=0.004)

# cam yan panel (ayrı düğüm: montajda çıkarılıp sonunda geri takılır)
glass = group("Glass", (0, Y0 + 0.002, 0.25))
box("Glass_Panel", (X0 + 0.004, Y0, Z0 + 0.008), (X1 - 0.008, Y0 + 0.004, Z1 - 0.004), M["glass"], glass, bevel=0.001)
for i, (x, z) in enumerate(((X0 + 0.02, 0.03), (X0 + 0.02, 0.47), (X1 - 0.025, 0.03), (X1 - 0.025, 0.47))):
    cylinder(f"Glass_Thumb{i}", Vector((x, Y0 - 0.002, z)), 0.006, 0.004, M["silver"], glass, axis="Y", segments=20)

# PSU örtüsü + LTZ yazısı
shroud = group("Shroud", (0, 0, 0.055))
# örtü ön fanlardan 1 cm geride biter (alt fan x=0.1925'ten başlar, montajda 2.5 mm boşluk)
box("Shroud_Body", (X0 + T, Y0 + 0.012, Z0 + T), (0.182, Y1 - T, 0.105), M["case"], shroud, bevel=0.003)
boxes("Shroud_Vents", [((0.10 + i * 0.008, Y0 + 0.011, 0.03), (0.104 + i * 0.008, Y0 + 0.0125, 0.09)) for i in range(9)], M["mesh"], shroud)
logo_plane("Shroud_Logo", (-0.06, Y0 + 0.0108, 0.062), 0.12, 0.12 * 147 / 480, logo_material("LTZ_Wordmark", "ltz-wordmark.png", 0.8), shroud)
box("Shroud_LogoLine", (-0.13, Y0 + 0.0112, 0.032), (0.01, Y0 + 0.0122, 0.0335), M["logo"], shroud)

psu = group("PSU", (-0.14, 0.0, 0.055))
box("PSU_Body", (X0 + 0.01, -0.075, 0.012), (-0.06, 0.085, 0.098), M["psu"], psu, bevel=0.003)
cylinder("PSU_Fan", Vector((-0.145, 0.005, 0.0105)), 0.055, 0.002, M["mesh"], psu, segments=40)

# --------------------------------------------------------------------------- anakart
MB_X0, MB_X1, MB_Z0, MB_Z1 = -0.215, 0.029, 0.12, 0.425
MB_FACE = 0.096  # PCB ön yüzü (cama bakan)
mb = group("Motherboard", ((MB_X0 + MB_X1) / 2, MB_FACE, (MB_Z0 + MB_Z1) / 2))
SOCKET = Vector((-0.085, MB_FACE, 0.33))
DIMM_X = [-0.040, -0.031, -0.020, -0.011]
TEXTURE_DIR = tempfile.mkdtemp(prefix="ltz-workshop-")


def srgb(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def save_image(name, rgb, non_color=False):
    h, w, _ = rgb.shape
    image = bpy.data.images.new(name, w, h, alpha=False)
    if non_color:
        image.colorspace_settings.name = "Non-Color"
    rgba = np.ones((h, w, 4), np.float32)
    rgba[..., :3] = np.clip(rgb, 0, 1)
    image.pixels.foreach_set(rgba.ravel())
    image.filepath_raw = os.path.join(TEXTURE_DIR, name + ".png")
    image.file_format = "PNG"
    image.save()
    return image


def motherboard_textures(w=512, h=640):
    """PCB yüzeyi: bakır yollar, vialar, lehim pedleri, serigrafi çerçeveleri ve montaj delikleri.
    Renk + yükseklikten türetilen normal map (tangent space, OpenGL +Y). Satır 0 = kartın alt kenarı (v=0)."""
    rng = np.random.default_rng(20261108)
    width, height_m = MB_X1 - MB_X0, MB_Z1 - MB_Z0
    color = np.empty((h, w, 3), np.float32)
    color[:] = srgb("#17191e")
    height = np.zeros((h, w), np.float32)

    def u(x):
        return int(np.clip(round((x - MB_X0) / width * w), 0, w - 1))

    def v(z):
        return int(np.clip(round((z - MB_Z0) / height_m * h), 0, h - 1))

    def paint(r0, r1, c0, c1, rgb, level):
        color[r0:r1, c0:c1] = rgb
        height[r0:r1, c0:c1] = np.maximum(height[r0:r1, c0:c1], level)

    def outline(x0, z0, x1, z1, rgb=srgb("#6f7580"), level=0.18, t=2):
        a, b, c, d = u(x0), u(x1), v(z0), v(z1)
        for r0, r1, c0, c1 in ((c, c + t, a, b), (d - t, d, a, b), (c, d, a, a + t), (c, d, b - t, b)):
            paint(r0, r1, c0, c1, rgb, level)

    trace, via, pad = srgb("#252a31"), srgb("#8a7a45"), srgb("#b39a4a")
    for _ in range(260):  # Manhattan yollar
        x, y = int(rng.integers(6, w - 6)), int(rng.integers(6, h - 6))
        for _ in range(int(rng.integers(2, 5))):
            if rng.random() < 0.5:
                nx = int(np.clip(x + rng.integers(-150, 150), 4, w - 4))
                a, b = sorted((x, nx))
                paint(y, y + 2, a, b + 2, trace, 0.32)
                x = nx
            else:
                ny = int(np.clip(y + rng.integers(-150, 150), 4, h - 4))
                a, b = sorted((y, ny))
                paint(a, b + 2, x, x + 2, trace, 0.32)
                y = ny
        paint(y - 2, y + 3, x - 2, x + 3, via, 0.55)
    # bileşen serigrafileri (gerçek konumlarla hizalı)
    outline(SOCKET.x - 0.036, SOCKET.z - 0.036, SOCKET.x + 0.036, SOCKET.z + 0.036)
    outline(DIMM_X[0] - 0.006, 0.26, DIMM_X[-1] + 0.006, 0.41)
    outline(MB_X0 + 0.042, 0.248, -0.006, 0.264)
    outline(MB_X0 + 0.042, 0.184, -0.086, 0.199)
    outline(-0.156, 0.266, -0.066, 0.296)
    outline(-0.154, 0.206, -0.066, 0.239)
    outline(-0.039, 0.131, 0.024, 0.204)
    outline(MB_X0 + 0.004, 0.135, MB_X0 + 0.04, 0.215)
    # serigrafi etiket çizgileri (metin değil, soyut işaretler)
    for _ in range(36):
        x, y = int(rng.integers(10, w - 40)), int(rng.integers(10, h - 10))
        for k in range(int(rng.integers(2, 6))):
            paint(y, y + 2, x + k * 5, x + k * 5 + 3, srgb("#5d636d"), 0.12)
    # lehim pedi sıraları (header ve SATA çevresi)
    for x0, z0, cols, rows in ((0.003, 0.123, 8, 2), (0.021, 0.212, 2, 6), (MB_X0 + 0.012, 0.222, 3, 2), (-0.06, 0.123, 5, 2)):
        for c in range(cols):
            for r in range(rows):
                cx, cz = u(x0 + c * 0.0026), v(z0 + r * 0.0026)
                paint(cz, cz + 3, cx, cx + 3, pad, 0.6)
    # ATX montaj delikleri (metal halka)
    yy, xx = np.mgrid[0:h, 0:w]
    for x, z in ((MB_X0 + 0.0065, MB_Z1 - 0.0065), (MB_X0 + 0.0065, 0.2), (-0.004, MB_Z1 - 0.0065), (MB_X1 - 0.0065, 0.28),
                 (MB_X1 - 0.0065, MB_Z0 + 0.0065), (MB_X0 + 0.0065, MB_Z0 + 0.0065), (-0.085, MB_Z0 + 0.0065)):
        d = np.hypot(xx - u(x), yy - v(z))
        ring = (d < 9) & (d >= 5)
        color[ring] = srgb("#a9adb4")
        height[ring] = 0.7
        color[d < 5] = srgb("#050506")
        height[d < 5] = 0.0
    # yumuşatılmış yükseklik → normal
    soft = height.copy()
    for axis in (0, 1):
        soft = (np.roll(soft, 1, axis) + soft * 2 + np.roll(soft, -1, axis)) / 4
    gy, gx = np.gradient(soft)
    normal = np.dstack((-gx * 2.2, -gy * 2.2, np.ones_like(gx)))
    normal /= np.linalg.norm(normal, axis=2, keepdims=True)
    return save_image("MB_SurfaceColor", color), save_image("MB_SurfaceNormal", normal * 0.5 + 0.5, non_color=True)


def motherboard_material():
    mat = bpy.data.materials.new("MB_Surface")
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = nodes["Principled BSDF"]
    color_image, normal_image = motherboard_textures()
    color_node = nodes.new("ShaderNodeTexImage")
    color_node.image = color_image
    normal_tex = nodes.new("ShaderNodeTexImage")
    normal_tex.image = normal_image
    normal_map = nodes.new("ShaderNodeNormalMap")
    links.new(color_node.outputs["Color"], bsdf.inputs["Base Color"])
    links.new(normal_tex.outputs["Color"], normal_map.inputs["Color"])
    links.new(normal_map.outputs["Normal"], bsdf.inputs["Normal"])
    bsdf.inputs["Metallic"].default_value = 0.15
    bsdf.inputs["Roughness"].default_value = 0.55
    MATERIALS[mat.name] = mat
    return mat


def uv_face_minus_y(name, x0, x1, y, z0, z1, mat, parent):
    """-Y'ye bakan UV'li dörtgen: u = x, v = z (anakart yüzü)."""
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    face = bm.faces.new([bm.verts.new((x, y, z)) for x, z in ((x0, z0), (x1, z0), (x1, z1), (x0, z1))])
    for loop, coords in zip(face.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
        loop[uv].uv = coords
    return finish(name, bm, mat, parent)


box("MB_PCB", (MB_X0, MB_FACE, MB_Z0), (MB_X1, MB_FACE + 0.002, MB_Z1), M["pcb"], mb, bevel=0.0008)
uv_face_minus_y("MB_Surface", MB_X0 + 0.0004, MB_X1 - 0.0004, MB_FACE - 0.0001, MB_Z0 + 0.0004, MB_Z1 - 0.0004, motherboard_material(), mb)
# I/O bloğu: örtü + arka panele uzanan port uçları (kasa I/O kalkanına oturur)
box("MB_IOShroud", (MB_X0, MB_FACE - 0.026, 0.31), (MB_X0 + 0.04, MB_FACE, MB_Z1 - 0.005), M["darkMetal"], mb, bevel=0.003)
box("MB_IOAccent", (MB_X0 + 0.004, MB_FACE - 0.0265, 0.32), (MB_X0 + 0.007, MB_FACE - 0.026, 0.41), M["logo"], mb)
boxes("MB_IOPorts", [((X0 + T, 0.072, 0.312 + r * 0.019), (MB_X0, 0.094, 0.326 + r * 0.019)) for r in range(6)], M["silver"], mb)
# VRM: üst soğutucu + kanatlar, sol soğutucu, bobinler
box("MB_VRMTop", (MB_X0 + 0.045, MB_FACE - 0.018, 0.39), (-0.06, MB_FACE, 0.418), M["darkMetal"], mb, bevel=0.003)
boxes("MB_VRMFins", [((MB_X0 + 0.05 + i * 0.006, MB_FACE - 0.0185, 0.392), (MB_X0 + 0.052 + i * 0.006, MB_FACE - 0.018, 0.416))
                     for i in range(16)], M["silver"], mb)
box("MB_VRMLeft", (-0.168, MB_FACE - 0.016, 0.30), (-0.150, MB_FACE, 0.385), M["darkMetal"], mb, bevel=0.003)
boxes("MB_VRMLeftFins", [((-0.1685, MB_FACE - 0.0165, 0.304 + i * 0.006), (-0.1495, MB_FACE - 0.016, 0.306 + i * 0.006)) for i in range(13)], M["silver"], mb)
boxes("MB_Chokes", [((-0.146 + i * 0.0105, MB_FACE - 0.006, 0.38), (-0.138 + i * 0.0105, MB_FACE, 0.388)) for i in range(8)], M["socket"], mb)
# soket + tutucu çerçeve + kol
box("MB_Socket", (SOCKET.x - 0.028, MB_FACE - 0.003, SOCKET.z - 0.03), (SOCKET.x + 0.028, MB_FACE, SOCKET.z + 0.03), M["socket"], mb, bevel=0.001)
boxes("MB_SocketFrame", [((SOCKET.x - 0.033, MB_FACE - 0.0035, SOCKET.z - 0.033), (SOCKET.x + 0.033, MB_FACE, SOCKET.z - 0.031)),
                         ((SOCKET.x - 0.033, MB_FACE - 0.0035, SOCKET.z + 0.031), (SOCKET.x + 0.033, MB_FACE, SOCKET.z + 0.033)),
                         ((SOCKET.x - 0.033, MB_FACE - 0.0035, SOCKET.z - 0.031), (SOCKET.x - 0.031, MB_FACE, SOCKET.z + 0.031)),
                         ((SOCKET.x + 0.031, MB_FACE - 0.0035, SOCKET.z - 0.031), (SOCKET.x + 0.033, MB_FACE, SOCKET.z + 0.031))], M["silver"], mb)
box("MB_SocketLever", (SOCKET.x + 0.034, MB_FACE - 0.003, SOCKET.z - 0.03), (SOCKET.x + 0.036, MB_FACE - 0.001, SOCKET.z + 0.02), M["silver"], mb)
for i, x in enumerate(DIMM_X):
    box(f"MB_DIMM{i + 1}", (x - 0.0028, MB_FACE - 0.007, 0.265), (x + 0.0028, MB_FACE, 0.405), M["plastic"], mb)
    box(f"MB_DIMMClip{i + 1}", (x - 0.003, MB_FACE - 0.009, 0.401), (x + 0.003, MB_FACE, 0.408), M["silver"], mb)
# güç ve veri konnektörleri
box("MB_ATX24", (0.016, MB_FACE - 0.012, 0.27), (0.026, MB_FACE, 0.34), M["plastic"], mb)
boxes("MB_ATX24Pins", [((0.0165, MB_FACE - 0.0122, 0.272 + i * 0.0057), (0.0255, MB_FACE - 0.012, 0.2755 + i * 0.0057)) for i in range(12)], M["darkMetal"], mb)
box("MB_EPS8", (MB_X0 + 0.01, MB_FACE - 0.01, 0.415), (MB_X0 + 0.035, MB_FACE, 0.423), M["plastic"], mb)
boxes("MB_SATA", [((0.021, MB_FACE - 0.008, 0.14 + i * 0.016), (0.028, MB_FACE, 0.152 + i * 0.016)) for i in range(4)], M["plastic"], mb)
boxes("MB_Headers", [((0.003 + i * 0.006, MB_FACE - 0.005, 0.122), (0.007 + i * 0.006, MB_FACE, 0.128)) for i in range(3)]
      + [((MB_X0 + 0.012, MB_FACE - 0.005, 0.222), (MB_X0 + 0.02, MB_FACE, 0.226)), ((-0.06, MB_FACE - 0.005, 0.122), (-0.047, MB_FACE, 0.127)),
         ((-0.03, MB_FACE - 0.005, 0.421), (-0.022, MB_FACE, 0.424))], M["plastic"], mb)
# genişleme yuvaları, yonga seti, M.2 kapakları, CMOS pili, ses kapasitörleri
box("MB_PCIe16", (MB_X0 + 0.045, MB_FACE - 0.008, 0.252), (-0.01, MB_FACE, 0.26), M["silver"], mb)
box("MB_PCIe16Latch", (-0.012, MB_FACE - 0.009, 0.2505), (-0.006, MB_FACE, 0.2615), M["plastic"], mb)
box("MB_PCIe4", (MB_X0 + 0.045, MB_FACE - 0.007, 0.188), (-0.09, MB_FACE, 0.195), M["plastic"], mb)
box("MB_Chipset", (-0.035, MB_FACE - 0.012, 0.135), (0.02, MB_FACE, 0.2), M["darkMetal"], mb, bevel=0.004)
box("MB_ChipsetAccent", (-0.03, MB_FACE - 0.0125, 0.19), (0.015, MB_FACE - 0.012, 0.193), M["logo"], mb)
box("MB_M2Slot", (-0.152, MB_FACE - 0.002, 0.27), (-0.07, MB_FACE, 0.292), M["plastic"], mb)
box("MB_M2Cover2", (-0.15, MB_FACE - 0.005, 0.21), (-0.07, MB_FACE, 0.235), M["darkMetal"], mb, bevel=0.0015)
cylinder("MB_CMOS", Vector((-0.05, MB_FACE - 0.0015, 0.215)), 0.009, 0.003, M["silver"], mb, axis="Y", segments=20)
for i, (x, z) in enumerate(((-0.2, 0.15), (-0.19, 0.15), (-0.2, 0.163), (-0.19, 0.163))):
    cylinder(f"MB_Cap{i}", Vector((x, MB_FACE - 0.004, z)), 0.0035, 0.008, M["gold"], mb, axis="Y", segments=10)

# --------------------------------------------------------------------------- CPU
cpu = group("CPU", (SOCKET.x, MB_FACE - 0.004, SOCKET.z))
box("CPU_Substrate", (SOCKET.x - 0.02, MB_FACE - 0.004, SOCKET.z - 0.02), (SOCKET.x + 0.02, MB_FACE - 0.0028, SOCKET.z + 0.02), M["cpuSub"], cpu)
box("CPU_IHS", (SOCKET.x - 0.0165, MB_FACE - 0.0072, SOCKET.z - 0.0165), (SOCKET.x + 0.0165, MB_FACE - 0.004, SOCKET.z + 0.0165), M["ihs"], cpu, bevel=0.0012)
box("CPU_AccentBar", (SOCKET.x - 0.013, MB_FACE - 0.0075, SOCKET.z - 0.012), (SOCKET.x + 0.013, MB_FACE - 0.0071, SOCKET.z - 0.0085), M["cpuAccent"], cpu)
cylinder("CPU_Mark", Vector((SOCKET.x - 0.012, MB_FACE - 0.0074, SOCKET.z + 0.012)), 0.0018, 0.0004, M["cpuAccent"], cpu, axis="Y", segments=16)

# --------------------------------------------------------------------------- RAM
for i, x in enumerate(DIMM_X):
    stick = group(f"RAM_{i + 1}", (x, MB_FACE - 0.03, 0.335))
    box(f"RAM_{i + 1}_PCB", (x - 0.0007, MB_FACE - 0.038, 0.268), (x + 0.0007, MB_FACE - 0.004, 0.402), M["ramPcb"], stick)
    for side in (-1, 1):
        box(f"RAM_{i + 1}_Spreader{side}", (x + side * 0.0007 - (0.0016 if side < 0 else 0), MB_FACE - 0.04, 0.266),
            (x + side * 0.0007 + (0.0016 if side > 0 else 0), MB_FACE - 0.008, 0.404), M["ramSpreader"], stick, bevel=0.0005)
    box(f"RAM_{i + 1}_RGB", (x - 0.0022, MB_FACE - 0.047, 0.267), (x + 0.0022, MB_FACE - 0.0395, 0.403), M["rgbRam"], stick, bevel=0.001)

# --------------------------------------------------------------------------- M.2 SSD
ssd = group("SSD", (-0.111, MB_FACE - 0.004, 0.281))
box("SSD_Board", (-0.15, MB_FACE - 0.003, 0.271), (-0.071, MB_FACE - 0.0018, 0.291), M["ramPcb"], ssd)
box("SSD_Heatsink", (-0.151, MB_FACE - 0.008, 0.2705), (-0.07, MB_FACE - 0.003, 0.2915), M["ssd"], ssd, bevel=0.0012)
boxes("SSD_Fins", [((-0.146 + i * 0.006, MB_FACE - 0.0086, 0.2725), (-0.144 + i * 0.006, MB_FACE - 0.008, 0.2895)) for i in range(12)], M["silver"], ssd)
box("SSD_Label", (-0.077, MB_FACE - 0.0085, 0.274), (-0.073, MB_FACE - 0.008, 0.288), M["ssdLabel"], ssd)

# --------------------------------------------------------------------------- GPU (dikey montaj, fanlar cama bakar)
# Kart dikey yükselticide: braket arka paneldeki dikey açıklığa (REAR_GPU_HOLE) vidalı, PCIe pinleri
# PSU örtüsü üstündeki yükseltici yuvasına girer. Katmanlar (cam → anakart): örtü, fanlar, soğutucu kanatları,
# ısı boruları, taban plakası, PCB, arka plaka.
G_X0, G_X1, G_Z0, G_Z1, G_Y0, G_Y1 = -0.222, 0.095, 0.125, 0.25, -0.086, -0.032
G_FAN_X, G_FAN_Z, G_HOLE_R = (-0.163, -0.0635, 0.036), 0.1875, 0.0465
gpu = group("GPU", ((G_X0 + G_X1) / 2, (G_Y0 + G_Y1) / 2, (G_Z0 + G_Z1) / 2))
shroud_obj = box("GPU_Shroud", (G_X0 + 0.004, G_Y0, G_Z0), (G_X1, G_Y0 + 0.016, G_Z1), M["gpuShroud"], gpu, bevel=0.004)
cut_holes(shroud_obj, [cylinder(f"GPU_Cut{i}", Vector((x, G_Y0 + 0.008, G_FAN_Z)), G_HOLE_R, 0.04, M["gpuShroud"], gpu, axis="Y", segments=40)
                       for i, x in enumerate(G_FAN_X)])
boxes("GPU_ShroudRidges", [((x - 0.0016, G_Y0 - 0.0012, G_Z0 + 0.016), (x + 0.0016, G_Y0, G_Z1 - 0.016)) for x in (-0.113, -0.0135)], M["gpuShroud"], gpu)
boxes("GPU_Fins", [((G_X0 + 0.012 + i * 0.0055, G_Y0 + 0.016, G_Z0 + 0.006), (G_X0 + 0.0135 + i * 0.0055, -0.046, G_Z1 - 0.006))
                   for i in range(int((G_X1 - 0.008 - (G_X0 + 0.012)) / 0.0055))], M["darkMetal"], gpu)
box("GPU_Body", (G_X0 + 0.004, -0.046, G_Z0 + 0.004), (G_X1 - 0.004, -0.040, G_Z1 - 0.004), M["radiator"], gpu, bevel=0.0015)
for i, y in enumerate((-0.064, -0.058, -0.052)):
    cylinder(f"GPU_Heatpipe{i}", Vector(((G_X0 + G_X1) / 2 + 0.004, y, G_Z1 - 0.0035)), 0.0025, G_X1 - G_X0 - 0.03, M["gold"], gpu, axis="X", segments=10)
box("GPU_PCB", (G_X0 + 0.004, -0.040, G_Z0), (G_X1 - 0.004, -0.0384, G_Z1 - 0.002), M["pcb"], gpu)
box("GPU_Fingers", (G_X0 + 0.05, -0.0401, 0.118), (G_X0 + 0.135, -0.0383, G_Z0), M["gold"], gpu)
box("GPU_Backplate", (G_X0 + 0.004, G_Y1 - 0.004, G_Z0), (G_X1, G_Y1, G_Z1), M["gpuBackplate"], gpu, bevel=0.002)
boxes("GPU_BackplateVents", [((0.02 + i * 0.006, G_Y1 - 0.0002, G_Z0 + 0.03), (0.0235 + i * 0.006, G_Y1 + 0.0004, G_Z1 - 0.03)) for i in range(10)], M["mesh"], gpu)
# braket: arka paneldeki dikey açıklığa vidalanır, ekran çıkışları dışa bakar
box("GPU_Bracket", (G_X0 - 0.0015, G_Y0 + 0.002, G_Z0 - 0.004), (G_X0 + 0.0025, G_Y1, G_Z1 + 0.004), M["silver"], gpu)
boxes("GPU_Ports", [((G_X0 - 0.0018, -0.072, G_Z0 + 0.012 + i * 0.026), (G_X0 - 0.0015, -0.048, G_Z0 + 0.024 + i * 0.026)) for i in range(4)], M["plastic"], gpu)
# vurgu şeritleri (üretici rengi)
box("GPU_AccentTop", (G_X0 + 0.01, G_Y0 - 0.0012, G_Z1 - 0.012), (G_X1 - 0.008, G_Y0, G_Z1 - 0.008), M["gpuAccent"], gpu)
box("GPU_AccentBottom", (G_X0 + 0.01, G_Y0 - 0.0012, G_Z0 + 0.008), (G_X0 + 0.11, G_Y0, G_Z0 + 0.011), M["gpuAccent"], gpu)
box("GPU_AccentEnd", (G_X1 - 0.0015, G_Y0 + 0.002, G_Z0 + 0.012), (G_X1 + 0.0002, G_Y1 - 0.006, G_Z1 - 0.012), M["gpuAccent"], gpu)
box("GPU_RGBEdge", (G_X0 + 0.02, G_Y0 + 0.004, G_Z1 - 0.0005), (G_X1 - 0.02, G_Y0 + 0.012, G_Z1 + 0.0015), M["rgbGpu"], gpu)
box("GPU_PowerPlug", (0.0, G_Y0 + 0.02, G_Z1), (0.02, G_Y1 - 0.01, G_Z1 + 0.008), M["plastic"], gpu)
boxes("GPU_PowerPins", [((0.0025 + c * 0.0052, -0.0625 + r * 0.008, G_Z1 + 0.008), (0.0055 + c * 0.0052, -0.0585 + r * 0.008, G_Z1 + 0.0084))
                        for c in range(3) for r in range(2)], M["darkMetal"], gpu)
for i, x in enumerate(G_FAN_X):
    fan(f"GPU_Fan_{i + 1}", Vector((x, G_Y0 + 0.006, G_FAN_Z)), "-Y", gpu, size=0.092, depth=0.012, rgb=True, frame=False)
# dikey yükseltici (kasa aksesuarı, PSU örtüsü üstünde): taban plakası, PCIe yuvası, anakarta giden şerit kablo
box("Case_RiserPlate", (G_X0 + 0.02, -0.06, 0.105), (G_X0 + 0.17, -0.025, 0.109), M["darkMetal"], shroud, bevel=0.001)
box("Case_RiserSlot", (G_X0 + 0.045, -0.0435, 0.109), (G_X0 + 0.14, -0.035, 0.121), M["plastic"], shroud)
box("Case_RiserCable", (-0.12, -0.025, 0.105), (-0.095, 0.09, 0.1065), M["cable"], shroud)

# --------------------------------------------------------------------------- AIO
PUMP = Vector((SOCKET.x, MB_FACE - 0.0075, SOCKET.z))
pump = group("AIO_Pump", (PUMP.x, PUMP.y - 0.015, PUMP.z))
cylinder("AIO_PumpBase", Vector((PUMP.x, PUMP.y - 0.004, PUMP.z)), 0.035, 0.008, M["silver"], pump, axis="Y", segments=48)
cylinder("AIO_PumpBody", Vector((PUMP.x, PUMP.y - 0.017, PUMP.z)), 0.032, 0.02, M["gpuShroud"], pump, axis="Y", segments=48)
torus("AIO_PumpRGB", Vector((PUMP.x, PUMP.y - 0.0275, PUMP.z)), 0.029, 0.0022, M["rgbPump"], pump, axis="Y")
cylinder("AIO_PumpDisplay", Vector((PUMP.x, PUMP.y - 0.0275, PUMP.z)), 0.025, 0.0012, M["pumpDisplay"], pump, axis="Y", segments=48)
logo_plane("AIO_PumpLogo", (PUMP.x, PUMP.y - 0.0286, PUMP.z), 0.03, 0.03 * 181 / 256, logo_material("LTZ_Mark", "ltz-mark.png", 1.2), pump)
# rakorlar pompanın üst kenarında, yukarı bakar (hortumlar VRM soğutucusunun önünden çıkar)
FITTINGS = [Vector((PUMP.x - 0.012, PUMP.y - 0.02, PUMP.z + 0.032)), Vector((PUMP.x + 0.012, PUMP.y - 0.02, PUMP.z + 0.032))]
for i, f in enumerate(FITTINGS):
    cylinder(f"AIO_Fitting{i}", f, 0.0065, 0.012, M["darkMetal"], pump, segments=20)

RAD_X0, RAD_X1 = -0.2025, 0.1925
radiator = group("AIO_Radiator", ((RAD_X0 + RAD_X1) / 2, 0.0, 0.478))
box("AIO_RadCore", (RAD_X0 + 0.02, -0.06, 0.466), (RAD_X1 - 0.02, 0.06, 0.491), M["radiator"], radiator)
boxes("AIO_RadFins", [((RAD_X0 + 0.022 + i * 0.0045, -0.061, 0.4655), (RAD_X0 + 0.023 + i * 0.0045, 0.061, 0.4915))
                      for i in range(78)], M["darkMetal"], radiator)
box("AIO_RadTankA", (RAD_X0, -0.062, 0.464), (RAD_X0 + 0.02, 0.062, 0.493), M["gpuShroud"], radiator, bevel=0.003)
box("AIO_RadTankB", (RAD_X1 - 0.02, -0.062, 0.464), (RAD_X1, 0.062, 0.493), M["gpuShroud"], radiator, bevel=0.003)
for i, y in enumerate((-0.012, 0.012)):
    cylinder(f"AIO_RadPort{i}", Vector((RAD_X1 - 0.01, y - 0.02, 0.46)), 0.006, 0.01, M["darkMetal"], radiator, segments=20)

# Hortumlar: pompa rakorundan dik çıkar, RAM üstünü (z>0.408) ve üst fanların altını (z<0.4395) izleyerek
# radyatörün ön haznesindeki portlara (z 0.455) aşağıdan girer.
tubes = group("AIO_Tubes", (0.06, 0.0, 0.4))
TUBE_TOP = FITTINGS[0].z + 0.007  # rakor üstüne 1 mm boşlukla oturur
for i, (fit, run_z, port_y) in enumerate(((FITTINGS[0], 0.42, -0.032), (FITTINGS[1], 0.4155, -0.008))):
    curve_mesh(f"AIO_Tube{i}", [Vector((fit.x, fit.y, TUBE_TOP)),
                                Vector((fit.x, fit.y, run_z - 0.03)),
                                Vector((fit.x + 0.03, fit.y - 0.02, run_z)),
                                Vector((0.09, port_y + 0.012, run_z)),
                                Vector((0.15, port_y, run_z)),
                                Vector((RAD_X1 - 0.01, port_y, run_z + 0.012)),
                                Vector((RAD_X1 - 0.01, port_y, 0.454))], 0.0055, M["tube"], tubes)

for i, x in enumerate((-0.1285, -0.0075, 0.1135)):  # 1 mm aralık
    fan(f"Fan_Top_{i + 1}", Vector((x, 0.0, 0.451)), "Z")  # üst yüz 0.4635 < hazne 0.464
for i, z in enumerate((0.104, 0.225, 0.346)):  # 1 mm aralıkla raya dizilir
    fan(f"Fan_Front_{i + 1}", Vector((FRONT_FAN_X, FRONT_FAN_Y, z)), "X")
fan("Fan_Rear", REAR_FAN, "-X")

# --------------------------------------------------------------------------- kablolar
cables = group("Cables", (0.04, 0.09, 0.3))
for i in range(8):
    z = 0.274 + i * 0.0085
    curve_mesh(f"Cable_ATX{i}", [Vector((0.026, MB_FACE - 0.006, z)), Vector((0.042, MB_FACE - 0.012, z + 0.002)),
                                 Vector((0.059, Y1 - T - 0.004, 0.27 + i * 0.002))], 0.0016, M["cable"], cables, resolution=8)
for i in range(4):
    x = MB_X0 + 0.013 + i * 0.006
    curve_mesh(f"Cable_EPS{i}", [Vector((x, MB_FACE - 0.008, 0.423)), Vector((x - 0.004, MB_FACE - 0.012, 0.44)),
                                 Vector((x - 0.002, Y1 - T - 0.004, 0.452))], 0.0015, M["cable"], cables, resolution=8)
for i in range(6):
    x = 0.002 + i * 0.0032
    curve_mesh(f"Cable_GPU{i}", [Vector((x, -0.06 + 0.0, G_Z1 + 0.008)), Vector((x + 0.01, -0.02, G_Z1 + 0.03)),
                                 Vector((0.05 + i * 0.002, 0.06, 0.3)), Vector((0.059, Y1 - T - 0.004, 0.36 + i * 0.002))],
               0.0015, M["cable"], cables, resolution=10)


# --------------------------------------------------------------------------- montaj rotaları
# glTF koordinatında (x ön, y yukarı, z cama doğru) ev konumuna göre ofsetler; ilk nokta başlangıç, son nokta 0.
# Blender karşılığı: glTF (a, b, c) = Blender (a, -c, b). Sıra = montaj sırası (schemas/workshop.ts adımları).
ROUTES = {
    # I/O bloğu arka dikmenin önünden geçsin diye 12 mm önde gelir, sonra I/O kalkanına doğru kayar
    "Motherboard": [(0.012, 0.004, 0.3), (0.012, 0.004, 0), (0, 0, 0)],
    "CPU": [(0, 0.04, 0.16), (0, 0, 0.02), (0, 0, 0)],
    "RAM_1": [(0, 0, 0.13), (0, 0, 0)], "RAM_2": [(0, 0, 0.13), (0, 0, 0)],
    "RAM_3": [(0, 0, 0.13), (0, 0, 0)], "RAM_4": [(0, 0, 0.13), (0, 0, 0)],
    "SSD": [(0, 0, 0.12), (0, 0, 0)],
    # braket dikmeyi, pinler yükselticiyi aşsın: önde ve yukarıda gelir, braketi açıklığa iter, yuvaya iner
    "GPU": [(0.008, 0.03, 0.22), (0.008, 0.03, 0), (0, 0.03, 0), (0, 0, 0)],
    # radyatör 3 cm aşağıda içeri girer, tavandaki raylara yukarı doğru oturur
    "AIO_Radiator": [(0, -0.03, 0.2), (0, -0.03, 0), (0, 0, 0)],
    "AIO_Pump": [(0, 0, 0.16), (0, 0, 0)],
    "AIO_Tubes": [(0, 0, 0.06), (0, 0, 0)],
    # üst fanlar radyatörün altına cam tarafından, son yükseklikte kayar (hortumların üstünden)
    "Fan_Top_1": [(0, 0, 0.2), (0, 0, 0)], "Fan_Top_2": [(0, 0, 0.2), (0, 0, 0)], "Fan_Top_3": [(0, 0, 0.2), (0, 0, 0)],
    # ön fanlar raydan 8 mm geride içeri girer, sonra öne kayıp raya oturur (ön paneli asla geçmez)
    "Fan_Front_1": [(-0.008, 0, 0.2), (-0.008, 0, 0), (0, 0, 0)],
    "Fan_Front_2": [(-0.008, 0, 0.2), (-0.008, 0, 0), (0, 0, 0)],
    "Fan_Front_3": [(-0.008, 0, 0.2), (-0.008, 0, 0), (0, 0, 0)],
    # arka fan 1 cm içeride gelir, arka panelin iç yüzüne yaslanır
    "Fan_Rear": [(0.01, 0, 0.2), (0.01, 0, 0), (0, 0, 0)],
    # kablolar son adımda konnektör takma hareketiyle (2 cm) gelir: EPS ucu arka fanın arkasında kalır
    "Cables": [(0, 0, 0.02), (0, 0, 0)],
}
STATIC_ROOTS = ("Case", "Shroud", "PSU")


def to_blender(offset):
    a, b, c = offset
    return Vector((a, -c, b))


def tree_of(roots, offset=Vector()):
    verts, polys = [], []
    for root in roots:
        for obj in [root, *root.children_recursive]:
            if obj.type != "MESH":
                continue
            mw = obj.matrix_world
            base = len(verts)
            verts.extend((mw @ v.co) + offset for v in obj.data.vertices)
            polys.extend([base + i for i in p.vertices] for p in obj.data.polygons)
    return BVHTree.FromPolygons(verts, polys) if polys else None


DIAG = "--diag" in sys.argv


def tree_of_obj(obj):
    mw = obj.matrix_world
    polys = [list(p.vertices) for p in obj.data.polygons]
    return BVHTree.FromPolygons([mw @ v.co for v in obj.data.vertices], polys) if polys else None


def check_routes():
    """Her parça rotası boyunca (son 12 mm oturma payı hariç) kasa + önceden takılmış parçalarla kesişmemeli."""
    bpy.context.view_layer.update()
    placed = [bpy.data.objects[name] for name in STATIC_ROOTS]
    failures = 0
    for name, path in ROUTES.items():
        node = bpy.data.objects[name]
        world = tree_of(placed)
        points = [to_blender(p) for p in path]
        hits, samples = 0, 0
        for a, b in zip(points, points[1:]):
            steps = max(1, int((b - a).length / 0.004))
            for k in range(steps + 1):
                offset = a.lerp(b, k / steps)
                if offset.length < 0.012:
                    continue
                samples += 1
                moving = tree_of([node], offset)
                if moving and world and moving.overlap(world):
                    hits += 1
                    if DIAG and hits <= 3:
                        culprits = sorted({o.name for r in placed for o in [r, *r.children_recursive]
                                           if o.type == "MESH" and (t := tree_of_obj(o)) and moving.overlap(t)})
                        print(f"  DIAG {name} @ {tuple(round(v, 3) for v in offset)}: {culprits}")
        failures += hits > 0
        print(f"CHECK route {name}: {'OK' if hits == 0 else f'COLLISION x{hits}'} ({samples} örnek)")
        placed.append(node)
    print(f"CHECK routes {'PASS' if failures == 0 else f'FAIL ({failures})'}")


def check_final_clearance(pairs):
    """Son konumda birbirine değmemesi gereken çiftler (hortum ↔ RAM/fan, fan ↔ panel)."""
    bpy.context.view_layer.update()
    for a, b in pairs:
        ta, tb = tree_of([bpy.data.objects[a]]), tree_of([bpy.data.objects[b]])
        n = len(ta.overlap(tb)) if ta and tb else 0
        culprits = ""
        if n and DIAG:
            ob = [o for o in [bpy.data.objects[b], *bpy.data.objects[b].children_recursive] if o.type == "MESH"]
            oa = [o for o in [bpy.data.objects[a], *bpy.data.objects[a].children_recursive] if o.type == "MESH"]
            culprits = sorted({f"{x.name}/{y.name}" for x in oa for y in ob
                               if (tx := tree_of_obj(x)) and (ty := tree_of_obj(y)) and tx.overlap(ty)})
        state = "OK" if n == 0 else ("EXPECTED (PCIe kenar konnektörü yükseltici yuvasında)" if (a, b) == ("GPU", "Shroud")
                                     else f"OVERLAP {n}")
        print(f"CHECK final {a} ↔ {b}: {state} {culprits}")


check_routes()
check_final_clearance([("AIO_Tubes", f"RAM_{i}") for i in range(1, 5)]
                      + [("AIO_Tubes", n) for n in ("Fan_Top_1", "Fan_Top_2", "Fan_Top_3", "Motherboard", "AIO_Radiator")]
                      + [(f"Fan_Front_{i}", "Shroud") for i in range(1, 4)] + [("Fan_Rear", "Motherboard"), ("GPU", "Shroud"),
                                                                              ("GPU", "Motherboard"), ("AIO_Pump", "RAM_1")])
for name, path in ROUTES.items():
    bpy.data.objects[name]["ltz_entry"] = json.dumps([[round(v, 4) for v in p] for p in path])


# --------------------------------------------------------------------------- draw call bütçesi
def join_by_material(parent):
    """Bir düğümün doğrudan mesh çocuklarını malzemeye göre tek meshte birleştirir (alt düğümler korunur:
    fan kökü, rotor, cam ve montaj düğümleri ayrı kalır)."""
    groups = {}
    for child in list(parent.children):
        if child.type == "MESH":
            groups.setdefault(child.data.materials[0].name, []).append(child)
    for mat_name, objs in groups.items():
        if len(objs) < 2:
            continue
        bm = bmesh.new()
        for obj in objs:
            data = obj.data.copy()
            data.transform(obj.matrix_basis)
            bm.from_mesh(data)
            bpy.data.meshes.remove(data)
        mesh = bpy.data.meshes.new(f"{parent.name}_{mat_name}")
        bm.to_mesh(mesh)
        bm.free()
        mesh.materials.append(bpy.data.materials[mat_name])
        merged = bpy.data.objects.new(mesh.name, mesh)
        MODEL.objects.link(merged)
        merged.parent = parent
        for obj in objs:
            data = obj.data
            bpy.data.objects.remove(obj)
            if data.users == 0:
                bpy.data.meshes.remove(data)
    for child in parent.children:
        if child.type == "EMPTY":
            join_by_material(child)


def share_geometry():
    """Yerel koordinatta özdeş meshler (fanlar, RAM'ler) tek glTF mesh'i paylaşır."""
    seen, shared = {}, 0
    for obj in [o for o in MODEL.objects if o.type == "MESH"]:
        coords = np.empty(len(obj.data.vertices) * 3, np.float32)
        obj.data.vertices.foreach_get("co", coords)
        key = (obj.data.materials[0].name, len(obj.data.polygons), np.round(coords, 5).tobytes())
        if key in seen and seen[key] is not obj.data:
            old = obj.data
            obj.data = seen[key]
            if old.users == 0:
                bpy.data.meshes.remove(old)
            shared += 1
        else:
            seen[key] = obj.data
    return shared


for root in [o for o in MODEL.objects if o.parent is None]:
    join_by_material(root)
print("SHARED", share_geometry())
meshes = [o for o in MODEL.objects if o.type == "MESH"]
print("BUDGET objects", len(meshes), "unique meshes", len({o.data.name for o in meshes}),
      "triangles", sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in meshes))


# --------------------------------------------------------------------------- dışa aktarım
def export_glb():
    bpy.ops.object.select_all(action="DESELECT")
    for obj in MODEL.objects:
        obj.select_set(True)
    path = os.path.join(OUT, "ltz-workshop-case.glb")
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_apply=True,
                              export_yup=True, export_texcoords=True, export_normals=True,
                              export_materials="EXPORT", export_cameras=False, export_lights=False,
                              export_extras=True, export_animations=False)
    print("GLB", path, os.path.getsize(path))


export_glb()

# --------------------------------------------------------------------------- yedek render
# WebGL yoksa gösterilen durağan görsel. Saydam arka plan + gölge yakalayıcı zemin: aynı dosya açık ve koyu
# sahnede doğru görünür (sahne zemini CSS token'ından gelir). Kasa rengi temadan bağımsızdır.
CASE_COLORS = {  # gövde, ızgara, metalik, pürüzlülük, kablo
    "black": ("#14161b", "#0b0c0f", 0.55, 0.38, "#17191e"),
    "white": ("#c4c7ce", "#b3b6be", 0.05, 0.5, "#c9ccd2"),
    "graphite": ("#3b3f47", "#24272d", 0.55, 0.4, "#17191e"),
    "red": ("#7c1520", "#2a0b0f", 0.45, 0.4, "#17191e"),
}


def setup_render():
    world = bpy.data.worlds.new("StageWorld")
    world.use_nodes = True
    # dünya ışığı çok zayıf: gölge yakalayıcı yalnız alan ışıklarının temas gölgesini yakalar, zemin kenarı görünmez
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (*hex_rgb("#0b0b0f"), 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.35
    scene.world = world
    cam_data = bpy.data.cameras.new("Cam")
    cam_data.lens = 46
    cam = bpy.data.objects.new("Cam", cam_data)
    STAGE.objects.link(cam)
    cam.location = (0.52, -0.98, 0.46)
    direction = Vector((0.0, 0.0, 0.24)) - cam.location
    cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    for name, loc, energy, size, color in (
            ("Key", (0.9, -0.8, 1.1), 70, 0.9, "#ffffff"),
            ("Rim", (-0.9, 0.5, 0.9), 35, 0.6, "#ff2e88"),
            ("Fill", (0.2, -1.2, 0.2), 20, 1.2, "#c9d1e0"),
            ("Inner", (0.0, -0.02, 0.32), 2.0, 0.25, "#ffffff")):
        light_data = bpy.data.lights.new(name, "AREA")
        light_data.energy = energy
        light_data.size = size
        light_data.color = hex_rgb(color)
        # cam üzerindeki büyük yansımalar iç donanımı örtmesin
        light_data.specular_factor = 0.12 if name in ("Key", "Fill") else 1.0
        light = bpy.data.objects.new(name, light_data)
        STAGE.objects.link(light)
        light.location = loc
        light.rotation_euler = (Vector((0, 0, 0.25)) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    floor = bpy.data.meshes.new("Floor")
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=6.0)
    bm.to_mesh(floor)
    bm.free()
    floor_obj = bpy.data.objects.new("Floor", floor)
    floor_obj.location = (0, 0, -0.012)
    floor_obj.is_shadow_catcher = True
    STAGE.objects.link(floor_obj)
    scene.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for backend in ("OPTIX", "CUDA", "HIP", "ONEAPI", "METAL"):
        try:
            prefs.compute_device_type = backend
            prefs.get_devices()
            if any(d.type == backend for d in prefs.devices):
                for d in prefs.devices:
                    d.use = d.type == backend
                scene.cycles.device = "GPU"
                print("RENDER device", backend)
                break
        except TypeError:
            continue
    scene.cycles.samples = 96
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Base Contrast"
    scene.render.resolution_x = 1200
    scene.render.resolution_y = 900
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "WEBP"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.quality = 82


if RENDER:
    setup_render()
    body = MATERIALS["CaseBody"].node_tree.nodes["Principled BSDF"]
    grille = MATERIALS["CaseMesh"].node_tree.nodes["Principled BSDF"]
    # WebGL sahnesindeki cam ile aynı koyu ton (GlassRuntime); açık cam rengi Cycles'ta süt gibi bir perde üretir
    MATERIALS["Glass"].node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (*hex_rgb("#0e1016"), 1)
    cable = MATERIALS["CableSleeve"].node_tree.nodes["Principled BSDF"]
    for key, (color, mesh_color, metallic, roughness, cable_color) in CASE_COLORS.items():
        body.inputs["Base Color"].default_value = (*hex_rgb(color), 1)
        body.inputs["Metallic"].default_value = metallic
        body.inputs["Roughness"].default_value = roughness
        grille.inputs["Base Color"].default_value = (*hex_rgb(mesh_color), 1)
        cable.inputs["Base Color"].default_value = (*hex_rgb(cable_color), 1)
        scene.render.filepath = os.path.join(OUT, f"ltz-workshop-fallback-{key}.webp")
        bpy.ops.render.render(write_still=True)
        print("RENDER", scene.render.filepath)
