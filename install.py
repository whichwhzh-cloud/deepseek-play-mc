"""Local installer: merge owned animation keys, backup, rollback on failure. No game launch."""
import argparse, hashlib, json, os, pathlib, shutil, struct, subprocess, sys, time, uuid, zipfile
BASE=pathlib.Path(__file__).resolve().parent
JAR='xixi-interaction-0.6.1-preview.jar'
CORE_JAR='xixi-interaction-0.6.1-core.jar'
MODELS=('xixi-action-prototype','xixi-player-hug-test')
TOOL_START='/* BEGIN XIXI INTERACTION TOOLS */'
TOOL_END='/* END XIXI INTERACTION TOOLS */'
DISCOVERY_START='/* BEGIN XIXI DISCOVERY */'
DISCOVERY_END='/* END XIXI DISCOVERY */'
DESKTOP_ROUTE="""if(mirrorEnabled&&(event?.type==='desktop-pet.message'||event?.type==='desktop-pet.speech')&&!event?.meta?.memoryInternal){
const outcome=await xixiText(event?.meta?.text);
if(outcome)return host.pushEvent({...event,text:event.text+'\\n【Minecraft 实际执行结果】'+outcome+' 原请求已由游戏直接处理，请根据结果回复。'},options);
event={...event,text:xixiSceneText('desktop',event.text)};
}"""
GAME_ROUTE="""if(text){
const outcome=await xixiText(text);
if(outcome){await push(hostRef,'minecraft.message','【Minecraft 实际执行结果】'+outcome+' 原请求已由游戏直接处理，请根据结果回复。');continue;}
}"""
def sha(data): return hashlib.sha256(data).hexdigest()
def inside(root,relative):
    p=(root/relative).resolve()
    if not p.is_relative_to(root.resolve()): raise ValueError('Path escapes instance: '+str(relative))
    return p
