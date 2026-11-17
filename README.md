# 大肥鱼 · Minecraft 伙伴互动

为 Numen 伙伴增加右键互动、双人分支动作、主动邀请与事件反馈的 NeoForge 模组。公开核心版支持普通 Minecraft 皮肤，包含可安装 JAR、源码、构建脚本和接口说明。目标环境为本地单人世界。

## 前置

| 项目 | 版本 | 用途 |
| --- | --- | --- |
| Minecraft Java Edition | 26.1.2 | 游戏客户端 |
| Java | 25 | 启动游戏。重建源码需 JDK 25 |
| NeoForge | [26.1.2.126](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.2.126/) | 模组加载器 |
| 言出法随 Numen | [0.1.4.1，NeoForge 26.1.2](https://github.com/Dwinovo/minecraft-numen#安装) | 伙伴、Lua SDK、内置 MCP |
| Yes Steve Model（YSM） | [2.6.5 NeoForge 的 26.1 热修版](https://modrinth.com/mod/yes-steve-model/version/2.6.5-neoforge%2Bmc26.1-hotfix) | 当前渲染集成所需，即使用普通皮肤也要安装 |
| Python | 3.10+ | 可选，用于安装备份、回滚和重新构建 |
| Coopanion | 已能与 Numen 通信的 Minecraft 扩展 | 可选，对话、表情工具和事件接入 |

Numen 自带 MCP 和 Lua SDK。Numen 自带的 AI 与外接 MCP 大脑可按需选择。前者在 Numen 面板设置自己的模型服务，后者使用 [接口接入指南](docs/INTERFACES.md)。

## 安装和试玩

1. 关闭游戏，将 `xixi-interaction-0.6.2-core.jar` 复制到目标实例的 `mods/`。更新时先移走同一模组的旧 JAR。需要备份和回滚时，可以用下面的安装脚本替代手动复制。
2. 进入单人世界，按 `N` 打开 Numen 面板，召唤属于自己的伙伴。建议命名为 `xixi`，以启用按此名称查找伙伴的可选 Coopanion 工具和空闲背抱。
3. 在 YSM 中将玩家和伙伴都切回普通皮肤。找一块安全空地，在 3 格内空手右键伙伴。选「一起坐下」后可再次右键选依靠、膝枕或梳头分支。
4. 按 Shift 停止配对动作。Esc 关闭菜单。主动邀请出现在聊天栏和伙伴的右键菜单，由玩家接受。

```powershell
python .\install.py --instance 'D:\Minecraft\instances\YourInstance' --dry-run
python .\install.py --instance 'D:\Minecraft\instances\YourInstance'
python .\install.py --rollback '.\backups\安装时输出的目录名'
```

## 互动和配置

| 功能 | 触发方式 |
| --- | --- |
| 坐下、依靠、两种膝枕、拥抱、背抱、牵手、摸头 | 右键菜单。汐枕膝时可右键摸头，重复点击可触发事件 |
| 背负 | 同行菜单，玩家作为载人者。Shift 下背。实验性的「抱起」默认关闭 |
| 梳头 | 一起坐下后手持原版刷子进入，右键头部重复梳理 |
| 手动共伞 | 工作台用三格羊毛横排，下方中列放两根木棍；任一方副手持伞后点菜单 |
| 雨天自动撑伞 | 伙伴在露天淋雨、附近安全且没有配对动作时自动撑伞；需要一只空手或已持伞 |
| 床边休息 | 玩家上床，伙伴在三格内空闲且床侧有安全位置时自动躺下；起床后结束 |
| 空闲背抱 | 名为 `xixi` 的伙伴靠近玩家，双方静止、没有主要工作和附近敌人时触发 |

`config/xixi-interaction.json` 首次进世界时生成。可用 `enableBackCarry`、`enableCarry`、`enableRainUmbrella`、`enableSleepIdle`、`enableIdleBackHug` 控制相应行为。`maxSeconds` 是双人互动上限，默认 600 秒。互动会暂时占用伙伴的移动身体；取消互动后可以继续下达身体任务。

0.6.2 增加创造飞行。将伙伴本人切到创造模式后，可以通过 Numen 对话让她飞起来、飞到指定坐标或降落。她会沿空中路线逐步移动，抵达后悬停，遇到障碍会停下。如何调用见 [接口接入指南](docs/INTERFACES.md)。

0.6.1 调整了 Numen 的路径代价与水中移动方向。高低差优先规划直向台阶，水中朝当前路径点转向，并降低长时间潜水路线的优先级。观察步骤见 [验收指南](docs/VERIFY.md)。

## 构建和 Git 分享

在 Windows 上准备已完成一次启动的 NeoForge 实例、表中版本的前置 JAR、JDK 25 和 Python。传入安装了 Numen 和 YSM 的实例目录。如果启动器的公共 `libraries`、`runtime` 位于另一个根目录，通过 `-MinecraftRoot` 指定；需要时用 `-JavaHome` 指定 JDK。

```powershell
powershell -ExecutionPolicy Bypass -File .\BUILD.ps1 -Instance 'D:\Minecraft\.minecraft\versions\26.1.2-neoforge-26.1.2.126' -CoreOnly
```

解压到新目录后，从该目录建立本地仓库。

```sh
git init -b main
git add .
git commit -m "Initial Xixi Interaction release"
```

可以按自己的 Git 平台流程设置远程仓库并推送。首次发布时可把 ZIP 或核心 JAR 附在 Release；若从源码重建后需要分发新的 ZIP，重新生成校验清单。许可与模型资源边界见 [许可及第三方资源](NOTICE.md)。
