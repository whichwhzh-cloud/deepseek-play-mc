"""Bake common Winefox bone rotations for the DS render adapter."""
import ast
import copy
import json
import math
import pathlib
import re

BASE = pathlib.Path(__file__).resolve().parent
SOURCE = BASE / 'asset-patch/xixi-action-prototype/animations/extra.animation.json'
CATALOG = BASE / 'src/xixi/interaction/InteractionMod.java'
TARGET = BASE / 'resources/assets/xixi_interaction/ds_poses.json'
PLAYER_PATCH = BASE / 'asset-patch/xixi-player-hug-test'
RATE = 20
BONES = {
    'Root', 'AllBody', 'UpBody', 'DownBody', 'Head',
    'LeftArm', 'LeftForeArm', 'LeftHand', 'RightArm', 'RightForeArm', 'RightHand',
    'LeftLeg', 'LeftLowerLeg', 'LeftFoot', 'RightLeg', 'RightLowerLeg', 'RightFoot',
}


def channel(value, time):
    if isinstance(value, (int, float)):
        return value
    formula = value.replace('query.anim_time', 't').replace('math.sin', 'sin').replace('math.max', 'max')

    def evaluate(node):
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return node.value
        if isinstance(node, ast.Name) and node.id == 't':
            return time
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
            return -evaluate(node.operand)
        if isinstance(node, ast.BinOp):
            a, b = evaluate(node.left), evaluate(node.right)
            if isinstance(node.op, ast.Add):
                return a + b
            if isinstance(node.op, ast.Sub):
                return a - b
            if isinstance(node.op, ast.Mult):
                return a * b
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and not node.keywords:
            args = [evaluate(arg) for arg in node.args]
            if node.func.id == 'sin' and len(args) == 1:
                return math.sin(math.radians(args[0]))
            if node.func.id == 'max' and len(args) == 2:
                return max(args)
        raise ValueError(f'Unsupported animation expression: {value}')

    return evaluate(ast.parse(formula, mode='eval').body)


def sample(track, time):
    if isinstance(track, list):
        return [channel(value, time) for value in track]
    keys = sorted((float(key), value) for key, value in track.items())
    if time <= keys[0][0]:
        return sample(keys[0][1], time)
    for (start, left), (end, right) in zip(keys, keys[1:]):
        if time <= end:
            t = (time-start)/(end-start)
            return [a+(b-a)*t for a, b in zip(sample(left,time), sample(right,time))]
    return sample(keys[-1][1], time)


def bake(action, source):
    duration = source.get('animation_length', 1)
    count = max(2, round(duration*RATE)+1)
    tracks = {}
    for name, bone in source['bones'].items():
        if name not in BONES or 'rotation' not in bone:
            continue
        if name == 'Root' or (action == 'maid_lap' and name == 'Head'):
            # Overall placement is handled by the dispatcher, as in the working DS lap.
            continue
        frames = [sample(bone['rotation'], min(i/RATE,duration)) for i in range(count)]
        tracks[name] = [[round(math.radians(degrees*sign),5) for degrees,sign in zip(frame,(-1,-1,1))]
                        for frame in frames]
    return {'frames':count, 'tracks':tracks}

def write_brush_assets():
    path = PLAYER_PATCH / 'animations/extra.animation.json'
    data = json.loads(path.read_text('utf-8'))
    animations = data['animations']
    for arm, sign in [('Right', 1), ('Left', -1)]:
        suffix = '' if arm == 'Right' else '_left'
        ready = copy.deepcopy(animations['xixi_player_sit'])
        ready['bones'][arm+'Arm'] = {'rotation': [-43, 0, 12*sign]}
        ready['bones'][arm+'ForeArm'] = {'rotation': [-32, 0, 0]}
        stroke = copy.deepcopy(ready)
        stroke['animation_length'] = 1
        stroke['bones'][arm+'Arm']['rotation'] = {
            '0.0': [-43, 0, 12*sign], '0.20': [-67, 0, 16*sign],
            '0.52': [-53, 0, 16*sign], '0.78': [-60, 0, 15*sign], '1.0': [-43, 0, 12*sign],
        }
        animations['xixi_player_brush'+suffix] = stroke
        animations['xixi_player_brush'+suffix+'_ready'] = ready
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n','utf-8')


if __name__ == '__main__':
    animations = json.loads(SOURCE.read_text('utf-8'))['animations']
    actions = dict(re.findall(r'new Action\("([^"]+)", "[^"]+", "([^"]+)"', CATALOG.read_text('utf-8')))
    assert len(actions) == 14
    poses = {action:bake(action, animations[key]) for action,key in actions.items()}
    poses['umbrella_walk'] = bake('umbrella_walk', animations['xixi_test_umbrella_walk'])
    poses['sleep_idle'] = bake('sleep_idle', animations['xixi_test_sleep'])
    for arm, sign in [('Left', -1), ('Right', 1)]:
        key = 'rain_umbrella_'+arm.lower()
        poses[key] = bake(key, {
            'animation_length': 1.2,
            'bones': {
                arm+'Arm': {'rotation': [-69, 0, 13*sign]},
                arm+'ForeArm': {'rotation': [-57, 0, -6*sign]},
            },
        })
    # Preserve the two arm angles that passed the DS hand test; leave walking legs to YSM.
    poses['hand'] = {'frames':2,'tracks':{'LeftArm':[[.052,0,.21]]*2,'LeftForeArm':[[.14,0,0]]*2}}
    poses['native_angry'] = bake('native_angry', {
        'animation_length': 1.2,
        'bones': {
            'Head': {'rotation': {'0': [4, 0, 0], '0.25': [8, -13, -4], '0.55': [5, 11, 3],
                                  '0.9': [7, -5, 1], '1.2': [4, 0, 0]}},
            'LeftArm': {'rotation': [-22, 0, 31]},
            'RightArm': {'rotation': [-22, 0, -31]},
            'LeftForeArm': {'rotation': [-55, 11, -45]},
            'RightForeArm': {'rotation': [-55, -11, 45]},
        },
    })
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(json.dumps(poses, separators=(',',':')), 'utf-8')
    write_brush_assets()
    print(f'Baked {len(actions)} DS interactions, automatic states and brush strokes.')
