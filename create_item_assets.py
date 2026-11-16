"""Generate the original block-style umbrella prop and recipe; no third-party artwork."""
import json,pathlib,struct,zlib
base=pathlib.Path(__file__).resolve().parent/'resources'
def save(rel,obj):
    p=base/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2),'utf-8')
save('assets/xixi_interaction/items/umbrella.json',{'model':{'type':'minecraft:model','model':'xixi_interaction:item/umbrella'}})
save('data/xixi_interaction/recipe/umbrella.json',{'type':'minecraft:crafting_shaped','category':'equipment','pattern':['WWW',' S ',' S '],'key':{'W':'#minecraft:wool','S':'minecraft:stick'},'result':{'id':'xixi_interaction:umbrella','count':1}})
save('assets/xixi_interaction/lang/zh_cn.json',{'item.xixi_interaction.umbrella':'汐的伞'})
save('assets/xixi_interaction/lang/en_us.json',{'item.xixi_interaction.umbrella':"Xi's Umbrella"})
elements=[]
def cube(start,end,uv):
    elements.append({'from':start,'to':end,'faces':{face:{'texture':'#canopy','uv':uv} for face in ['north','south','east','west','up','down']}})
cube([7.5,-8,7.5],[8.5,29,8.5],[0,0,2,2])
cube([7.5,-9,7.5],[12,-7,8.5],[0,0,2,2])
for extent,y in [(16,24),(13,25),(10,26),(7,27),(4,28)]:cube([8-extent,y,8-extent],[8+extent,y+1,8+extent],[0,0,16,16])
display={}
for hand in ['righthand','lefthand']:
    display['thirdperson_'+hand]={'rotation':[0,0,0],'translation':[0,2,0],'scale':[.65,.65,.65]}
    display['firstperson_'+hand]={'rotation':[0,0,0],'translation':[0,0,0],'scale':[.5,.5,.5]}
display['gui']={'rotation':[20,30,0],'translation':[0,-3,0],'scale':[.35,.35,.35]}
display['ground']={'rotation':[0,0,0],'translation':[0,4,0],'scale':[.25,.25,.25]}
display['fixed']={'rotation':[0,0,0],'translation':[0,-3,0],'scale':[.4,.4,.4]}
save('assets/xixi_interaction/models/item/umbrella.json',{'textures':{'canopy':'xixi_interaction:item/umbrella','particle':'xixi_interaction:item/umbrella'},'elements':elements,'display':display})
def chunk(tag,data):return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
raw=b''.join(b'\0'+b''.join(bytes((35,72,90,255) if x<2 and y<2 else ((89,183,211,255) if (x//4+y//4)%2 else (201,235,241,255))) for x in range(16)) for y in range(16))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
p=base/'assets/xixi_interaction/textures/item/umbrella.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(png)
print('Generated original umbrella item model, texture, translations and recipe')
