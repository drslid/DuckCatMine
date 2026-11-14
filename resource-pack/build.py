#!/usr/bin/env python3
"""Build DuckCatMine's original, mod-free horror assets. Python stdlib + FFmpeg/Vorbis only."""
from __future__ import annotations
import argparse, base64, hashlib, html, json, math, random, shutil, struct, subprocess, tempfile, wave, zipfile, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VERSION = "1.3.0"
FORMAT = [97, 1]  # Minecraft Java 26.3, verified against its version.json and Mojang release notes.
ROSTER = {
    "spitter": ("Cracheur d’acide", (83, 106, 62), (215, 244, 82)),
    "jumper": ("Bondisseur", (109, 84, 73), (248, 186, 92)),
    "crawler": ("Rampant", (111, 104, 91), (238, 224, 177)),
    "stalker": ("Traqueur décharné", (145, 150, 137), (240, 230, 208)),
    "bloated": ("Boursouflé", (82, 107, 100), (189, 215, 110)),
    "breaker": ("Briseur", (103, 89, 83), (255, 158, 100)),
    "abomination": ("Abomination", (92, 70, 77), (235, 72, 44)),
    "nemesis": ("Némésis", (176, 160, 160), (236, 52, 36)),
    "smiler": ("Souriant", (138, 132, 120), (255, 246, 214)),
}
CLOTH = {"spitter": (61, 74, 52), "jumper": (88, 64, 52), "crawler": (72, 76, 84), "stalker": (96, 92, 82), "bloated": (70, 88, 96),
         "breaker": (52, 70, 104), "abomination": (46, 40, 44), "nemesis": (24, 24, 27), "smiler": (54, 44, 44)}

def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

def png(path: Path, pixels: list[tuple[int, int, int, int]], size=None):
    size = size or int(len(pixels) ** 0.5)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    rows = b"".join(b"\0" + bytes(c for px in pixels[y*size:(y+1)*size] for c in px) for y in range(size))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))

def texture(path: Path, base, eye, seed: int, face=False, kind=""):
    """Original 32 px skin: mottled shading, veins, bruises and scars; the face adds eyes, a torn mouth and teeth."""
    rng = random.Random(seed)
    coarse = [[rng.randrange(-22, 23) for _ in range(9)] for _ in range(9)]
    def smooth(x, y):
        fx, fy = x / 4, y / 4; ix, iy = int(fx), int(fy); tx, ty = fx - ix, fy - iy
        a = coarse[iy][ix] * (1 - tx) + coarse[iy][ix + 1] * tx; b = coarse[iy + 1][ix] * (1 - tx) + coarse[iy + 1][ix + 1] * tx
        return a * (1 - ty) + b * ty
    veins = {(int(16 + 12 * math.sin(t * .37 + seed)), int(t)) for t in range(32)} | {(int(t), int(9 + 6 * math.cos(t * .5 + seed))) for t in range(32)}
    bruise = (rng.randrange(6, 26), rng.randrange(6, 26), rng.randrange(3, 6))
    pixels = []
    for y in range(32):
        for x in range(32):
            delta = smooth(x, y) + rng.randrange(-8, 9)
            color = [max(0, min(255, c + delta)) for c in base]
            if (x, y) in veins: color = [int(c * .62) for c in color]; color[2] = min(255, color[2] + 12)
            if (x - bruise[0]) ** 2 + (y - bruise[1]) ** 2 < bruise[2] ** 2: color = [int(color[0] * .7 + 40), int(color[1] * .55), int(color[2] * .7 + 20)]
            if (x*3+y*5+seed) % 53 < 1: color = [58, 39, 36]
            if face:
                if kind == "smiler":
                    if 6 <= y <= 12 and (4 <= x <= 12 or 19 <= x <= 27): color = [12, 10, 10]
                    if 8 <= y <= 10 and (7 <= x <= 9 or 22 <= x <= 24): color = list(eye)
                    if 17 <= y <= 28 and abs(x - 15.5) <= 14 - max(0, (y - 22)) * 1.4: color = [20, 8, 10]
                    if y in (18, 27) and 3 <= x <= 28 and x % 3 != 0: color = list(eye)
                    if 18 <= y <= 27 and x in (2, 29) : color = [120, 20, 24]
                elif kind == "nemesis":
                    if 7 <= y <= 11 and (6 <= x <= 12): color = [20, 16, 16]
                    if 8 <= y <= 10 and 8 <= x <= 10: color = list(eye)
                    if 7 <= y <= 12 and 19 <= x <= 26: color = [90, 60, 64]
                    if y % 3 == 0 and 18 <= x <= 27 and 5 <= y <= 14: color = [40, 20, 22]
                    if 19 <= y <= 25 and 6 <= x <= 26: color = [52, 14, 18]
                    if (y in (19, 25)) and 6 <= x <= 26 and x % 2 == 0: color = [222, 214, 186]
                    if x in (15, 16) and 2 <= y <= 30 and y % 2: color = [70, 30, 34]
                else:
                    if 6 <= y <= 11 and (5 <= x <= 11 or 20 <= x <= 26): color = [24, 22, 20]
                    if 8 <= y <= 10 and (8 <= x <= 10 or 21 <= x <= 23): color = list(eye)
                    if 18 <= y <= 27 and 6 <= x <= 26: color = [42, 21, 24]
                    if (19 <= y <= 21 or 25 <= y <= 27) and 8 <= x <= 24 and x % 4 < 2: color = [210, 198, 158]
                    if x in (16, 17) and 11 <= y <= 17: color = [48, 41, 36]
                    if 13 <= y <= 15 and 24 <= x <= 29: color = [96, 28, 30]
            pixels.append((*[int(c) for c in color], 255))
    png(path, pixels)

