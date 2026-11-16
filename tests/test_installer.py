import importlib.util,json,pathlib,tempfile,unittest,sys
base=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('installer',base/'install.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class InstallerTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name);self.instance=self.root/'game';self.package=self.root/'package';self.package.mkdir()
        (self.package/m.JAR).write_bytes(b'test jar')
        for model in m.MODELS:
            for rel in ['ysm.json','animations/extra.animation.json']:
                d={'metadata':{'name':'custom-name'},'properties':{'extra_animation':{'user_pose':'keep'}},'animations':{'user_pose':{'loop':True}}}
                p=self.instance/'config/yes_steve_model/custom'/model/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d))
                q=self.package/'asset-patch'/model/rel;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(json.dumps({'properties':{'extra_animation':{'xixi_new':'new','unrelated':'ignored'}},'animations':{'xixi_new':{'loop':True},'unrelated':{}}}))
        (self.instance/'mods').mkdir();(self.instance/'mods/xixi-interaction-0.2.0-prototype.jar').write_bytes(b'old');(self.instance/'mods/keep.jar').write_bytes(b'other')
    def tearDown(self):self.tmp.cleanup()
    def snapshot(self):return {str(p.relative_to(self.instance)):p.read_bytes() for p in self.instance.rglob('*') if p.is_file()}
    def test_dry_run(self):
        before=self.snapshot();m.install(self.instance,self.package,True);self.assertEqual(before,self.snapshot());self.assertFalse((self.package/'backups').exists())
    def test_core_install_without_models(self):
        package=self.root/'core'
        package.mkdir()
        (package/m.CORE_JAR).write_bytes(b'core jar')
        instance=self.root/'new-game'
        (instance/'mods').mkdir(parents=True)
        unrelated=instance/'mods/keep.jar'
        unrelated.write_bytes(b'other')
        result=m.install(instance,package,check_running=False)
        self.assertEqual((instance/'mods'/m.CORE_JAR).read_bytes(),b'core jar')
        self.assertFalse((instance/'config').exists())
        m.rollback(pathlib.Path(result['backup']),False)
        self.assertEqual(list((instance/'mods').iterdir()),[unrelated])
    def test_merge_and_rollback(self):
        before=self.snapshot();r=m.install(self.instance,self.package,check_running=False)
        p=self.instance/'config/yes_steve_model/custom'/m.MODELS[0]/'ysm.json';d=json.loads(p.read_text('utf-8'))
        self.assertEqual(d['metadata']['name'],'custom-name');self.assertEqual(d['properties']['extra_animation'],{'user_pose':'keep','xixi_new':'new'})
        self.assertFalse((self.instance/'mods/xixi-interaction-0.2.0-prototype.jar').exists())
        self.assertEqual((self.instance/'mods/keep.jar').read_bytes(),b'other')
        m.rollback(pathlib.Path(r['backup']),False);self.assertEqual(before,self.snapshot())
    def test_failed_write_restores_all(self):
        before=self.snapshot();calls=0
        def fail(p,d):
            nonlocal calls;calls+=1
            if calls==3:raise OSError('injected disk failure')
            m.atomic(p,d)
        with self.assertRaises(OSError):m.install(self.instance,self.package,check_running=False,writer=fail)
        self.assertEqual(calls,3)
        self.assertEqual(before,self.snapshot())
    def test_rollback_protects_new_edits(self):
        r=m.install(self.instance,self.package,check_running=False);(self.instance/'mods'/m.JAR).write_bytes(b'new edit')
        with self.assertRaises(ValueError):m.rollback(pathlib.Path(r['backup']),False)
    def test_path_escape_rejected(self):
        with self.assertRaises(ValueError):m.inside(self.instance,'../elsewhere')
    def test_corrupt_package_rejected_before_writes(self):
        (self.package/'SHA256SUMS.json').write_text(json.dumps({m.JAR:'invalid'}));before=self.snapshot()
        with self.assertRaises(ValueError):m.install(self.instance,self.package,check_running=False)
        self.assertEqual(before,self.snapshot())
    def test_coopanion_active_entry_install_and_rollback(self):
        extension=self.root/'coopanion'
        extension.mkdir()
        (extension/'package.json').write_text(json.dumps({'name':'cortico-world-minecraft','main':'./current.mjs'}))
        inactive=extension/'index.mjs'
        active=extension/'current.mjs'
        original=(b"const specs=[{name:'minecraft_do'}];\r\nasync function lua(code) {}\r\n"
                  b"const result=await host.pushEvent(event,options);\r\n"
                  b"if(text)await push(hostRef,'minecraft.message',text);\r\n"
                  b"const event={text,meta:{channel:'minecraft'}};\r\n"
                  b"const [hp,max,hunger,air,water,lava,count,enemy,distance,ownerHp,ownerMax]=sample;\r\n")
        inactive.write_bytes(original)
        active.write_bytes(original)
        (self.package/'coopanion-tools.mjs').write_bytes((base/'coopanion-tools.mjs').read_bytes())
        (self.package/'coopanion-discovery.mjs').write_bytes((base/'coopanion-discovery.mjs').read_bytes())
        dry_run=m.install(self.instance,self.package,dry_run=True,coopanion=extension)
        self.assertIn(str(active.resolve()),dry_run['files'])
        self.assertEqual(active.read_bytes(),original)
        result=m.install(self.instance,self.package,check_running=False,coopanion=extension)
        patched=active.read_text('utf-8')
        self.assertIn("name: 'minecraft_expression'",patched)
        self.assertIn("name: 'minecraft_model_action'",patched)
        self.assertIn('ysm.xixi_actions.list()',patched)
        self.assertIn('BEGIN XIXI DESKTOP ROUTE',patched)
        self.assertIn('BEGIN XIXI GAME ROUTE',patched)
        self.assertIn('text:xixiSceneText(type,text)',patched)
        self.assertIn('noteXixiSeen();',patched)
        self.assertEqual(inactive.read_bytes(),original)
        self.assertEqual(m.plan_coopanion(extension,self.package),{})
        m.rollback(pathlib.Path(result['backup']),False)
        self.assertEqual(active.read_bytes(),original)
if __name__=='__main__':unittest.main()
