#!/usr/bin/env python3
"""Build DuckCatMine's original, mod-free horror assets. Python stdlib + FFmpeg/Vorbis only."""
from __future__ import annotations
import argparse, base64, hashlib, html, json, math, random, shutil, struct, subprocess, tempfile, wave, zipfile, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VERSION = "1.1.0"
FORMAT = [97, 1]  # Minecraft Java 26.3, verified against its version.json and Mojang release notes.
ROSTER = {
    "spitter": ("Cracheur d’acide", (83, 106, 62), (215, 244, 82)),
    "jumper": ("Bondisseur", (109, 84, 73), (248, 186, 92)),
    "crawler": ("Rampant", (111, 104, 91), (238, 224, 177)),
    "stalker": ("Traqueur décharné", (145, 150, 137), (240, 230, 208)),
    "bloated": ("Boursouflé", (82, 107, 100), (189, 215, 110)),
    "breaker": ("Briseur", (103, 89, 83), (255, 158, 100)),
}

def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

def png(path: Path, pixels: list[tuple[int, int, int, int]], size=32):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    rows = b"".join(b"\0" + bytes(c for px in pixels[y*size:(y+1)*size] for c in px) for y in range(size))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))

def texture(path: Path, base, eye, seed: int, face=False):
    rng = random.Random(seed)
    pixels = []
    for y in range(32):
        for x in range(32):
            delta = rng.randrange(-17, 18)
            color = tuple(max(0, min(255, c+delta)) for c in base)
            if (x*3+y*5+seed) % 29 < 2: color = (58, 39, 36)  # original pixel scars and veins
            if face:
                if 6 <= y <= 11 and (5 <= x <= 11 or 20 <= x <= 26): color = (24, 22, 20)
                if 8 <= y <= 10 and (8 <= x <= 10 or 21 <= x <= 23): color = eye
                if 18 <= y <= 27 and 6 <= x <= 26: color = (42, 21, 24)
                if (19 <= y <= 21 or 25 <= y <= 27) and 8 <= x <= 24 and x % 4 < 2: color = (210, 198, 158)
                if x in (16, 17) and 11 <= y <= 17: color = (48, 41, 36)
            pixels.append((*color, 255))
    png(path, pixels)

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
    for key,file in (texture_paths or {k:assets/'textures/item/horror'/f'{kind}_{k}.png' for k in ['skin','face','wound','bone','eye']}).items():
        data=base64.b64encode(file.read_bytes()).decode()
        svg.append(f'<image id="{key}" width="32" height="32" href="data:image/png;base64,{data}" image-rendering="pixelated"/>')
    svg.append('</defs>')
    for _,points,key,shade in sorted(faces,key=lambda f:f[0]):
        p,q,r,s=points;a=(q[0]-p[0])/32;b=(q[1]-p[1])/32;c=(s[0]-p[0])/32;d=(s[1]-p[1])/32
        svg.append(f'<use href="#{key}" transform="matrix({a:.5f} {b:.5f} {c:.5f} {d:.5f} {p[0]:.3f} {p[1]:.3f})"/>')
        if shade:svg.append('<polygon points="'+' '.join(f'{x:.3f},{y:.3f}' for x,y in points)+f'" opacity="{shade}"/>')
    svg.append('</svg>');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(''.join(svg),encoding='utf-8')

def geometry(kind: str):
    """Three compact item models form a rig; no third-party game textures or creature models."""
    body = [box((4, 10, 5), (12, 21, 11)), box((4, 21, 4), (12, 29, 12), front="#face"),
            box((1, 11, 6), (4, 23, 10), angle=-22.5), box((12, 10, 6), (15, 22, 10), angle=22.5)]
    left = [box((3, 1, 6), (7, 11, 10)), box((2, 0, 5), (7, 2, 13))]
    right = [box((9, 1, 6), (13, 11, 10)), box((9, 0, 5), (14, 2, 13))]
    if kind == "spitter":
        body += [box((2, 19, 10), (14, 24, 15), "#wound"), box((1, 17, 6), (4, 21, 10), "#eye", light=3),
                 box((12, 17, 6), (15, 21, 10), "#eye", light=3)]
        body += [box((x, 22, 14), (x+1, 25, 16), "#bone") for x in (3, 6, 9, 12)]
    elif kind == "jumper":
        body = [box((3, 8, 4), (13, 19, 11), angle=22.5, axis="x", origin=(8, 10, 8)),
                box((3, 14, 9), (13, 23, 17), front="#face"), box((0, 3, 6), (3, 20, 10)),
                box((13, 3, 6), (16, 20, 10)), box((6, 11, 2), (10, 22, 5), "#bone")]
        left = [box((1, 0, 4), (7, 4, 14)), box((2, 3, 7), (7, 10, 12), angle=-22.5)]
        right = [box((9, 0, 4), (15, 4, 14)), box((9, 3, 7), (14, 10, 12), angle=22.5)]
    elif kind == "crawler":
        body = [box((3, 3, 1), (13, 10, 14)), box((3, 4, 12), (13, 12, 20), front="#face"),
                box((0, 0, 6), (3, 5, 18)), box((13, 0, 6), (16, 5, 18)),
                box((6, 9, 0), (10, 12, 12), "#bone")]
        left = [box((3, 0, -5), (7, 4, 4))]
        right = [box((9, 0, -5), (13, 4, 4))]
    elif kind == "stalker":
        body = [box((5, 11, 6), (11, 23, 10)), box((5, 23, 5), (11, 32, 11), front="#face"),
                box((1, 3, 7), (4, 24, 10), angle=-22.5), box((12, 1, 7), (15, 23, 10), angle=22.5),
                box((7, 13, 4), (9, 29, 6), "#bone")]
        left = [box((4, 0, 7), (7, 12, 10)), box((3, 0, 7), (7, 2, 14))]
        right = [box((9, 0, 7), (12, 12, 10)), box((9, 0, 7), (13, 2, 14))]
    elif kind == "bloated":
        body = [box((1, 5, 2), (15, 21, 15)), box((4, 20, 5), (12, 28, 13), front="#face"),
                box((-2, 5, 5), (2, 20, 12), angle=-22.5), box((14, 6, 5), (18, 20, 12), angle=22.5),
                box((5, 9, 14), (11, 18, 17), "#wound"), box((2, 17, 11), (5, 21, 15), "#eye", light=2)]
        left = [box((3, 0, 6), (7, 7, 11))]
        right = [box((9, 0, 6), (13, 7, 11))]
    elif kind == "breaker":
        body = [box((1, 9, 3), (15, 24, 13)), box((3, 22, 4), (13, 31, 14), front="#face"),
                box((-3, 3, 4), (2, 25, 12)), box((14, 2, 4), (19, 25, 12)),
                box((-4, 0, 6), (3, 7, 14), "#bone"), box((13, 0, 6), (20, 7, 14), "#bone"),
                box((5, 10, 13), (11, 19, 15), "#wound")]
        left = [box((2, 0, 5), (7, 10, 11)), box((1, 0, 4), (7, 3, 14))]
        right = [box((9, 0, 5), (14, 10, 11)), box((9, 0, 4), (15, 3, 14))]
    return {"body": body, "left": left, "right": right}