def fabric(path: Path, base, seed: int):
    """Worn cloth: woven lines, stains and frayed holes."""
    rng = random.Random(seed); pixels = []
    holes = [(rng.randrange(32), rng.randrange(32), rng.randrange(2, 4)) for _ in range(3)]
    for y in range(32):
        for x in range(32):
            d = rng.randrange(-9, 10) + (6 if (x + y) % 4 == 0 else 0) - (8 if y % 6 == 0 else 0)
            color = [max(0, min(255, c + d)) for c in base]
            if any((x - hx) ** 2 + (y - hy) ** 2 < r * r for hx, hy, r in holes): color = [int(c * .35) for c in color]
            if (x * 7 + y * 3 + seed) % 41 < 2: color = [int(color[0] * .6 + 40), int(color[1] * .5), int(color[2] * .5)]
            pixels.append((*color, 255))
    png(path, pixels)

def blood(path: Path, seed: int):
    rng = random.Random(seed); pixels = []
    for y in range(32):
        for x in range(32):
            v = rng.randrange(-14, 15)
            color = (max(0, min(255, 104 + v + (24 if (x * y + seed) % 13 == 0 else 0))), max(0, 18 + v // 3), max(0, 24 + v // 3))
            pixels.append((*color, 255))
    png(path, pixels)

SURVIVORS = {  # shirt, trousers, hat (or None), accent
    "engineer": ((230, 126, 34), (52, 73, 94), (241, 196, 15), (250, 220, 60)),
    "scientist": ((244, 246, 247), (90, 98, 110), None, (120, 190, 230)),
    "guard": ((31, 58, 95), (28, 40, 51), (120, 124, 128), (230, 200, 80)),
    "explorer": ((142, 123, 86), (107, 91, 62), (123, 91, 58), (70, 110, 60)),
}

def skin(path: Path, shirt, trousers, hat, accent, seed: int, infected=False):
    """Original 64×64 player skin for a survivor; the infected variant is pale green with blood stains."""
    rng = random.Random(seed)
    flesh = (112, 140, 96) if infected else rng.choice([(222, 182, 150), (198, 150, 112), (150, 104, 74), (240, 200, 170)])
    hair = rng.choice([(48, 34, 24), (92, 60, 30), (20, 20, 22), (170, 130, 70)])
    img = [[(0, 0, 0, 0)] * 64 for _ in range(64)]
    def fill(x0, y0, x1, y1, color, jitter=8):
        for y in range(y0, y1):
            for x in range(x0, x1):
                d = rng.randrange(-jitter, jitter + 1)
                img[y][x] = (*[max(0, min(255, c + d)) for c in color], 255)
    fill(0, 0, 32, 16, flesh, 5)                      # head
    fill(8, 0, 16, 8, hair); fill(24, 8, 32, 16, hair) # top and back of the head
    for x0 in (0, 16): fill(x0, 8, x0 + 8, 10, hair)   # sides
    fill(8, 8, 16, 10, hair)                           # fringe
    for x in (10, 13): img[12][x] = (255, 255, 255, 255); img[12][x + 1] = ((140, 30, 30, 255) if infected else (40, 60, 90, 255))
    for x in range(10, 14): img[14][x] = (110, 50, 50, 255)
    if hat:
        fill(8, 0, 16, 8, hat, 4); fill(8, 8, 16, 9, hat, 4); fill(0, 8, 8, 9, hat, 4); fill(16, 8, 32, 9, hat, 4)
        fill(40, 0, 48, 8, hat, 4); fill(40, 8, 64, 10, hat, 4)  # hat layer brim
    fill(16, 16, 40, 32, shirt)                        # body
    fill(40, 16, 56, 32, shirt); fill(32, 48, 48, 64, shirt)  # arms (sleeves)
    fill(40, 28, 56, 32, flesh, 5); fill(32, 60, 48, 64, flesh, 5)  # hands
    fill(0, 16, 16, 32, trousers); fill(16, 48, 32, 64, trousers)   # legs
    fill(0, 29, 16, 32, (40, 32, 26)); fill(16, 61, 32, 64, (40, 32, 26))  # shoes
    fill(20, 20, 28, 21, accent, 3)                    # collar / badge line
    if accent and hat is None: fill(21, 22, 23, 25, accent, 2)  # pen in the lab coat pocket
    if infected:
        for _ in range(60):
            x, y = rng.randrange(64), rng.randrange(16, 64)
            if img[y][x][3]: img[y][x] = (110, 20, 24, 255)
        for _ in range(20):
            x, y = rng.randrange(8, 16), rng.randrange(8, 16); img[y][x] = (90, 30, 30, 255)
    png(path, [px for row in img for px in row], size=64)

def box(start, end, texture="#skin", angle=0, axis="z", origin=(8, 16, 8), front=None, light=0):
    faces = {face: {"uv": [0, 0, 16, 16], "texture": texture} for face in ["north", "south", "east", "west", "up", "down"]}
    if front: faces["south"]["texture"] = front
    value = {"from": list(start), "to": list(end), "faces": faces}
    if angle: value["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle, "rescale": False}
    if light: value["light_emission"] = light
    return value

def thumbnail(path: Path, elements, assets: Path, kind: str, label: str, texture_paths=None):
    """Actual model faces and original textures, baked to a small standalone SVG for the panel."""
    def project(v):
        x,y,z=v
        return ((x-z)*.86,(x+z)*.4-y)
    faces=[]
    for element in elements:
        x,y,z=element['from'];X,Y,Z=element['to']
        points=[[x,y,z],[X,y,z],[X,y,Z],[x,y,Z],[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]]
        rotation=element.get('rotation')
        if rotation:
            axis=rotation['axis'];o=rotation['origin'];a=math.radians(rotation['angle']);c=math.cos(a);s=math.sin(a)
            result=[]
            for p in points:
                a,b,d=[p[i]-o[i] for i in range(3)]
                q=(a,b*c-d*s,b*s+d*c) if axis=='x' else (a*c+d*s,b,-a*s+d*c) if axis=='y' else (a*c-b*s,a*s+b*c,d)
                result.append([q[i]+o[i] for i in range(3)])
            points=result
        for indices,side,shade in [([7,6,5,4],'up',0),([5,6,2,1],'east',.22),([7,6,2,3],'south',.06)]:
            vertices=[points[i] for i in indices]
            faces.append((sum(v[0]+v[2]+v[1]*.08 for v in vertices)/4,[project(v) for v in vertices],element['faces'][side]['texture'][1:],shade))
    all_points=[v for _,points,_,_ in faces for v in points]
    minx=min(x for x,y in all_points);maxx=max(x for x,y in all_points);miny=min(y for x,y in all_points);maxy=max(y for x,y in all_points)
    size=max(maxx-minx,maxy-miny)+6;ox=(minx+maxx-size)/2;oy=(miny+maxy-size)/2
    svg=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{ox:.2f} {oy:.2f} {size:.2f} {size:.2f}" role="img"><title>{html.escape(label)}</title><defs>']
    for key,file in (texture_paths or {k:assets/'textures/item/horror'/f'{kind}_{k}.png' for k in TEXTURE_KEYS}).items():
        data=base64.b64encode(file.read_bytes()).decode()
        svg.append(f'<image id="{key}" width="32" height="32" href="data:image/png;base64,{data}" image-rendering="pixelated"/>')
    svg.append('</defs>')
    for _,points,key,shade in sorted(faces,key=lambda f:f[0]):
        p,q,r,s=points;a=(q[0]-p[0])/32;b=(q[1]-p[1])/32;c=(s[0]-p[0])/32;d=(s[1]-p[1])/32
        svg.append(f'<use href="#{key}" transform="matrix({a:.5f} {b:.5f} {c:.5f} {d:.5f} {p[0]:.3f} {p[1]:.3f})"/>')
        if shade:svg.append('<polygon points="'+' '.join(f'{x:.3f},{y:.3f}' for x,y in points)+f'" opacity="{shade}"/>')
    svg.append('</svg>');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(''.join(svg),encoding='utf-8')

from creatures import geometry, rebase
TEXTURE_KEYS = ["skin","face","wound","bone","eye","cloth","blood"]

def sound(path: Path, name: str, ffmpeg: str, work: Path):
    gun=name in ('pistol','shotgun','smg','rocket_launch','rocket_impact')
    duration = {'pistol':.22,'shotgun':.4,'smg':.16,'rocket_launch':.8,'rocket_impact':1.2,'nemesis_roar':3.2,'smiler_laugh':1.6}.get(name,0.65 if name in ('spit','jump') else 1.5)
    rate = 22050; rng = random.Random(name); filtered = 0.0; samples = []
    for n in range(int(rate*duration)):
        t = n/rate; noise = rng.uniform(-1, 1); filtered = .94*filtered+.06*noise
        envelope = min(1, t/.045)*max(0, 1-t/duration)**.65
        frequency = {"charge":73,"spit":220,"jump":95,"breath":42,"burst":55,"growl":48,'pistol':95,'shotgun':55,'smg':110,'rocket_launch':65,'rocket_impact':38,'nemesis_roar':52,'smiler_laugh':260}[name]
        tone = math.sin(2*math.pi*frequency*t + 2.8*math.sin(t*19))
        grit = .5*math.sin(2*math.pi*frequency*2.37*t) + .5*math.sin(2*math.pi*frequency*3.1*t)
        value = (noise*.55+filtered*2) if name in ("spit", "burst") else tone*.32+grit*.2+filtered*2.5
        if gun:value=(noise*.65+filtered*2+tone*.4)*math.exp(-t*(11 if name in ('pistol','smg','shotgun') else 3))
        if name=='nemesis_roar':
            # A low tearing roar that rises into a strained, inhuman scream, with a growling sub layer.
            rise=min(1,t/2.2);f=frequency*(1+2.6*rise**2)
            scream=math.sin(2*math.pi*f*t+5*math.sin(2*math.pi*7*t))+.6*math.sin(2*math.pi*f*1.51*t)+.35*math.sin(2*math.pi*f*2.03*t)
            sub=math.sin(2*math.pi*38*t)*(1-rise*.5)
            value=(scream*.32+sub*.35+filtered*3.2+noise*.18*rise)*(.75+.25*math.sin(2*math.pi*23*t))
        if name=='smiler_laugh':
            # Staccato giggle: bright bursts, each sliding down in pitch.
            beat=(t*7.5)%1;f=frequency*(1.25-.35*beat)*(1+.08*math.sin(t*3))
            value=(math.sin(2*math.pi*f*t)+.4*math.sin(2*math.pi*f*2*t)+.15*noise)*max(0,1-beat*2.2)*.8
        value *= envelope * (.55+.25*math.sin(t*31)**2)
        samples.append(int(max(-.92,min(.92,value))*32767))
    wav = work/(name+".wav")
    with wave.open(str(wav), "wb") as f:
        f.setparams((1, 2, rate, 0, "NONE", "not compressed")); f.writeframes(struct.pack("<"+"h"*len(samples), *samples))
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([ffmpeg,"-y","-v","error","-i",str(wav),"-fflags","+bitexact","-flags:a","+bitexact","-c:a","libvorbis","-q:a","3","-ar",str(rate),"-ac","1",str(path)],check=True)

def build(output: Path, ffmpeg: str):
    output = output.resolve(); output.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="pack-build-",dir=output) as temporary:
        work=Path(temporary);pack=work/"pack";assets=pack/"assets"/"duckcatmine";preview=[]
        write_json(pack/"pack.mcmeta",{"pack":{"min_format":FORMAT,"max_format":FORMAT,"description":"DuckCatMine · Ground Zero — bestiaire, armes et sons"}})
        shutil.copyfile(ROOT.parent/"LICENSE",pack/"LICENSE.txt")
        rig=json.loads((ROOT.parent/"apocalypse/src/main/resources/horror-rigs.json").read_text(encoding="utf-8"))
        for index,(kind,(label,base,eye)) in enumerate(ROSTER.items()):
            texture(assets/"textures/item/horror"/(kind+"_skin.png"),base,eye,index*71,kind=kind)
            texture(assets/"textures/item/horror"/(kind+"_face.png"),base,eye,index*71,True,kind=kind)
            fabric(assets/"textures/item/horror"/(kind+"_cloth.png"),CLOTH[kind],index*53)
            blood(assets/"textures/item/horror"/(kind+"_blood.png"),index*31)
            for key,color in [("wound",(78,31,36)),("bone",(188,179,141)),("eye",eye)]:
                rng=random.Random(index*17+len(key))
                png(assets/"textures/item/horror"/(kind+"_"+key+".png"),[(*[max(0,min(255,c+rng.randrange(-10,11))) for c in color],255) for _ in range(1024)])
            parts=geometry(kind,box)
            thumbnail(ROOT.parent/'admin/public/horror'/f'{kind}.svg',[element for group in parts.values() for element in group],assets,kind,label)
            for part,elements in parts.items():
                model=kind+"_"+part
                write_json(assets/"items"/(model+".json"),{"model":{"type":"minecraft:model","model":"duckcatmine:item/"+model}})
                write_json(assets/"models/item"/(model+".json"),{"gui_light":"side","textures":{key:"duckcatmine:item/horror/"+kind+"_"+key for key in TEXTURE_KEYS},"elements":rebase(elements,rig["models"][kind]["pivots"][part])})
            preview.append({"id":kind,"name":label,"skin":base,"eyes":eye,"parts":parts,"pivots":rig["models"][kind]["pivots"]})
        for index,(role,(shirt,trousers,hat,accent)) in enumerate(SURVIVORS.items()):
            skin(assets/"textures/entity/survivor"/(role+".png"),shirt,trousers,hat,accent,900+index)
            skin(assets/"textures/entity/survivor"/(role+"_infected.png"),shirt,trousers,hat,accent,900+index,True)
        from weapons import build_weapons
        weapons=build_weapons(assets,ROOT.parent/'admin/public/weapons',png,box,write_json,thumbnail)
        sounds={}
        for name in ["charge","spit","jump","breath","burst","growl","pistol","shotgun","smg","rocket_launch","rocket_impact","nemesis_roar","smiler_laugh"]:
            sound(assets/"sounds/horror"/(name+".ogg"),name,ffmpeg,work)
            sounds["horror."+name]={"subtitle":"duckcatmine.subtitle."+name,"sounds":[{"name":"duckcatmine:horror/"+name,"attenuation_distance":96 if name=="nemesis_roar" else 24}]}
        write_json(assets/"sounds.json",sounds)
        labels={"charge":"Un infecté prépare une attaque","spit":"Crachat d’acide","jump":"Un infecté bondit","breath":"Une respiration inquiétante","burst":"Un nuage toxique se répand","growl":"Un briseur gronde"}
        labels.update(pistol='Tir de pistolet',shotgun='Tir de fusil à pompe',smg='Rafale de mitraillette',rocket_launch='Départ de roquette',rocket_impact='Explosion de roquette',nemesis_roar='Le Némésis rugit',smiler_laugh='Un rire dans le noir')
        write_json(assets/"lang/fr_fr.json",{"duckcatmine.subtitle."+k:v for k,v in labels.items()})
        write_json(assets/"lang/en_us.json",{"duckcatmine.subtitle."+k:v for k,v in {"charge":"An infected prepares an attack","spit":"Acid spits","jump":"An infected leaps","breath":"Uneasy breathing","burst":"A toxic cloud spreads","growl":"A breaker growls","pistol":"Pistol shot","shotgun":"Shotgun blast","smg":"Submachine gun burst","rocket_launch":"Rocket launch","rocket_impact":"Rocket impact","nemesis_roar":"The Nemesis roars","smiler_laugh":"Laughter in the dark"}.items()})
        temporary_zip=work/"pack.zip"
        with zipfile.ZipFile(temporary_zip,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for path in sorted(pack.rglob("*")):
                if not path.is_file():continue
                info=zipfile.ZipInfo(path.relative_to(pack).as_posix(),date_time=(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o644<<16
                z.writestr(info,path.read_bytes())
        data=temporary_zip.read_bytes();sha1=hashlib.sha1(data).hexdigest();name="duckcatmine-horror-"+sha1[:16]+".zip"
        (output/name).write_bytes(data)
        write_json(output/"manifest.json",{"version":VERSION,"minecraft":"26.3","format":FORMAT,"filename":name,"sha1":sha1,"size":len(data),"path":"/packs/"+name,"license":"GPL-3.0-only"})
        write_json(output/"preview.json",{"version":VERSION,"models":preview,"weapons":weapons})
        assert len(data)<1024*1024,"Resource pack exceeds its lightweight budget"
        print(json.dumps({"file":str(output/name),"size":len(data),"sha1":sha1,"models":len(preview),"sounds":len(sounds)},ensure_ascii=False))

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--out",type=Path,default=ROOT/"dist");parser.add_argument("--ffmpeg",default=shutil.which("ffmpeg"));args=parser.parse_args()
    if not args.ffmpeg:parser.error("FFmpeg with libvorbis is required to rebuild the original sounds")
    build(args.out,args.ffmpeg)
