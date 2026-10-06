# VibePad 皮肤与设计源码

VibePad 安卓平板端的布局与配色参考源。布局（`classic` / `graphite` / `titanium`）
与配色（`classic` / `graphite` / `titanium` / `midnight` / `forest` / `violet` / `auto`）
是两个独立维度，任意组合。所有组合共用同一套连接协议、手势与快捷键逻辑，
只改变**视觉变量与布局**，不各自发明交互。

正式 App 在设置里提供布局单选组 + 配色下拉框，不是三个独立 App。

| 布局组合 | 目录 | 来源 | 特点 |
|---|---|---|---|
| 01 经典黑 | `skins/01-classic-dark/` | 2026-07-23 Stitch 原型，现行 App 已实现版本 | 纯黑背景、左控右触（原型中的五组额度栏已从 App 移除，顶栏改为 Touch Bar 画面） |
| 02 深空专业 Graphite Pro | `skins/02-graphite-pro/` | 2026-09-10 Orbit 交付包 | 深空灰分层、左触控右快捷键、底部 App Dock |
| 05 双手操控 Titanium Duo | `skins/05-titanium-duo/` | 2026-09-10 Orbit 交付包 | 暖钛浅色、中央触控、左右拇指分工 |

## 布局与配色目录结构

- `index.html`：可交互高保真原型，浏览器直接打开（02/05 已内嵌全部样式脚本，
  断网可用；01 依赖 CDN 的 Tailwind 与 Google Fonts，需联网）
- `*.png`：主界面 / Chrome 上下文 / 设置弹层截图，1280 × 800 参考画布

## 设计源码

`orbit-source/` 是 02/05 的可重建源码与设计文档（tokens.json、measurements.json、
HANDOFF.md、QA.md）。改 02/05 时优先编辑 `orbit-source/src/selected-fragment.html`，
再跑 `python3 orbit-source/tools/build.py` 重新生成。01 是 Stitch 历史产物，
只有最终 HTML，无可重建源码。

## 优化时必须遵守的边界

1. **编号固定**：01 / 02 / 05 是正式编号，不要重命名，不要把两套布局的元素混成第三套。
2. **协议不变**：配色与布局只改视觉与排布，不改网络协议、配对流程、手势语义、键位功能。
3. **演示数据非真值**：原型里的设备名、延迟、电量、转写文字都是演示值；
   接入规则见 `orbit-source/design/HANDOFF.md` 第 6 节（真实数据接入）。
4. **主题与布局分离**：02 与 05 的差异是信息架构 + 色板，不能只换色表互相推导。
5. **触控目标 ≥ 48dp**，横屏平板优先；验收必须在真机上完成，浏览器检查不替代真机。
6. 开发实施总纲读 `orbit-source/design/HANDOFF.md`；验收清单读 `orbit-source/design/QA.md`。

## 现行 App 实现对应

当前 Android App（`android/`，Kotlin 原生 View）已经实现三套默认组合，可在设置里切换
布局和配色（7 种可选），也可以在 Mac 菜单栏「VibePad 设置…」里切换：

| 设计 | App 内名称 | 实现 |
| --- | --- | --- |
| 01 经典黑 | 经典 | `PadLayout.CLASSIC` + `SkinTheme.CLASSIC`，沿用 0.4.1 已上线版本 |
| 02 深空专业 | 深空专业 | `PadLayout.GRAPHITE` + `SkinTheme.GRAPHITE` |
| 05 双手操控 | 双手操控 | `PadLayout.TITANIUM` + `SkinTheme.TITANIUM` |

布局与配色在实现上已拆成两个独立维度（`PadLayout` 决定排布，`SkinTheme` 决定色板），
三套设计只是它们的默认组合；额外配色 `midnight` 深夜蓝 / `forest` 墨绿 / `violet` 暗紫 /
`auto` 跟随 Mac 外观可与任意布局搭配。色板在
`android/app/src/main/java/com/xiaoxi/vibepad/ui/Skin.kt`，布局在同目录的
`VibePadView.kt`。原型里本项目做不到的元素（固定 Touch Bar 标签、亮度/音量滑杆、
Coding/日常模式、文字输入弹层、演示转写）按交接约定不实现。
