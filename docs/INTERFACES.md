# 接入接口

## Numen 自带 AI 和外接 MCP

游戏内原生 AI 可直接发现 `xixi_interaction` Lua 命名空间。外部控制器使用 Numen 内置的 MCP HTTP 服务。按实例的 `config/numen/mcp_server.json` 设置，示例端点为 `http://127.0.0.1:8765/mcp`。如果启用鉴权，在客户端添加对应的 Bearer token，密钥留在本机。启动游戏、进入世界、召唤伙伴后再连接。

连接后用 `tools/list` 读取当前 MCP 会话的工具和 schema。先用 `list_companions` 查实际伙伴名或 ID，用 `lua` 传入该伙伴及代码。MCP 客户端可使用 `get_events` 轮询事件、`say` 让伙伴发言。遵循服务端返回的接管状态和参数约定。Lua 对同一伙伴串行提交；若返回 busy，等待进行中的程序完成。

```json
{
  "name": "lua",
  "arguments": {
    "companion": "xixi",
    "code": "return xixi_interaction.interaction.status()"
  }
}
```

## Lua 能力

| 调用 | 用途 |
| --- | --- |
| `xixi_interaction.interaction.status()` | 查看互动及身体占用、近期摸头、床边睡眠或撑伞状态 |
| `xixi_interaction.interaction.request("sit")` | 伙伴发起邀请，等玩家接受。返回布尔值 |
| `xixi_interaction.interaction.perform("hug")` | 执行玩家明确提出的互动。返回可读执行结果 |
| `xixi_interaction.interaction.stop()` | 结束该伙伴参与的互动 |
| `xixi_interaction.control.chat("抱抱我")` | 将玩家的明确请求交给本地文本识别器。未识别返回 `XIXI_SKIP` |
| `xixi_interaction.expression.sticker(3, 4)` | 显示爱心 4 秒，编号 0 收起。需要本地 DS 外观和贴纸图集 |
| `xixi_interaction.expression.blush(4)`、`react("affectionate", 4)` | 脸红或场景表情。需要本地 DS 外观及匹配资源 |
| `xixi_interaction.expression.dismiss()`、`status()` | 收起表情、读取表情状态 |
| `xixi_interaction.actions.list()` | 枚举当前 DS 模型菜单的原生动作和值 |
| `xixi_interaction.actions.play("点头", 0)`、`stop()` | 播放实际查询到的动作 ID、停止播放。0 使用原时长 |

当前版本也注册了 `ysm.xixi_actions` 与 `ysm.xixi_expression` 别名。用 `numen.api.help("xixi_interaction")` 查看当前运行版本的参数说明。动作 ID 如下。

| ID | 动作 |
| --- | --- |
| `sit`, `lean` | 坐下、依靠 |
| `maid_lap`, `player_lap` | 伙伴枕玩家、玩家枕伙伴 |
| `brush`, `pat`, `ask_pat` | 梳头、摸头、请求摸头 |
| `hug`, `back_hug`, `hand` | 正抱、背抱、牵手 |
| `umbrella`, `carry`, `lift`, `sleep` | 共伞、背负、实验抱起、手动睡眠预览 |

## 游戏事件

| 事件 | 数据 |
| --- | --- |
| `xixi_player_interaction` | 开始时有 `action`、`phase="started"`、`initiator="player"` 或 `"companion"`；重复梳头发送 `action="brush"`、`phase="stroke"` |
| `xixi_head_pat` | `action` 为当时正在执行的互动 ID |

处理事件时可按当前场景回复。事件已经代表动作在游戏中发生，重复调用 `perform` 会重新请求互动。身体被占用时先查 `interaction.status()`，避免同一伙伴同时执行独立移动任务。寻路卡住时，用可站立且带明确 Y 高度的短路线重试一次。二次失败时报告卡点，等待新的指令或环境变化。

## Coopanion 可选适配

包内两个 `.mjs` 是对现有 `cortico-world-minecraft` 扩展的适配片段。它们需要扩展已能调用 Numen MCP、注册 `minecraft_do`，并通过 `package.json` 的 `main` 指定入口。匹配的扩展可通过以下命令预览和安装。

```powershell
python .\install.py --instance 'D:\Minecraft\instances\YourInstance' --coopanion-extension 'D:\Coopanion\data\extensions\packages\cortico-world-minecraft' --dry-run
python .\install.py --instance 'D:\Minecraft\instances\YourInstance' --coopanion-extension 'D:\Coopanion\data\extensions\packages\cortico-world-minecraft'
```

适配会检查入口结构，找不到对应位置时会在写入前报错。通用集成可直接采用上面的 MCP 工具和 Lua 接口。适配片段增加的 `minecraft_expression` 与 `minecraft_model_action` 依赖本地 DS 外观，默认目标名为 `xixi`。基础互动可直接在游戏里使用。