def sound(path: Path, name: str, ffmpeg: str, work: Path):
    gun=name in ('pistol','shotgun','smg','rocket_launch','rocket_impact')
    duration = {'pistol':.22,'shotgun':.4,'smg':.16,'rocket_launch':.8,'rocket_impact':1.2}.get(name,0.65 if name in ('spit','jump') else 1.5)
    rate = 22050; rng = random.Random(name); filtered = 0.0; samples = []
    for n in range(int(rate*duration)):
        t = n/rate; noise = rng.uniform(-1, 1); filtered = .94*filtered+.06*noise
        envelope = min(1, t/.045)*max(0, 1-t/duration)**.65
        frequency = {"charge":73,"spit":220,"jump":95,"breath":42,"burst":55,"growl":48,'pistol':95,'shotgun':55,'smg':110,'rocket_launch':65,'rocket_impact':38}[name]
        tone = math.sin(2*math.pi*frequency*t + 2.8*math.sin(t*19))
        grit = .5*math.sin(2*math.pi*frequency*2.37*t) + .5*math.sin(2*math.pi*frequency*3.1*t)
        value = (noise*.55+filtered*2) if name in ("spit", "burst") else tone*.32+grit*.2+filtered*2.5
        if gun:value=(noise*.65+filtered*2+tone*.4)*math.exp(-t*(11 if name in ('pistol','smg','shotgun') else 3))
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
        for index,(kind,(label,base,eye)) in enumerate(ROSTER.items()):
            texture(assets/"textures/item/horror"/(kind+"_skin.png"),base,eye,index*71)
            texture(assets/"textures/item/horror"/(kind+"_face.png"),base,eye,index*71,True)
            for key,color in [("wound",(78,31,36)),("bone",(188,179,141)),("eye",eye)]:
                png(assets/"textures/item/horror"/(kind+"_"+key+".png"),[(*color,255)]*1024)
            parts=geometry(kind)
            thumbnail(ROOT.parent/'admin/public/horror'/f'{kind}.svg',[element for group in parts.values() for element in group],assets,kind,label)
            for part,elements in parts.items():
                model=kind+"_"+part
                write_json(assets/"items"/(model+".json"),{"model":{"type":"minecraft:model","model":"duckcatmine:item/"+model}})
                write_json(assets/"models/item"/(model+".json"),{"gui_light":"side","textures":{key:"duckcatmine:item/horror/"+kind+"_"+key for key in ["skin","face","wound","bone","eye"]},"elements":elements})
            preview.append({"id":kind,"name":label,"skin":base,"eyes":eye,"parts":parts})
        from weapons import build_weapons
        weapons=build_weapons(assets,ROOT.parent/'admin/public/weapons',png,box,write_json,thumbnail)
        sounds={}
        for name in ["charge","spit","jump","breath","burst","growl","pistol","shotgun","smg","rocket_launch","rocket_impact"]:
            sound(assets/"sounds/horror"/(name+".ogg"),name,ffmpeg,work)
            sounds["horror."+name]={"subtitle":"duckcatmine.subtitle."+name,"sounds":[{"name":"duckcatmine:horror/"+name,"attenuation_distance":24}]}
        write_json(assets/"sounds.json",sounds)
        labels={"charge":"Un infecté prépare une attaque","spit":"Crachat d’acide","jump":"Un infecté bondit","breath":"Une respiration inquiétante","burst":"Un nuage toxique se répand","growl":"Un briseur gronde"}
        labels.update(pistol='Tir de pistolet',shotgun='Tir de fusil à pompe',smg='Rafale de mitraillette',rocket_launch='Départ de roquette',rocket_impact='Explosion de roquette')
        write_json(assets/"lang/fr_fr.json",{"duckcatmine.subtitle."+k:v for k,v in labels.items()})
        write_json(assets/"lang/en_us.json",{"duckcatmine.subtitle."+k:v for k,v in {"charge":"An infected prepares an attack","spit":"Acid spits","jump":"An infected leaps","breath":"Uneasy breathing","burst":"A toxic cloud spreads","growl":"A breaker growls","pistol":"Pistol shot","shotgun":"Shotgun blast","smg":"Submachine gun burst","rocket_launch":"Rocket launch","rocket_impact":"Rocket impact"}.items()})
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
