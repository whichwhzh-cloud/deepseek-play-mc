"""Read the cheek art from the donor FaceFx1 bone and preserve its texture UVs."""

import json
import math


def rotate(point, pivot, degrees):
    x, y, z = (value - origin for value, origin in zip(point, pivot))
    a, b, c = map(math.radians, degrees)
    y, z = y*math.cos(a)-z*math.sin(a), y*math.sin(a)+z*math.cos(a)
    x, z = x*math.cos(b)+z*math.sin(b), -x*math.sin(b)+z*math.cos(b)
    x, y = x*math.cos(c)-y*math.sin(c), x*math.sin(c)+y*math.cos(c)
    return x+pivot[0], y+pivot[1], z+pivot[2]


def accessory_data(path):
    geometry = json.loads(path.read_text('utf-8-sig'))['minecraft:geometry'][0]
    if (geometry['description']['texture_width'], geometry['description']['texture_height']) != (512, 512):
        raise ValueError('Expected the 512-unit UV layout from 大肥鱼/models/main.json.')
    bones = {bone['name']: bone for bone in geometry['bones']}
    origin_x, origin_y, _ = bones['Mouth']['pivot']
    name = 'FaceFx1'
    quads = []
    for cube in bones[name]['cubes']:
        front = cube['uv'].get('north')
        if front is None:
            continue
        x, y, z = cube['origin']
        w, h, _ = cube['size']
        if w <= 0 or h <= 0:
            continue
        u, v = front['uv']
        du, dv = front['uv_size']
        corners = [(x+w,y,z,u+du,v+dv), (x,y,z,u,v+dv),
                   (x,y+h,z,u,v), (x+w,y+h,z,u+du,v)]
        vertices = []
        for cx, cy, cz, cu, cv in corners:
            cx, cy, _ = rotate((cx,cy,cz),cube.get('pivot',cube['origin']),cube.get('rotation',[0,0,0]))
            vertices.extend(round(value,5) for value in (origin_x-cx,cy-origin_y,cu/512,cv/512))
        quads.append(vertices)
    if not quads:
        raise ValueError(f'No visible front faces in donor bone {name}.')
    return (json.dumps({'blush': quads},separators=(',',':'))+'\n').encode('utf-8')
