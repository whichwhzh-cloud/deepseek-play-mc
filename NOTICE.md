# 许可及第三方资源

项目自己的 Java、Python、JavaScript 源码、文档以及原创伞道具资源使用 [MIT 许可](LICENSE)。Minecraft、NeoForge、Numen、YSM 和 Coopanion 分别由其上游提供，并适用各自的条款。分享包中的核心 JAR 只包含本项目内容。

DS 鲸鱼娘、酒狐测试模型、大肥鱼表情贴纸与脸红几何，以及由这些第三方资产烘焙出来的动作数据由使用者按各自授权在本地提供。公开 ZIP 提供普通皮肤双人方案、适配代码及动作生成器。拥有完整模型适配资源时，可将获准使用的 `asset-patch/` 放入项目根目录。生成器读取两个测试模型目录下的 `ysm.json` 和 `animations/extra.animation.json`。构建本地完整版本后，用安装器将受支持的 `xixi_` 动画键合并到本机 YSM 模型。

DS 原生动作从用户本机装备的对应模型读取。脸红和贴纸还需具有匹配 UV 的图集与几何。可以通过安装器的 `--sticker-source` 和 `--face-source` 指定本地 PNG 图集和模型几何 JSON。不同模型的骨名、比例和 UV 不同，自动套用可能错位。将任何模型资产加入 Git 仓库前，按其许可确认再分发权。
