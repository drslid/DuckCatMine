"""Original biological horror meshes, authored in pixels before joint rebasing. Forward is +Z.

Every creature shares a detailed humanoid base (torn clothes, ribs, spine, jaw, knees, claws), then each kind reshapes
it. Texture keys: skin, face, wound, bone, eye (emissive details), cloth and blood.
"""

def _details(parts, box, lean=0.0):
    """Shared anatomy details on the default humanoid proportions."""
    parts['body'] += [
        # Torn shirt over the belly and a ragged hem.
        box((3.6, 10, 4.6), (12.4, 15, 11.4), '#cloth'),
        box((4.5, 9, 4.4), (6.5, 10.5, 11.6), '#cloth'), box((9, 9.2, 4.4), (11.5, 10.5, 11.6), '#cloth'),
        # Exposed ribs through the torn chest, a dark wound behind them.
        box((5.5, 15.5, 10.6), (10.5, 20.5, 11.3), '#blood'),
        *[box((5, y, 11.1), (11, y + 0.7, 11.8), '#bone') for y in (16, 17.6, 19.2)],
        # Spine ridge on the back.
        *[box((7.3, y, 4.2), (8.7, y + 1.2, 5.2), '#bone') for y in (12, 14.5, 17, 19.5)],
        # Collar bones.
        box((4.5, 21.3, 7), (11.5, 22, 10), '#bone'),
    ]
    parts['head'] += [
        # Hanging jaw with teeth, ears and a scalp wound.
        box((5, 20, 9), (11, 21.6, 12.6), '#wound'),
        *[box((x, 21.2, 11.9), (x + 0.8, 22.2, 12.5), '#bone') for x in (5.4, 6.8, 8.2, 9.6)],
        box((3.4, 24, 7), (4, 26.5, 9), '#skin'), box((12, 24, 7), (12.6, 26.5, 9), '#skin'),
        box((6, 28.6, 5), (10, 29.3, 9), '#blood'),
        # Glowing pupils, the only light in the dark.
        box((5.3, 25.3, 12.05), (6.7, 26.2, 12.25), '#eye', light=6), box((9.3, 25.3, 12.05), (10.7, 26.2, 12.25), '#eye', light=6),
    ]
    for side, x0 in (('left', 3), ('right', 9)):
        parts[side] += [box((x0 - 0.3, 5, 9.7), (x0 + 4.3, 7, 10.4), '#bone'),  # knee cap
                        box((x0 - 0.2, 7, 5.8), (x0 + 4.2, 10, 10.2), '#cloth')]  # torn trouser leg
    for arm, x0 in (('arm_left', 1), ('arm_right', 12)):
        parts[arm] += [box((x0 - 0.3, 18, 5.7), (x0 + 3.3, 23.4, 10.3), '#cloth'),  # sleeve
                       box((x0 + 0.5, 13, 9.8), (x0 + 2.5, 16, 10.5), '#blood')]


