import json,math,pathlib,re
base=pathlib.Path(__file__).resolve().parent
source=(base/'src/xixi/interaction/InteractionMod.java').read_text('utf-8')
rows=re.findall(r'new Action\("([^"]+)", "[^"]+", "([^"]+)", (?:true|false), "([^"]+)"\)',source)
assert len(rows)==14 and len({r[0] for r in rows})==14
actions={r[0] for r in rows}
client=(base/'src/xixi/interaction/InteractionClient.java').read_text('utf-8')
tabs=re.findall(r'(?:AFFECTION|REST|TRAVEL)\("[^"]+", ([^\n]*)\)[,;]',client)
menu_actions=[action for tab in tabs for action in re.findall(r'"([^"]+)"',tab)]
assert len(menu_actions)==len(actions) and set(menu_actions)==actions
poses=json.loads((base/'resources/assets/xixi_interaction/ds_poses.json').read_text('utf-8'))
assert set(poses)==actions|{'umbrella_walk','native_angry','sleep_idle','rain_umbrella_left','rain_umbrella_right'}
for action,clip in poses.items():
    frames,tracks=clip['frames'],clip['tracks']
    assert frames>=2 and tracks and 'Root' not in tracks,action
    for bone,rotations in tracks.items():
        assert len(rotations)==frames and all(len(xyz)==3 and all(map(math.isfinite,xyz)) for xyz in rotations),(action,bone)
assert 'Head' not in poses['maid_lap']['tracks']
assert poses['hand']['tracks']=={'LeftArm':[[.052,0,.21]]*2,'LeftForeArm':[[.14,0,0]]*2}
assert poses['native_angry']['tracks'].keys()=={'Head','LeftArm','RightArm','LeftForeArm','RightForeArm'}
assert len({tuple(frame) for frame in poses['native_angry']['tracks']['Head']})>4
assert set(poses['rain_umbrella_left']['tracks'])=={'LeftArm','LeftForeArm'}
assert set(poses['rain_umbrella_right']['tracks'])=={'RightArm','RightForeArm'}
assert {'Head','LeftArm','RightArm'}<=set(poses['sleep_idle']['tracks'])
print('PASS: all 14 actions reachable in the three tabs and backed by DS pose data')
data={}
for model in ['xixi-action-prototype','xixi-player-hug-test']:
    p=base/'asset-patch'/model
    data[model]=json.loads((p/'animations/extra.animation.json').read_text('utf-8'))['animations']
    meta=json.loads((p/'ysm.json').read_text('utf-8'))
    assert 'metadata' in meta and 'files' in meta
for action,companion,owner in rows:
    assert companion in data['xixi-action-prototype'],companion
    assert owner=='stop' or owner in data['xixi-player-hug-test'],owner
for suffix,arm in [('', 'RightArm'), ('_left', 'LeftArm')]:
    player=data['xixi-player-hug-test']
    stroke=player['xixi_player_brush'+suffix]['bones']
    ready=player['xixi_player_brush'+suffix+'_ready']['bones']
    assert stroke[arm]['rotation']['0.0']==ready[arm]['rotation']
    assert stroke[arm]['rotation']['1.0']==ready[arm]['rotation']
    assert stroke[arm]['rotation']['0.20']!=ready[arm]['rotation']
for name in ['hand_walk','umbrella_walk']:
    assert 'xixi_test_'+name in data['xixi-action-prototype']
for action,base_pose in [('lean','sit'),('maid_lap','maid_lap')]:
    player=data['xixi-player-hug-test']
    stroke=player[f'xixi_player_{action}_pat']['bones']
    for key,bone in player[f'xixi_player_{base_pose}']['bones'].items():
        if key not in ('RightArm','RightForeArm'):
            assert stroke[key]==bone,(action,key)
for model,keys in [
    ('xixi-action-prototype',('xixi_test_maid_lap','xixi_test_player_lap')),
    ('xixi-player-hug-test',('xixi_player_maid_lap','xixi_player_lap')),
]:
    for key in keys:
        pose=data[model][key]['bones']
        assert isinstance(pose['Head']['rotation'],list) and len(pose['Head']['rotation'])==3,key
        assert 'AllHead' not in pose,key
for model,key in [
    ('xixi-action-prototype','xixi_test_maid_lap'),
    ('xixi-player-hug-test','xixi_player_lap'),
]:
    root=data[model][key]['bones']['Root']
    assert root['rotation']==[0,0,0] and root['position']==[0,0,0],key
for mixin in json.loads((base/'resources/xixi-interaction.mixins.json').read_text('utf-8'))['mixins']+json.loads((base/'resources/xixi-interaction.mixins.json').read_text('utf-8'))['client']:
    assert (base/'src/xixi/interaction/mixin'/f'{mixin}.java').is_file()
print('PASS: 14 unique menu actions, 2 walking variants, owner animations and mixin manifest')
item=json.loads((base/'resources/assets/xixi_interaction/items/umbrella.json').read_text('utf-8'))
assert item['model']['model']=='xixi_interaction:item/umbrella'
model=json.loads((base/'resources/assets/xixi_interaction/models/item/umbrella.json').read_text('utf-8'))
for element in model['elements']:
    assert all(-16<=v<=32 for v in element['from']+element['to'])
    assert all(a<b for a,b in zip(element['from'],element['to']))
recipe=json.loads((base/'resources/data/xixi_interaction/recipe/umbrella.json').read_text('utf-8'))
assert recipe['result']['id']=='xixi_interaction:umbrella'
assert (base/'resources/assets/xixi_interaction/textures/item/umbrella.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
print('PASS: umbrella item/model/recipe/texture references and geometry bounds')
