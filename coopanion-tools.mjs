// Xixi interaction tools. Installed inside the Minecraft world's existing specs array.
{
  name: 'minecraft_expression',
  description: '让游戏里的汐自主显示、更换或收起 DS 鲸鱼娘的表情贴纸，也可以单独脸红。可在对话或双人互动过程中按情境主动使用。贴纸编号：0 收起，1 吃白饭，2 拜托，3 爱心，4 晚饭，5 睁大眼，6 符纸，7 流汗，8 发呆，9 小丑。根据实际执行结果回答。',
  parameters: {
    type: 'object',
    properties: {
      sticker: {type: 'integer', minimum: 0, maximum: 9},
      blush: {type: 'boolean', description: '为 true 时脸红，可与贴纸同时显示'},
      seconds: {type: 'integer', minimum: 1, maximum: 12, description: '默认 4 秒'},
    },
  },
  handler: args => {
    const {sticker, blush, seconds = 4} = args;
    if (!Number.isInteger(seconds) || seconds < 1 || seconds > 12) return '显示时间须为 1 到 12 秒。';
    const actions = [];
    if (sticker !== undefined) {
      if (!Number.isInteger(sticker) || sticker < 0 || sticker > 9) return '贴纸编号须为 0 到 9。';
      actions.push(`print(xixi_interaction.expression.sticker(${sticker}, ${seconds}))`);
    }
    if (blush === true) actions.push(`print(xixi_interaction.expression.blush(${seconds}))`);
    if (!actions.length) return '请选择贴纸编号或脸红。';
    return call('lua', {companion: 'xixi', code: actions.join('\n')});
  },
},
{
  name: 'minecraft_model_action',
  description: '查询、播放或结束汐的 DS 鲸鱼娘原生单人动作。比如点头、摇头、鼓掌、招手、鸭子坐，按列表里的 ID 播放。空闲时可根据当下情境自主使用；双人动作继续用互动邀请。',
  parameters: {
    type: 'object',
    properties: {
      operation: {type: 'string', enum: ['list', 'play', 'stop']},
      action: {type: 'string', description: 'play 时用 list 返回的完整动作 ID'},
      seconds: {type: 'integer', minimum: 0, maximum: 30, description: '0 使用动作原本的时长'},
    },
    required: ['operation'],
  },
  handler: args => {
    let code;
    switch (args.operation) {
      case 'list': code = 'local a=xixi_interaction.actions.list(); if #a==0 then return "DS 动作列表暂无内容，请确认汐正在使用 DS 鲸鱼娘" end; for _,x in ipairs(a) do print(x) end'; break;
      case 'stop': code = 'return xixi_interaction.actions.stop()'; break;
      case 'play': {
        const id = typeof args.action === 'string' ? args.action.trim() : '';
        const seconds = args.seconds ?? 0;
        if (!id || id.length > 96 || /[\x00-\x1f]/.test(id)) return '请先查询列表并选择动作 ID。';
        if (!Number.isInteger(seconds) || seconds < 0 || seconds > 30) return '动作时长须为 0 到 30 秒。';
        code = `return xixi_interaction.actions.play(${JSON.stringify(id)}, ${seconds})`;
        break;
      }
      default: return '请选择 list、play 或 stop。';
    }
    return call('lua', {companion: 'xixi', code});
  },
},