def geometry(kind, box):
    parts = {
        'body': [box((4, 10, 5), (12, 22, 11)), box((5, 12, 10.5), (11, 19, 12), '#wound')],
        'head': [box((4, 21, 4), (12, 29, 12), front='#face')],
        'arm_left': [box((1, 10, 6), (4, 23, 10)), box((.5, 8, 7), (4, 12, 12), '#wound')],
        'arm_right': [box((12, 10, 6), (15, 23, 10)), box((12, 8, 7), (15.5, 12, 12), '#wound')],
        'left': [box((3, 1, 6), (7, 11, 10)), box((2, 0, 5), (7, 2, 13))],
        'right': [box((9, 1, 6), (13, 11, 10)), box((9, 0, 5), (14, 2, 13))],
    }
    if kind == 'spitter':
        _details(parts, box)
    if kind == 'spitter':
        parts['head'] += [box((2, 20, 10), (14, 25, 15), '#wound'),
                          box((4, 21, 14), (12, 23, 16), '#eye', light=3)]
        parts['head'] += [box((x, 23, 14), (x+1, 26, 16), '#bone') for x in (3, 6, 9, 12)]
        # Swollen acid glands on the throat and cheeks.
        parts['head'] += [box((2.5, 23.5, 8), (4.5, 26, 11), '#eye', light=4), box((11.5, 23.5, 8), (13.5, 26, 11), '#eye', light=4)]
        parts['body'] += [box((1.5, 15, 8), (5, 21, 12), '#eye', light=2),
                          box((11, 14, 8), (14.5, 20, 12), '#eye', light=2),
                          box((6, 18, 12), (10, 23, 14), '#wound'),
                          box((6.5, 13, 11.8), (9.5, 14, 13.5), '#eye', light=3)]
    elif kind == 'jumper':
        parts['body'] = [box((3, 7, 3), (13, 18, 11)), box((6, 9, 1), (10, 21, 5), '#bone'),
                         box((3.5, 8, 10.5), (12.5, 12, 11.4), '#cloth'),
                         *[box((4, y, 10.6), (12, y + .7, 11.5), '#bone') for y in (13, 14.6, 16.2)],
                         *[box((7, y, .3), (9, y + 1.2, 1.3), '#bone') for y in (11, 14, 17)]]
        parts['head'] = [box((3, 14, 9), (13, 23, 17), front='#face'), box((4, 13, 15), (12, 17, 20), '#wound'),
                         *[box((x, 13.4, 19.4), (x + .9, 15, 20.3), '#bone') for x in (4.6, 6.4, 8.2, 10)],
                         box((4.8, 19, 17.05), (6.6, 20.2, 17.3), '#eye', light=6), box((9.4, 19, 17.05), (11.2, 20.2, 17.3), '#eye', light=6)]
        parts['arm_left'] = [box((-1, 3, 5), (3, 19, 11)), box((-2, 0, 7), (4, 5, 14), '#wound'), box((-1.3, 12, 4.7), (3.3, 16, 11.3), '#cloth')]
        parts['arm_right'] = [box((13, 3, 5), (17, 19, 11)), box((12, 0, 7), (18, 5, 14), '#wound'), box((12.7, 12, 4.7), (17.3, 16, 11.3), '#cloth')]
        # Powerful digitigrade legs.
        parts['left'] = [box((1, 0, 4), (7, 4, 14)), box((2, 3, 7), (7, 10, 12)), box((1.6, 6, 4), (7.4, 9, 7), '#bone')]
        parts['right'] = [box((9, 0, 4), (15, 4, 14)), box((9, 3, 7), (14, 10, 12)), box((8.6, 6, 4), (14.4, 9, 7), '#bone')]
    elif kind == 'crawler':
        parts['body'] = [box((3, 3, 0), (13, 10, 14)), box((6, 9, -1), (10, 12, 11), '#bone'),
                         box((2.6, 2.5, 4), (13.4, 6, 10), '#cloth'),
                         *[box((7.2, 11.6, z), (8.8, 12.8, z + 1.4), '#bone') for z in (0, 3, 6, 9)],
                         box((5, 2.6, 1), (11, 3.2, 13), '#blood')]
        parts['head'] = [box((3, 4, 12), (13, 12, 20), front='#face'), box((6, 5, 18), (10, 7, 24), '#wound'),
                         *[box((x, 6.6, 22.6), (x + .7, 7.6, 23.4), '#bone') for x in (6.3, 7.6, 8.9)],
                         box((4.8, 8.5, 20.05), (6.4, 9.5, 20.3), '#eye', light=6), box((9.6, 8.5, 20.05), (11.2, 9.5, 20.3), '#eye', light=6)]
        parts['arm_left'] = [box((-1, 1, 7), (3, 7, 18)), box((-2, 0, 16), (4, 3, 22), '#wound'), box((-1.3, 1, 9), (3.3, 7.3, 12), '#cloth')]
        parts['arm_right'] = [box((13, 1, 7), (17, 7, 18)), box((12, 0, 16), (18, 3, 22), '#wound'), box((12.7, 1, 9), (17.3, 7.3, 12), '#cloth')]
        # Dragged, broken legs with a bare bone.
        parts['left'] = [box((2, 0, -6), (7, 4, 4)), box((3, 1, -8), (5, 2.5, -6), '#bone')]
        parts['right'] = [box((9, 0, -6), (14, 4, 4)), box((10.5, 3.6, -4), (12, 4.6, 2), '#blood')]
    elif kind == 'stalker':
        parts['body'] = [box((5, 11, 6), (11, 24, 10)), box((7, 13, 3), (9, 27, 6), '#bone'),
                         box((4.6, 11, 5.6), (11.4, 14, 10.4), '#cloth')]
        parts['head'] = [box((5, 23, 5), (11, 32, 11), front='#face'), box((5, 22, 10), (11, 27, 14), '#wound'),
                         *[box((x, 22.4, 13.4), (x + .7, 24.2, 14.2), '#bone') for x in (5.6, 7, 8.4, 9.8)],
                         box((5.6, 28, 11.05), (7, 29, 11.3), '#eye', light=7), box((9, 28, 11.05), (10.4, 29, 11.3), '#eye', light=7)]
        parts['arm_left'] = [box((1, 3, 7), (4, 24, 10)), box((.5, 1, 8), (4, 5, 13), '#wound'), box((.7, 13, 7.6), (4.3, 14, 9.4), '#bone')]
        parts['arm_right'] = [box((12, 1, 7), (15, 24, 10)), box((12, 0, 8), (15.5, 4, 13), '#wound'), box((11.7, 13, 7.6), (15.3, 14, 9.4), '#bone')]
        parts['left'] = [box((4, 0, 7), (7, 12, 10)), box((3, 0, 7), (7, 2, 14)), box((3.8, 5.5, 9.8), (7.2, 6.5, 10.4), '#bone')]
        parts['right'] = [box((9, 0, 7), (12, 12, 10)), box((9, 0, 7), (13, 2, 14)), box((8.8, 5.5, 9.8), (12.2, 6.5, 10.4), '#bone')]
        parts['body'] += [box((3, y, 10), (13, y+1, 12), '#bone') for y in (15, 18, 21)]
    elif kind == 'bloated':
        parts['body'] = [box((0, 5, 2), (16, 22, 16)), box((4, 8, 15), (12, 19, 18), '#wound'),
                         box((1, 16, 13), (5, 22, 18), '#eye', light=2),
                         # Pustules and a stretched, split shirt.
                         box((11, 10, 15.5), (14, 13, 17.5), '#eye', light=3), box((2, 7, 15.5), (4.5, 9.5, 17), '#eye', light=2),
                         box((-.3, 5, 5), (16.3, 9, 13), '#cloth'), box((6, 12, 17.6), (10, 15, 18.6), '#blood'),
                         box((13.8, 16, 4), (16.6, 19, 7), '#eye', light=2)]
        parts['head'] = [box((4, 20, 5), (12, 28, 13), front='#face'), box((5, 19, 10), (11, 21, 13.6), '#wound'),
                         box((5.2, 24.6, 13.05), (6.6, 25.6, 13.3), '#eye', light=6), box((9.4, 24.6, 13.05), (10.8, 25.6, 13.3), '#eye', light=6)]
        parts['arm_left'] = [box((-3, 5, 5), (2, 21, 12)), box((-3, 4, 10), (2, 9, 14), '#wound'), box((-3.3, 14, 4.7), (2.3, 18, 12.3), '#cloth')]
        parts['arm_right'] = [box((14, 6, 5), (19, 21, 12)), box((14, 4, 10), (19, 9, 14), '#wound'), box((13.7, 14, 4.7), (19.3, 18, 12.3), '#cloth')]
        parts['left'] = [box((3, 0, 6), (7, 7, 11)), box((2.7, 3, 5.7), (7.3, 7, 11.3), '#cloth')]
        parts['right'] = [box((9, 0, 6), (13, 7, 11)), box((8.7, 3, 5.7), (13.3, 7, 11.3), '#cloth')]
    elif kind in ('breaker', 'abomination'):
        boss = kind == 'abomination'
        parts['body'] = [box((0, 9, 3), (16, 25, 13)), box((5, 10, 12), (12, 21, 16), '#wound'),
                         box((-.3, 9, 2.7), (16.3, 14, 13.3), '#cloth'),
                         *[box((5.5, y, 15.6), (11.5, y + .8, 16.4), '#bone') for y in (14, 16, 18)],
                         box((3, 23, 4), (13, 26, 12), '#skin')]
        parts['head'] = [box((3, 23, 4), (13, 32, 14), front='#face'), box((4, 21, 12), (12, 25, 16), '#wound'),
                         *[box((x, 21.6, 15.4), (x + .9, 23.4, 16.3), '#bone') for x in (4.6, 6.4, 8.2, 10)],
                         box((4.6, 28, 14.05), (6.6, 29.2, 14.3), '#eye', light=7), box((9.4, 28, 14.05), (11.4, 29.2, 14.3), '#eye', light=7)]
        parts['arm_left'] = [box((-4, 3, 4), (2, 25, 12)), box((-5, 0, 6), (3, 7, 15), '#bone'), box((-4.3, 17, 3.7), (2.3, 22, 12.3), '#cloth')]
        parts['arm_right'] = [box((14, 2, 3), (22 if boss else 20, 26, 14), '#wound'),
                              box((13, 0, 6), (23 if boss else 21, 8, 17), '#bone'),
                              *[box((x, 14, 13.6), (x + 1, 20, 14.6), '#bone') for x in ((15, 17.5, 20) if boss else (15, 17.5))]]
        parts['left'] = [box((1, 0, 5), (7, 11, 12)), box((0, 0, 4), (7, 3, 15)), box((.7, 5, 4.7), (7.3, 9, 12.3), '#cloth')]
        parts['right'] = [box((9, 0, 5), (15, 11, 12)), box((9, 0, 4), (16, 3, 15)), box((8.7, 5, 4.7), (15.3, 9, 12.3), '#cloth')]
        parts['body'] += [box((x, 20, 0), (x+2, 29 if boss else 26, 5), '#bone') for x in (1, 6, 11)]
        if boss:
            parts['body'] += [box((2, 15, 12), (6, 20, 17), '#eye', light=2), box((9, 18, 13), (14, 23, 18), '#eye', light=2),
                              box((12, 11, 12.5), (15, 14, 16), '#eye', light=3), box((0, 18, 1), (3, 22, 6), '#blood')]
    elif kind == 'nemesis':
        # Tall tyrant in a long black coat, bald scarred head with a lipless grin, a tendril at the shoulder and a launcher.
        parts['body'] = [box((1.5, 15, 3), (14.5, 31, 13), '#cloth'),
                         box((1, 6, 2.5), (15, 16, 13.5), '#cloth'),              # coat tails over the thighs
                         box((5.5, 17, 12.9), (10.5, 30, 13.6), '#skin'),         # open coat, pale torso
                         *[box((5.8, y, 13.3), (10.2, y + .6, 13.9), '#wound') for y in (19, 22, 25)],
                         box((1.2, 24, 2.6), (14.8, 25.4, 13.4), '#bone'),        # harness strap
                         box((7, 24, 13.5), (9, 26, 14.2), '#bone'),              # buckle
                         box((2, 29, 4), (14, 31.5, 12), '#cloth'),               # raised collar
                         box((2, 30, 9), (4, 34, 11), '#wound'), box((1, 33, 9.5), (3, 36, 11), '#wound'),
                         box((.5, 35.5, 10), (2, 37, 11), '#blood')]
        parts['head'] = [box((4, 30, 4), (12, 40, 12), front='#face'),
                         box((5, 30.4, 11.6), (11, 32.4, 12.7), '#wound'),
                         *[box((x, 31.6, 12.3), (x + .9, 32.8, 12.9), '#bone') for x in (5.4, 6.8, 8.2, 9.6)],
                         box((5.4, 35.6, 12.05), (7, 36.6, 12.3), '#eye', light=10),
                         box((3.6, 32, 6), (4.2, 39, 6.6), '#blood'), box((8.5, 39.6, 5), (9.1, 40.2, 11), '#blood')]
        parts['arm_left'] = [box((-2, 14, 5), (2, 30, 11), '#cloth'), box((-2.5, 9, 5.5), (2, 14, 10.5)),
                             box((-2.6, 9, 9), (1.8, 10.5, 12), '#bone')]
        parts['arm_right'] = [box((14, 14, 5), (18, 30, 11), '#cloth'), box((14, 9, 5.5), (18.5, 14, 10.5)),
                              box((14.5, 9, 0), (19.5, 13.5, 22), '#bone'),       # launcher tube
                              box((15, 9.5, 22), (19, 13, 22.4), '#blood'), box((15.5, 13.5, 6), (18.5, 15, 10), '#bone')]
        parts['left'] = [box((3, 3, 5), (7.5, 16, 11), '#cloth'), box((2.5, 0, 4), (7.5, 3.5, 12.5), '#bone')]
        parts['right'] = [box((8.5, 3, 5), (13, 16, 11), '#cloth'), box((8.5, 0, 4), (13.5, 3.5, 12.5), '#bone')]
    elif kind == 'smiler':
        # Spindly body under a huge head; the grin and the eyes glow, so at night only the smile shows.
        parts['body'] = [box((5, 9, 6), (11, 19, 10), '#cloth'), box((5.5, 12, 9.6), (10.5, 17, 10.4), '#skin'),
                         *[box((5.7, y, 10.1), (10.3, y + .5, 10.6), '#bone') for y in (13, 14.5, 16)],
                         box((7.3, 10, 5.5), (8.7, 18, 6.2), '#bone')]
        parts['head'] = [box((0, 18, 0), (16, 34, 16), front='#face'),
                         box((1, 20, 15.9), (15, 27, 16.4), '#wound'),            # wide open mouth
                         # A crescent grin of glowing teeth, lower in the middle.
                         *[box((x, 25.6 + (-.9 if 4 < x < 11 else 0), 16.2), (x + .9, 27 - (.6 if 4 < x < 11 else 0), 16.7), '#eye', light=15)
                           for x in (1.5, 2.7, 3.9, 5.1, 6.3, 7.5, 8.7, 9.9, 11.1, 12.3, 13.5)],
                         *[box((x, 20.2 + (.8 if 4 < x < 11 else 0), 16.2), (x + .9, 21.4 + (.8 if 4 < x < 11 else 0), 16.7), '#eye', light=15)
                           for x in (2.3, 3.6, 4.9, 6.2, 7.5, 8.8, 10.1, 11.4, 12.7)],
                         box((.5, 26.4, 16.1), (1.6, 28.4, 16.6), '#eye', light=15), box((14.4, 26.4, 16.1), (15.5, 28.4, 16.6), '#eye', light=15),
                         box((3.5, 29.5, 16.1), (6.5, 30.5, 16.6), '#eye', light=15), box((9.5, 29.5, 16.1), (12.5, 30.5, 16.6), '#eye', light=15),
                         box((4.5, 33.6, 3), (11.5, 34.4, 13), '#blood')]
        parts['arm_left'] = [box((2.5, 2, 7), (5, 19, 9.5), '#skin'), box((2.5, 13, 6.8), (5.2, 18.5, 9.7), '#cloth'),
                             *[box((x, 0, 8.8), (x + .5, 2.5, 11.5), '#bone') for x in (2.6, 3.6, 4.5)]]
        parts['arm_right'] = [box((11, 2, 7), (13.5, 19, 9.5), '#skin'), box((10.8, 13, 6.8), (13.5, 18.5, 9.7), '#cloth'),
                              *[box((x, 0, 8.8), (x + .5, 2.5, 11.5), '#bone') for x in (11.1, 12.1, 13)]]
        parts['left'] = [box((5, 0, 7), (7.5, 10, 9.5), '#cloth'), box((4.8, 0, 7), (7.7, 1.2, 11), '#skin')]
        parts['right'] = [box((8.5, 0, 7), (11, 10, 9.5), '#cloth'), box((8.3, 0, 7), (11.2, 1.2, 11), '#skin')]
    # Narrow claws remain attached to their own hand, rather than being fused to the torso.
    if kind not in ('nemesis', 'smiler'):
        for name in ('arm_left', 'arm_right'):
            hand=parts[name][1]
            lo,hi=hand['from'],hand['to']
            for x in (lo[0]+.5, (lo[0]+hi[0])/2-.35, hi[0]-1.3):
                parts[name].append(box((x, lo[1], hi[2]-.5), (x+.7, lo[1]+1.5, hi[2]+3), '#bone'))
    return parts


def rebase(elements, pivot):
    """Model origin (8,8,8) becomes the server's pivot, for real limb articulation."""
    import copy
    result=copy.deepcopy(elements)
    for element in result:
        for key in ('from','to'):
            element[key]=[round(v-p+8,4) for v,p in zip(element[key],pivot)]
        if 'rotation' in element:
            element['rotation']['origin']=[v-p+8 for v,p in zip(element['rotation']['origin'],pivot)]
    return result
