"""Original biological horror meshes, authored in pixels before joint rebasing. Forward is +Z."""

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
        parts['head'] += [box((2, 20, 10), (14, 25, 15), '#wound'),
                          box((4, 21, 14), (12, 23, 16), '#eye', light=3)]
        parts['head'] += [box((x, 23, 14), (x+1, 26, 16), '#bone') for x in (3, 6, 9, 12)]
        parts['body'] += [box((1.5, 15, 8), (5, 21, 12), '#eye', light=2),
                          box((11, 14, 8), (14.5, 20, 12), '#eye', light=2),
                          box((6, 18, 12), (10, 23, 14), '#wound')]
    elif kind == 'jumper':
        parts['body'] = [box((3, 7, 3), (13, 18, 11)), box((6, 9, 1), (10, 21, 5), '#bone')]
        parts['head'] = [box((3, 14, 9), (13, 23, 17), front='#face'), box((4, 13, 15), (12, 17, 20), '#wound')]
        parts['arm_left'] = [box((-1, 3, 5), (3, 19, 11)), box((-2, 0, 7), (4, 5, 14), '#wound')]
        parts['arm_right'] = [box((13, 3, 5), (17, 19, 11)), box((12, 0, 7), (18, 5, 14), '#wound')]
        parts['left'] = [box((1, 0, 4), (7, 4, 14)), box((2, 3, 7), (7, 10, 12))]
        parts['right'] = [box((9, 0, 4), (15, 4, 14)), box((9, 3, 7), (14, 10, 12))]
    elif kind == 'crawler':
        parts['body'] = [box((3, 3, 0), (13, 10, 14)), box((6, 9, -1), (10, 12, 11), '#bone')]
        parts['head'] = [box((3, 4, 12), (13, 12, 20), front='#face'), box((6, 5, 18), (10, 7, 24), '#wound')]
        parts['arm_left'] = [box((-1, 1, 7), (3, 7, 18)), box((-2, 0, 16), (4, 3, 22), '#wound')]
        parts['arm_right'] = [box((13, 1, 7), (17, 7, 18)), box((12, 0, 16), (18, 3, 22), '#wound')]
        parts['left'] = [box((2, 0, -6), (7, 4, 4))]
        parts['right'] = [box((9, 0, -6), (14, 4, 4))]
    elif kind == 'stalker':
        parts['body'] = [box((5, 11, 6), (11, 24, 10)), box((7, 13, 3), (9, 27, 6), '#bone')]
        parts['head'] = [box((5, 23, 5), (11, 32, 11), front='#face'), box((5, 22, 10), (11, 27, 14), '#wound')]
        parts['arm_left'] = [box((1, 3, 7), (4, 24, 10)), box((.5, 1, 8), (4, 5, 13), '#wound')]
        parts['arm_right'] = [box((12, 1, 7), (15, 24, 10)), box((12, 0, 8), (15.5, 4, 13), '#wound')]
        parts['left'] = [box((4, 0, 7), (7, 12, 10)), box((3, 0, 7), (7, 2, 14))]
        parts['right'] = [box((9, 0, 7), (12, 12, 10)), box((9, 0, 7), (13, 2, 14))]
        parts['body'] += [box((3, y, 10), (13, y+1, 12), '#bone') for y in (15, 18, 21)]
    elif kind == 'bloated':
        parts['body'] = [box((0, 5, 2), (16, 22, 16)), box((4, 8, 15), (12, 19, 18), '#wound'),
                         box((1, 16, 13), (5, 22, 18), '#eye', light=2)]
        parts['head'] = [box((4, 20, 5), (12, 28, 13), front='#face')]
        parts['arm_left'] = [box((-3, 5, 5), (2, 21, 12)), box((-3, 4, 10), (2, 9, 14), '#wound')]
        parts['arm_right'] = [box((14, 6, 5), (19, 21, 12)), box((14, 4, 10), (19, 9, 14), '#wound')]
        parts['left'] = [box((3, 0, 6), (7, 7, 11))]
        parts['right'] = [box((9, 0, 6), (13, 7, 11))]
    elif kind in ('breaker', 'abomination'):
        boss = kind == 'abomination'
        parts['body'] = [box((0, 9, 3), (16, 25, 13)), box((5, 10, 12), (12, 21, 16), '#wound')]
        parts['head'] = [box((3, 23, 4), (13, 32, 14), front='#face'), box((4, 21, 12), (12, 25, 16), '#wound')]
        parts['arm_left'] = [box((-4, 3, 4), (2, 25, 12)), box((-5, 0, 6), (3, 7, 15), '#bone')]
        parts['arm_right'] = [box((14, 2, 3), (22 if boss else 20, 26, 14), '#wound'),
                              box((13, 0, 6), (23 if boss else 21, 8, 17), '#bone')]
        parts['left'] = [box((1, 0, 5), (7, 11, 12)), box((0, 0, 4), (7, 3, 15))]
        parts['right'] = [box((9, 0, 5), (15, 11, 12)), box((9, 0, 4), (16, 3, 15))]
        parts['body'] += [box((x, 20, 0), (x+2, 29 if boss else 26, 5), '#bone') for x in (1, 6, 11)]
        if boss:
            parts['body'] += [box((2, 15, 12), (6, 20, 17), '#eye', light=2), box((9, 18, 13), (14, 23, 18), '#eye', light=2)]
    # Narrow claws remain attached to their own hand, rather than being fused to the torso.
    for name in ('arm_left', 'arm_right'):
        hand=parts[name][-1];lo,hi=hand['from'],hand['to']
        for x in (lo[0]+.5, hi[0]-1.3):
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
