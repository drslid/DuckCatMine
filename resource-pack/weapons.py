"""Original block-model firearms: recognizable fictional silhouettes, no third-party assets."""
import random

WEAPONS = {'pistol':'Pistolet', 'shotgun':'Fusil à pompe', 'smg':'Mitraillette', 'rocket_launcher':'Lance-roquettes'}
PALETTE = {'metal':(83,94,102),'dark':(29,34,39),'wood':(119,72,39),'grip':(41,44,43),
           'brass':(191,153,65),'olive':(89,106,65),'warning':(198,151,48),'glass':(52,139,143)}

def weapon(kind,box):
    def b(a,z,t='metal',**kw):return box(a,z,'#'+t,**kw)
    if kind=='pistol':
        return [b((5,11,4),(11,16,20)),b((6,4,4),(10,12,9),'grip',angle=-22.5,axis='x',origin=(8,11,7)),
                b((6,10,9),(10,12,16),'dark'),b((6,7,10),(7,11,15),'dark'),b((9,7,10),(10,11,15),'dark'),b((6,7,14),(10,8,15),'dark'),
                b((7,12,19),(9,15,23),'dark'),b((7.4,12.5,23),(8.6,14.4,23.1),'dark'),
                b((5,15,6),(11,16.5,7),'dark'),b((7.3,16,18),(8.7,17,19),'brass'),b((8,11,11),(9,13,12),'brass')]
    if kind=='shotgun':
        return [b((5,9,-6),(11,13,4),'wood'),b((5,8,-7),(11,14,-5),'grip'),b((5,10,3),(11,15,12)),
                b((6,5,2),(10,11,7),'wood',angle=-22.5,axis='x',origin=(8,11,5)),
                b((7,13,11),(9,15,30),'dark'),b((7,10,11),(9,12,28),'metal'),b((5.5,9.5,15),(10.5,12.5,24),'wood'),
                b((7.4,15,28),(8.6,16,29),'brass'),b((6,7,8),(7,10,12),'dark'),b((9,7,8),(10,10,12),'dark'),b((6,7,11),(10,8,12),'dark')]
    if kind=='smg':
        parts=[b((5,9,-6),(11,12,4),'dark'),b((5,7,-7),(11,14,-5),'grip'),b((4,10,3),(12,15,17)),
               b((6,12,17),(10,15,28),'dark'),b((5,10,17),(11,15,23)),b((6,1,10),(10,11,14),'dark'),
               b((6,3,4),(10,11,8),'grip',angle=-22.5,axis='x',origin=(8,11,6)),b((6,5,20),(10,10,23),'grip'),
               b((5,15,4),(11,16,19),'dark'),b((7,16,6),(9,17.5,8),'brass'),b((7,15.5,22),(9,17,23),'brass')]
        return parts+[b((4.8,11,zz),(5,14,zz+1),'dark') for zz in [18,20,22]]
    if kind=='rocket_launcher':
        return [b((4,8,-5),(12,16,27),'olive'),b((3,9,-5),(13,15,27),'olive'),
                b((5,7,-5),(11,17,27),'olive'),b((3,7,25),(13,17,29),'dark'),b((4,8,29),(12,16,29.2),'dark'),
                b((6,10,29.2),(10,14,29.3),'warning'),b((3,7,-7),(13,17,-4),'dark'),b((5,6,-6),(11,11,-2),'grip'),
                b((6,1,11),(10,9,15),'grip'),b((9,8,6),(13,11,10),'dark'),b((11,11,4),(14,14,13),'metal'),
                b((11.2,11.2,13),(13.8,13.8,13.2),'glass'),b((3.8,8,19),(12.2,16,20),'warning')]
    if kind=='rocket_projectile':
        return [b((6,6,-2),(10,10,12),'olive'),b((6.5,6.5,12),(9.5,9.5,15),'warning'),b((7.2,7.2,15),(8.8,8.8,17),'dark'),
                b((4,7.5,-3),(12,8.5,1),'metal'),b((7.5,4,-3),(8.5,12,1),'metal'),b((7,7,-4),(9,9,-2),'brass')]
    raise ValueError(kind)

def build_weapons(assets,output,png,box,write_json,thumbnail):
    for index,(name,color) in enumerate(PALETTE.items()):
        rng=random.Random(8800+index);pixels=[]
        for y in range(32):
            for x in range(32):
                d=rng.randint(-7,7)+(8 if y%8==0 else 0)
                pixels.append((*[max(0,min(255,c+d)) for c in color],255))
        png(assets/'textures/item/weapons'/f'{name}.png',pixels)
    models=[]
    for kind in [*WEAPONS,'rocket_projectile']:
        elements=weapon(kind,box)
        display={
            'gui':{'rotation':[25,225,0],'translation':[0,-1,0],'scale':[.7,.7,.7] if kind=='pistol' else [.5,.5,.5]},
            'firstperson_righthand':{'rotation':[0,180,0],'translation':[2,-1,-4],'scale':[.75,.75,.75]},
            'firstperson_lefthand':{'rotation':[0,180,0],'translation':[-2,-1,-4],'scale':[.75,.75,.75]},
            'thirdperson_righthand':{'rotation':[0,180,0],'translation':[0,1,1],'scale':[.65,.65,.65]},
            'thirdperson_lefthand':{'rotation':[0,180,0],'translation':[0,1,1],'scale':[.65,.65,.65]},
            'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.4,.4,.4]},
            'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},
        }
        write_json(assets/'models/item/weapons'/f'{kind}.json',{'gui_light':'side','textures':{k:'duckcatmine:item/weapons/'+k for k in PALETTE},'display':display,'elements':elements})
        if kind in WEAPONS:
            thumbnail(output/f'{kind}.svg',elements,assets,kind,WEAPONS[kind],texture_paths={k:assets/'textures/item/weapons'/f'{k}.png' for k in PALETTE})
        models.append({'id':kind,'name':WEAPONS.get(kind,'Roquette'),'parts':{'body':elements}})
    # Only opt-in named strings select a weapon. Ordinary paper keeps its vanilla model,
    # and clients declining the pack see a usable named paper item, never a missing texture.
    write_json(assets.parent/'minecraft/items/paper.json',{'model':{'type':'minecraft:select','property':'minecraft:custom_model_data','index':0,
        'cases':[{'when':'duckcatmine:'+kind,'model':{'type':'minecraft:model','model':'duckcatmine:item/weapons/'+kind}} for kind in [*WEAPONS,'rocket_projectile']],
        'fallback':{'type':'minecraft:model','model':'minecraft:item/paper'}}})
    return models
