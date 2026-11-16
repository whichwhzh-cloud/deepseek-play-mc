{
  const gameTool = specs.find(tool => tool.name === 'minecraft_do');
  if (gameTool) {
    const xixiHelp = '汐的 DS 鲸鱼娘：贴纸与模型自带动作可直接通过 Minecraft Lua 调用。显示爱心：return ysm.xixi_expression.sticker(3, 8)；收起：return ysm.xixi_expression.sticker(0, 4)；单独脸红：return ysm.xixi_expression.blush(4)。查全部原生动作：return ysm.xixi_actions.list()；按返回的 ID 播放，例如 return ysm.xixi_actions.play("点头", 0)；停止：return ysm.xixi_actions.stop()。原生动作请用这个列表和原样的 ID。';
    const routeHelp = '寻路遇到浅水、岸边或草地台阶，先看落脚高度。优先用带 y 的短距离落脚点，陆地沿台阶正向上下，入水后先找就近可浮起且能上岸的位置。路线失败后最多换一条短路线重试，仍失败就报告卡点。';
    const flightHelp = '玩家明确要求汐飞行时，用 return xixi_interaction.flight.takeoff() 原地升高三格，用 return xixi_interaction.flight.to(x, y, z) 飞到脚下坐标，用 return xixi_interaction.flight.land() 垂直降落。需要伙伴本人已经处于创造模式。结果的 reached 为 true 才算到达。';
    gameTool.description = xixiHelp + ' ' + flightHelp + ' ' + routeHelp + ' ' + gameTool.description;
    const invoke = gameTool.handler;
    gameTool.handler = async args => {
      const result = await invoke(args);
      const code = String(args.code ?? '');
      const asksForHelp = /numen\.api\.help\s*\(\s*["'](?:numen|ysm(?:\.model(?:\.emote)?)?)["']/.test(code)
        || /numen\.mc\.run\s*\(\s*["']\/?xixi\s+help["']/.test(code);
      const usesSlots = /ysm\.model\.emote\s*\(/.test(code) && /\bextra\d+\b/.test(code);
      if (asksForHelp || usesSlots) {
        return xixiHelp + '\n' + result;
      }
      if (/\bnumen\.move\.(?:to|go)\s*\(/.test(code)
          && /no_path|BOXED_IN|OUT_OF_BREATH|STUCK|timed?\s*out|超时|执行时间过长/.test(String(result))) {
        return result + '\n' + routeHelp;
      }
      return result;
    };
  }
}

async function xixiText(text) {
  text = String(text ?? '').trim();
  if (!text || text.length > 96 || /[\x00-\x1f]/.test(text)) return null;
  if (!/^(?:[/]xixi|xixi|小?汐|请|麻烦你|给我|显示|发|用|放|来|做|试|播放|表演|列出|查看|说说|查|看看|收起|关闭|取消|单独|脸红)|^(?:鸭子坐|侧躺|趴|点头|摇头|鼓掌|招手[12]?|卖萌|思考|怒了)[。！!]*$/.test(text)) return null;
  const code = `return xixi_interaction.control.chat(${JSON.stringify(text)})`;
  let response;
  for (let attempt = 0; attempt < 3; attempt++) {
    response = String(await call('lua', {companion: 'xixi', code}));
    if (!/already running|another program|正在执行|busy/i.test(response)) break;
    if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 250));
  }
  const match = /^returned:\s*(.+)$/m.exec(response);
  return match && match[1].trim() !== 'XIXI_SKIP' ? match[1].trim() : null;
}

let xixiLastSeen = 0;
function noteXixiSeen() { xixiLastSeen = Date.now(); }
function xixiSceneText(type, text) {
  if (type === 'desktop' && Date.now() - xixiLastSeen > 20000) return text;
  if (!['desktop','minecraft.message','minecraft.event','minecraft.observe','minecraft.danger','minecraft.owner_danger'].includes(type)
      || text.startsWith('【Minecraft 实际执行结果】')) return text;
  return text + '\n【汐在 Minecraft 的表达】你可以按当前对话或场景主动做一个贴合的反应，无需用户点名。用 minecraft_expression 选择贴纸或脸红；空闲时用 minecraft_model_action 选实际菜单中的原生动作。譬如开心或被摸头时用爱心，认同时点头，疑惑时思考。是否用、用哪个由你决定。已有任务或危险时先处理，双人互动中可用贴纸。少而自然，根据工具结果判断是否提交成功。';
}