def atomic(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    tmp=path.with_name('.xixi-'+uuid.uuid4().hex[:12]+'.tmp')
    try:
        tmp.write_bytes(data);os.replace(tmp,path)
    finally:
        if tmp.exists(): tmp.unlink()
def plan(instance,package=BASE,sticker_source=None,face_source=None):
    result={}
    full=(package/'asset-patch').is_dir()
    for model in MODELS if full else ():
        for rel in ('ysm.json','animations/extra.animation.json'):
            target='config/yes_steve_model/custom/'+model+'/'+rel
            current=json.loads(inside(instance,target).read_text('utf-8-sig'))
            patch=json.loads((package/'asset-patch'/model/rel).read_text('utf-8-sig'))
            if rel=='ysm.json':
                dest=current.setdefault('properties',{}).setdefault('extra_animation',{})
                src=patch['properties']['extra_animation']
            else: dest=current['animations'];src=patch['animations']
            for key,value in src.items():
                if key.startswith('xixi_'): dest[key]=value
            result[target]=(json.dumps(current,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    jar_name=JAR if full else CORE_JAR
    jar=(package/jar_name).read_bytes()
    manifest=package/'SHA256SUMS.json'
    if manifest.exists():
        for name,expected in json.loads(manifest.read_text('utf-8')).items():
            p=inside(package,name)
            if sha(p.read_bytes())!=expected: raise ValueError('Package checksum mismatch: '+name)
    result['mods/'+jar_name]=jar
    if sticker_source is not None:
        atlas=sticker_source.read_bytes()
        if atlas[:16]!=b'\x89PNG\r\n\x1a\n\0\0\0\rIHDR' or len(atlas)<24 or struct.unpack('>II',atlas[16:24]) not in ((512,512),(1024,1024)):
            raise ValueError('Expected the 512 or 1024 pixel square PNG atlas from the 大肥鱼 model.')
        result['config/xixi-sticker-atlas.png']=atlas
    if face_source is not None:
        from prepare_face_accessories import accessory_data
        result['config/xixi-face-accessories.json']=accessory_data(face_source)
    # Only this project's interaction jars; old pair-actions and unrelated mods remain intact.
    for old in (instance/'mods').glob('xixi-interaction-*.jar'):
        if old.name!=jar_name: result['mods/'+old.name]=None
    return result
def plan_coopanion(extension,package):
    manifest=json.loads((extension/'package.json').read_text('utf-8-sig'))
    if manifest.get('name')!='cortico-world-minecraft':
        raise ValueError('Expected the Coopanion Minecraft extension.')
    entry=manifest['main']
    path=inside(extension,entry)
    original=path.read_bytes()
    source=original.decode('utf-8')
    for start_marker,end_marker,filename,anchor,after in (
        (TOOL_START,TOOL_END,'coopanion-tools.mjs','const specs=[',True),
        (DISCOVERY_START,DISCOVERY_END,'coopanion-discovery.mjs','async function lua(code) {',False),
    ):
        start,end=source.find(start_marker),source.find(end_marker)
        if start!=-1 or end!=-1:
            if not 0<=start<end: raise ValueError('Malformed Xixi markers in the Coopanion extension.')
            source=source[:start]+source[end+len(end_marker):]
        if source.count(anchor)!=1:
            raise ValueError('Could not locate the Xixi tools in the Coopanion Minecraft extension.')
        snippet=(package/filename).read_text('utf-8').strip()
        block=start_marker+'\n'+snippet+'\n'+end_marker
        source=source.replace(anchor,anchor+block if after else block+anchor,1)
    for marker,body,anchor,replace in (
        ('DESKTOP',DESKTOP_ROUTE,'const result=await host.pushEvent(event,options);',False),
        ('GAME',GAME_ROUTE,"if(text)await push(hostRef,'minecraft.message'",False),
        ('SCENE',"text:xixiSceneText(type,text),meta:{channel:'minecraft'}","text,meta:{channel:'minecraft'}",True),
        ('SENSE','noteXixiSeen();','const [hp,max,hunger,air,water,lava,count,enemy,distance,ownerHp,ownerMax]=',False),
    ):
        start_marker=f'/* BEGIN XIXI {marker} ROUTE */'
        end_marker=f'/* END XIXI {marker} ROUTE */'
        start,end=source.find(start_marker),source.find(end_marker)
        if start!=-1 or end!=-1:
            if not 0<=start<end: raise ValueError('Malformed Xixi request route.')
            source=source[:start]+(anchor if replace else '')+source[end+len(end_marker):]
        if source.count(anchor)!=1: raise ValueError('Could not find the Coopanion message handler.')
        source=source.replace(anchor,start_marker+body+end_marker+('' if replace else anchor),1)
    output=source.encode('utf-8')
    return {(True,entry):output} if output!=original else {}

def java_running():
    if os.name!='nt': return False
    output=subprocess.run(
        ['powershell','-NoProfile','-NonInteractive','-Command',
         'Get-Process java,javaw -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id; exit 0'],
        capture_output=True,text=True,check=True).stdout
    return bool(output.strip())
def install(instance,package=BASE,dry_run=False,check_running=True,writer=atomic,sticker_source=None,face_source=None,coopanion=None):
    instance=instance.resolve()
    changes={(False,rel):data for rel,data in plan(instance,package,sticker_source,face_source).items()}
    if coopanion is not None:
        coopanion=coopanion.resolve()
        changes.update(plan_coopanion(coopanion,package))
    if dry_run:
        return {'dry_run':True,'files':[str(inside(coopanion,rel)) if external else rel for external,rel in changes]}
    if check_running and java_running(): raise RuntimeError('Close Minecraft and running Java programs before installation.')
    backup=package/'backups'/(time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8])
    backup.mkdir(parents=True)
    before={};records=[]
    for index,((external,rel),data) in enumerate(changes.items()):
        target=inside(coopanion if external else instance,rel)
        old=target.read_bytes() if target.exists() else None
        before[external,rel]=old
        saved='data/'+str(index)+'.bin'
        if old is not None: atomic(inside(backup,saved),old)
        records.append({'path':rel,'coopanion':external,'backup':saved,'before':sha(old) if old is not None else None,'after':sha(data) if data is not None else None})
    record={'instance':str(instance),'files':records,'completed':False}
    if coopanion is not None: record['coopanion']=str(coopanion)
    atomic(backup/'transaction.json',json.dumps(record,indent=2).encode())
    applied=[]
    try:
        for (external,rel),data in changes.items():
            target=inside(coopanion if external else instance,rel);applied.append((external,rel))
            if data is None:
                if target.exists():target.unlink()
            else:writer(target,data)
        record['completed']=True
        atomic(backup/'transaction.json',json.dumps(record,indent=2).encode())
    except Exception:
        errors=[]
        for external,rel in reversed(applied):
            try:
                target=inside(coopanion if external else instance,rel)
                current=target.read_bytes() if target.exists() else None
                if current==before[external,rel]:continue
                if before[external,rel] is None:
                    if target.exists():target.unlink()
                else:atomic(target,before[external,rel])
            except Exception as ex:errors.append(str(ex))
        if errors: raise RuntimeError('Rollback incomplete; backup: '+str(backup)+'; '+str(errors))
        raise
    return {'installed':True,'backup':str(backup),'files':len(changes)}
def rollback(backup,check_running=True):
    record=json.loads((backup/'transaction.json').read_text('utf-8'));instance=pathlib.Path(record['instance']).resolve()
    coopanion=pathlib.Path(record['coopanion']).resolve() if 'coopanion' in record else None
    if not record['completed']:raise ValueError('Transaction did not complete; inspect backup before manual recovery')
    if check_running and java_running():raise RuntimeError('Close Minecraft before rollback')
    for r in record['files']:
        # Refuse to overwrite modifications made after installation.
        p=inside(coopanion if r.get('coopanion') else instance,r['path']);actual=sha(p.read_bytes()) if p.exists() else None
        if actual!=r['after']:raise ValueError('File changed since installation: '+r['path'])
        if r['before'] is not None and sha(inside(backup,r['backup']).read_bytes())!=r['before']:raise ValueError('Backup checksum mismatch')
    for r in reversed(record['files']):
        target=inside(coopanion if r.get('coopanion') else instance,r['path'])
        if r['before'] is None:
            if target.exists():target.unlink()
        else:atomic(target,inside(backup,r['backup']).read_bytes())
    return {'rolled_back':True,'instance':str(instance)}
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--instance',type=pathlib.Path,default=os.environ.get('XIXI_MINECRAFT_INSTANCE'));parser.add_argument('--dry-run',action='store_true');parser.add_argument('--rollback',type=pathlib.Path)
    parser.add_argument('--sticker-source',type=pathlib.Path,help='Local 大肥鱼 textures/texture.png.')
    parser.add_argument('--face-source',type=pathlib.Path,help='Local 大肥鱼 models/main.json.')
    parser.add_argument('--coopanion-extension',type=pathlib.Path,help='Path of the Coopanion Minecraft extension.')
    parser.add_argument('--skip-coopanion',action='store_true',help='Only update the Minecraft mod and its assets.')
    args=parser.parse_args()
    if args.instance is None and args.rollback is None:parser.error('--instance must point to the Minecraft instance')
    coopanion=None if args.skip_coopanion else args.coopanion_extension
    source=args.sticker_source
    face_source=args.face_source
    if face_source is None and source is not None:
        model=source.parent.parent/'models/main.json'
        if model.is_file():face_source=model
    try: print(json.dumps(rollback(args.rollback) if args.rollback else install(args.instance,dry_run=args.dry_run,sticker_source=source,face_source=face_source,coopanion=coopanion),ensure_ascii=True,indent=2))
    except Exception as ex: print('INSTALL FAILED: '+str(ex),file=sys.stderr);sys.exit(1)
