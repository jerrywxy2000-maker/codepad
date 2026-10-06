package com.xiaoxi.vibepad.ui

/**
 * 布局与配色是两个独立维度，可以自由组合：
 *
 * - [PadLayout] 决定控件排布（3 种，对应 designs/skins/ 下的 01 / 02 / 05）。
 * - [SkinTheme] 只决定色板，新增一种配色只需在这里加一个枚举值和一份
 *   [SkinPalette]，不必改动任何布局代码、协议或手势语义。
 *
 * 皮肤只改变视觉变量与布局，不改变网络协议、配对流程、手势语义和键位功能
 * （designs/README.md「优化时必须遵守的边界」）。原型里在本项目上无法真正操作的元素
 * （固定 Touch Bar 标签、亮度/音量滑杆、演示转写草稿、文字输入弹层）一律不实现：
 * 顶部一律走 Mac 真实 Touch Bar 画面回传。
 */
enum class PadLayout(val id: String, val displayName: String, val summary: String) {
    CLASSIC("classic", "经典", "纯黑背景 · 左控制右触控"),
    GRAPHITE("graphite", "深空专业", "左触控右快捷键 · 底部 App Dock"),
    TITANIUM("titanium", "双手操控", "中央触控 · 左右拇指分工");

    companion object {
        fun fromId(id: String?): PadLayout =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * 配色维度。[AUTO] 不是固定色板，而是按 Mac 当前系统外观（深色/浅色）解析成
 * [SkinPalette.CLASSIC] 或 [SkinPalette.TITANIUM]——解析所需的状态由 Helper 心跳帧
 * `0x21 PONG` 的 `appearance` 字段下发，属于运行时状态，不写进配置。
 */
enum class SkinTheme(val id: String, val displayName: String, val summary: String) {
    CLASSIC("classic", "经典黑", "纯黑底 · 冷蓝强调"),
    GRAPHITE("graphite", "深空灰", "深空灰 · 低饱和蓝灰"),
    TITANIUM("titanium", "暖钛浅", "暖钛浅色 · 深棕强调"),
    MIDNIGHT("midnight", "深夜蓝", "深蓝底 · 亮蓝强调"),
    FOREST("forest", "墨绿", "深绿底 · 薄荷绿强调"),
    VIOLET("violet", "暗紫", "深紫底 · 淡紫强调"),
    AUTO("auto", "跟随 Mac", "按 Mac 系统外观自动切换明暗");

    /** 固定配色的色板；[AUTO] 返回 null，需由 [SkinTheme.resolve] 结合外观解析。 */
    val fixedPalette: SkinPalette?
        get() = when (this) {
            CLASSIC -> SkinPalette.CLASSIC
            GRAPHITE -> SkinPalette.GRAPHITE
            TITANIUM -> SkinPalette.TITANIUM
            MIDNIGHT -> SkinPalette.MIDNIGHT
            FOREST -> SkinPalette.FOREST
            VIOLET -> SkinPalette.VIOLET
            AUTO -> null
        }

    /**
     * 解析出实际使用的色板。
     *
     * @param macAppearance Mac 当前系统外观：`"dark"` / `"light"`，未知时按深色处理。
     */
    fun resolve(macAppearance: String?): SkinPalette =
        fixedPalette
            ?: if (macAppearance == "light") SkinPalette.TITANIUM else SkinPalette.CLASSIC

    companion object {
        fun fromId(id: String?): SkinTheme = entries.firstOrNull { it.id == id } ?: CLASSIC

        /**
         * 兼容旧版本只传一个 `skin` 字段的场景：旧皮肤 id 本身就是「布局 + 配色」的
         * 绑定值，因此直接映射为同名配色；识别不了的退回 [CLASSIC]。
         */
        fun fromLegacySkinId(id: String?): SkinTheme = fromId(id)
    }
}

/**
 * 配色与形状变量。取值来自 designs/orbit-source/design/tokens.json；
 * 经典黑沿用 0.4.1 已上线的实现值，改皮肤时不要改动它。
 */
data class SkinPalette(
    /** 整屏底色。 */
    val background: Int,
    /** 面板（常用 App / 快捷键 / Dock）底色。 */
    val panel: Int,
    /** 键帽与次级按钮底色。 */
    val key: Int,
    /** 键帽按下态底色。 */
    val keyPressed: Int,
    /** 面板与键帽描边。 */
    val outline: Int,
    /** 主文字。 */
    val text: Int,
    /** 次级文字。 */
    val muted: Int,
    /** 线性图标。 */
    val icon: Int,
    /** 强调文字（面板动作、App 首字母）。 */
    val accent: Int,
    /** 当前 App / 按下高亮的浅底。 */
    val accentSoft: Int,
    /** 主操作按钮底色（按住说话）。 */
    val accentStrong: Int,
    /** 主操作按钮按下态底色。 */
    val accentPressed: Int,
    /** 主操作按钮上的文字与图标。 */
    val onAccent: Int,
    /** 触控区底色。 */
    val pad: Int,
    /** 触控区未连接时的描边。 */
    val padOutline: Int,
    /** 触控区已连接时的描边。 */
    val padOnline: Int,
    /** 触控区中央的提示文字。 */
    val padLabel: Int,
    /** Touch Bar 条底色。 */
    val touchBar: Int,
    /** 危险键位（停止 ⌃C）。 */
    val warning: Int,
    /** 录音态。 */
    val recording: Int,
    /** 面板圆角（dp）。 */
    val panelRadius: Float,
    /** 触控区圆角（dp）。 */
    val padRadius: Float,
    /** 键帽圆角（dp）。 */
    val keyRadius: Float,
    /** 浅色皮肤为 true，用于决定阴影与状态点亮度。 */
    val light: Boolean,
) {
    companion object {
        /** 01 经典黑：0.4.1 已上线配色，逐值对应旧的 VibePadView 常量。 */
        val CLASSIC = SkinPalette(
            background = 0xFF000000.toInt(),
            panel = 0xFF2A2A2A.toInt(),
            key = 0xFF2A2A2A.toInt(),
            keyPressed = 0xFF3A3A3D.toInt(),
            outline = 0xFF414754.toInt(),
            text = 0xFFE2E2E2.toInt(),
            muted = 0xFFC0C6D6.toInt(),
            icon = 0xFFC0C6D6.toInt(),
            accent = 0xFFAAC7FF.toInt(),
            accentSoft = 0xFF25334C.toInt(),
            // 麦克风主按钮改用 02 深空专业的蓝（老板指定）
            accentStrong = 0xFFACC3ED.toInt(),
            accentPressed = 0xFF8FAEE2.toInt(),
            onAccent = 0xFF1C2941.toInt(),
            pad = 0xFF000000.toInt(),
            padOutline = 0xFF3D4551.toInt(),
            padOnline = 0xFF53B582.toInt(),
            padLabel = 0xFF697381.toInt(),
            touchBar = 0xFF000000.toInt(),
            warning = 0xFFFFB4AB.toInt(),
            recording = 0xFFFF3B30.toInt(),
            panelRadius = 14f,
            padRadius = 22f,
            keyRadius = 10f,
            light = false,
        )

        /** 02 深空专业 Graphite Pro。 */
        val GRAPHITE = SkinPalette(
            background = 0xFF151619.toInt(),
            panel = 0xFF202125.toInt(),
            key = 0xFF2D2F35.toInt(),
            keyPressed = 0xFF3C3F48.toInt(),
            outline = 0xFF32343B.toInt(),
            text = 0xFFEEEEEF.toInt(),
            muted = 0xFF8B8E99.toInt(),
            icon = 0xFFA3A8B5.toInt(),
            accent = 0xFFACC3ED.toInt(),
            accentSoft = 0xFF303C53.toInt(),
            accentStrong = 0xFFACC3ED.toInt(),
            accentPressed = 0xFF8FAEE2.toInt(),
            onAccent = 0xFF1C2941.toInt(),
            pad = 0xFF1B1D21.toInt(),
            padOutline = 0xFF32343B.toInt(),
            padOnline = 0xFF3C9168.toInt(),
            padLabel = 0xFF8B8E99.toInt(),
            touchBar = 0xFF0E0F11.toInt(),
            warning = 0xFFC65C59.toInt(),
            recording = 0xFFB95D68.toInt(),
            panelRadius = 19f,
            padRadius = 19f,
            keyRadius = 11f,
            light = false,
        )

        /** 05 双手操控 Titanium Duo。 */
        val TITANIUM = SkinPalette(
            background = 0xFFEAE7E1.toInt(),
            panel = 0xFFF7F5F1.toInt(),
            key = 0xFFEDE9E2.toInt(),
            keyPressed = 0xFFE1DBD1.toInt(),
            outline = 0xFFDEDAD2.toInt(),
            text = 0xFF49453F.toInt(),
            muted = 0xFF928B81.toInt(),
            icon = 0xFF958B7D.toInt(),
            accent = 0xFF776753.toInt(),
            accentSoft = 0xFFE9E1D5.toInt(),
            accentStrong = 0xFF776753.toInt(),
            accentPressed = 0xFF6A5B49.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            pad = 0xFFF1EEE8.toInt(),
            padOutline = 0xFFD5CFC4.toInt(),
            padOnline = 0xFF3C9168.toInt(),
            padLabel = 0xFF958B7D.toInt(),
            touchBar = 0xFFF7F5F1.toInt(),
            warning = 0xFFB4524E.toInt(),
            recording = 0xFFB95D68.toInt(),
            panelRadius = 21f,
            padRadius = 21f,
            keyRadius = 11f,
            light = true,
        )

        /**
         * 深夜蓝 Midnight：深靛底 + 亮蓝强调，长时间夜间使用比纯黑更少刺眼。
         * 圆角与布局无关，沿用经典黑的 14/22/10。
         */
        val MIDNIGHT = SkinPalette(
            background = 0xFF0A1020.toInt(),
            panel = 0xFF141C30.toInt(),
            key = 0xFF1B2440.toInt(),
            keyPressed = 0xFF273156.toInt(),
            outline = 0xFF2E3A5C.toInt(),
            text = 0xFFE4E9F5.toInt(),
            muted = 0xFF9AA6C4.toInt(),
            icon = 0xFFA8B4D2.toInt(),
            accent = 0xFF9CC0FF.toInt(),
            accentSoft = 0xFF1D2B4A.toInt(),
            accentStrong = 0xFF8FB6F5.toInt(),
            accentPressed = 0xFF7BA2E4.toInt(),
            onAccent = 0xFF0C1730.toInt(),
            pad = 0xFF0A1020.toInt(),
            padOutline = 0xFF2A3555.toInt(),
            padOnline = 0xFF53B582.toInt(),
            padLabel = 0xFF6E7A99.toInt(),
            touchBar = 0xFF070C18.toInt(),
            warning = 0xFFFFB4AB.toInt(),
            recording = 0xFFFF4D6B.toInt(),
            panelRadius = 14f,
            padRadius = 22f,
            keyRadius = 10f,
            light = false,
        )

        /**
         * 墨绿 Forest：深松绿底 + 薄荷绿强调，冷色系里最放松的一套。
         */
        val FOREST = SkinPalette(
            background = 0xFF0A1512.toInt(),
            panel = 0xFF12211C.toInt(),
            key = 0xFF172A23.toInt(),
            keyPressed = 0xFF21392F.toInt(),
            outline = 0xFF274038.toInt(),
            text = 0xFFE2EDE8.toInt(),
            muted = 0xFF95AFA5.toInt(),
            icon = 0xFFA3BDB2.toInt(),
            accent = 0xFF86E0BC.toInt(),
            accentSoft = 0xFF173328.toInt(),
            accentStrong = 0xFF74D6B1.toInt(),
            accentPressed = 0xFF62C39F.toInt(),
            onAccent = 0xFF07211A.toInt(),
            pad = 0xFF0A1512.toInt(),
            padOutline = 0xFF284039.toInt(),
            padOnline = 0xFF53B582.toInt(),
            padLabel = 0xFF648073.toInt(),
            touchBar = 0xFF070F0D.toInt(),
            warning = 0xFFFFB4AB.toInt(),
            recording = 0xFFFF4D6B.toInt(),
            panelRadius = 14f,
            padRadius = 22f,
            keyRadius = 10f,
            light = false,
        )

        /**
         * 暗紫 Violet：深紫底 + 淡紫强调。
         */
        val VIOLET = SkinPalette(
            background = 0xFF120D1E.toInt(),
            panel = 0xFF1C152C.toInt(),
            key = 0xFF241B38.toInt(),
            keyPressed = 0xFF31254A.toInt(),
            outline = 0xFF392D52.toInt(),
            text = 0xFFEAE4F5.toInt(),
            muted = 0xFFA99CC4.toInt(),
            icon = 0xFFB4A7CE.toInt(),
            accent = 0xFFC6B0FF.toInt(),
            accentSoft = 0xFF2A2148.toInt(),
            accentStrong = 0xFFB9A2F7.toInt(),
            accentPressed = 0xFFA78EEA.toInt(),
            onAccent = 0xFF170F2E.toInt(),
            pad = 0xFF120D1E.toInt(),
            padOutline = 0xFF382D51.toInt(),
            padOnline = 0xFF53B582.toInt(),
            padLabel = 0xFF786D95.toInt(),
            touchBar = 0xFF0D0917.toInt(),
            warning = 0xFFFFB4AB.toInt(),
            recording = 0xFFFF4D6B.toInt(),
            panelRadius = 14f,
            padRadius = 22f,
            keyRadius = 10f,
            light = false,
        )
    }
}
