package com.xiaoxi.codepad.ui

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/** 自定义快捷键（Vibe Coding 条）。 */
data class CustomShortcut(val label: String, val usage: Int, val modifiers: Int)

/**
 * 平板界面配置。平板设置弹层和 Mac Helper 设置窗口都写这份结构，
 * 通过 0x60 / 0x61 / 0x62 三个帧同步（见 docs/HANDOFF.md 第 20 节）。
 *
 * revision 单调递增，两端都用它决定谁更新：收到的 revision 大于等于本地时才覆盖本地，
 * 本地修改时 revision + 1 并推给对端。
 */
data class PadConfig(
    val revision: Long = 0L,
    /** 布局维度：3 种排布，与配色独立组合。 */
    val layout: PadLayout = PadLayout.CLASSIC,
    /** 配色维度：含 [SkinTheme.AUTO]（跟随 Mac 系统外观）。 */
    val theme: SkinTheme = SkinTheme.CLASSIC,
    /**
     * 对端是否**显式**声明了 layout / theme。
     *
     * 旧版对端只发 `skin` 一个字段，无法表达新的两个维度；此时缺失不代表
     * 「对方要设成默认值」，而是「对方不关心」。由 [PadConfigStore.applyRemote]
     * 据此保留本地值，避免新旧混用时把用户配好的配色静默改回经典黑。
     *
     * 不参与 [sameContent]：它描述来源而非内容，算进去会让同一份数据反复回推。
     */
    val explicitAppearance: Boolean = true,
    val apps: List<String> = emptyList(),
    val shortcuts: List<CustomShortcut> = emptyList(),
    val mouseSensitivity: Float = 1f,
    val scrollSensitivity: Float = 1f,
) {
    /** 比较时忽略 revision：只有内容不同才值得重建界面或回推对端。 */
    fun sameContent(other: PadConfig): Boolean =
        layout == other.layout &&
            theme == other.theme &&
            apps == other.apps &&
            shortcuts == other.shortcuts &&
            mouseSensitivity == other.mouseSensitivity &&
            scrollSensitivity == other.scrollSensitivity

    fun toJson(): JSONObject = JSONObject().apply {
        put("revision", revision)
        put("layout", layout.id)
        put("theme", theme.id)
        // 旧版 Mac / 平板只认 skin 字段，写成布局 id 以保持兼容：
        // 旧版收到后会套用同名旧皮肤，视觉上等价于「布局 + 该布局默认配色」。
        put("skin", layout.id)
        put("apps", JSONArray(apps))
        put("shortcuts", JSONArray().apply {
            shortcuts.forEach {
                put(JSONObject()
                    .put("label", it.label)
                    .put("usage", it.usage)
                    .put("modifiers", it.modifiers))
            }
        })
        put("mouseSensitivity", mouseSensitivity.toDouble())
        put("scrollSensitivity", scrollSensitivity.toDouble())
    }

    companion object {
        const val MAX_APPS = 9
        const val MAX_SHORTCUTS = 12

        fun fromJson(json: JSONObject): PadConfig {
            val apps = json.optJSONArray("apps")?.let { array ->
                List(array.length()) { array.optString(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(MAX_APPS)
            } ?: emptyList()
            val shortcuts = json.optJSONArray("shortcuts")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val label = item.optString("label").trim()
                    val usage = item.optInt("usage", 0)
                    if (label.isEmpty() || usage <= 0) null
                    else CustomShortcut(label.take(12), usage, item.optInt("modifiers", 0))
                }.take(MAX_SHORTCUTS)
            } ?: emptyList()
            return PadConfig(
                revision = json.optLong("revision", 0L),
                // layout/theme 缺失时回退到旧的 skin 字段，仅作为展示用的兜底值；
                // 是否真的覆盖本地，由 explicitAppearance 在 applyRemote 里决定。
                layout = PadLayout.fromId(json.optString("layout").ifBlank { json.optString("skin") }),
                theme = SkinTheme.fromId(json.optString("theme").ifBlank { json.optString("skin") }),
                explicitAppearance = json.has("layout") || json.has("theme"),
                apps = apps,
                shortcuts = shortcuts,
                mouseSensitivity = json.optDouble("mouseSensitivity", 1.0).toFloat()
                    .coerceIn(MOUSE_MIN, MOUSE_MAX),
                scrollSensitivity = json.optDouble("scrollSensitivity", 1.0).toFloat()
                    .coerceIn(SCROLL_MIN, SCROLL_MAX),
            )
        }

        const val MOUSE_MIN = 0.5f
        const val MOUSE_MAX = 2f
        const val SCROLL_MIN = 0.5f
        const val SCROLL_MAX = 4f
    }
}

/**
 * 配置的唯一读写入口。只在主线程访问：网络线程收到的配置由 MainActivity 转到主线程再调用。
 */
class PadConfigStore private constructor(context: Context) {

    enum class Origin { LOCAL, REMOTE }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArrayList<(PadConfig, Origin) -> Unit>()

    fun addListener(listener: (PadConfig, Origin) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (PadConfig, Origin) -> Unit) {
        listeners -= listener
    }

    fun current(): PadConfig {
        // 老版本只存 skin 一个键：pref 里没有 layout/theme 时用它派生，
        // 否则升级后用户已选的深空专业 / 双手操控会被静默重置成「经典」。
        val legacy = prefs.getString(PREF_SKIN, null)
        return PadConfig(
            revision = prefs.getLong(PREF_REVISION, 0L),
            layout = PadLayout.fromId(prefs.getString(PREF_LAYOUT, null) ?: legacy),
            theme = SkinTheme.fromId(prefs.getString(PREF_THEME, null) ?: legacy),
            apps = readApps(),
            shortcuts = readShortcuts(),
            mouseSensitivity = prefs.getFloat(PREF_MOUSE_SENSITIVITY, 1f)
                .coerceIn(PadConfig.MOUSE_MIN, PadConfig.MOUSE_MAX),
            scrollSensitivity = prefs.getFloat(PREF_SCROLL_SENSITIVITY, 1f)
                .coerceIn(PadConfig.SCROLL_MIN, PadConfig.SCROLL_MAX),
        )
    }

    /** 平板本地修改：revision + 1 后落盘，并通知监听者推送给 Mac。 */
    fun update(mutate: (PadConfig) -> PadConfig): PadConfig {
        val previous = current()
        val next = mutate(previous)
        if (next.sameContent(previous)) return previous
        val stored = next.copy(revision = previous.revision + 1)
        persist(stored)
        notifyListeners(stored, Origin.LOCAL)
        return stored
    }

    /**
     * 应用 Mac 推来的配置。revision 小于本地时忽略（本地更新），内容相同也不重建界面。
     * @return 真正应用了的配置，未应用时返回 null。
     */
    fun applyRemote(payload: String): PadConfig? {
        val parsed = runCatching { PadConfig.fromJson(JSONObject(payload)) }
            .onFailure { Log.w(TAG, "Invalid pad config payload", it) }
            .getOrNull() ?: return null
        val previous = current()
        if (parsed.revision < previous.revision) return null
        // 旧版对端只发 skin，没有 layout/theme 字段：缺失是「不关心」而非「设为默认」，
        // 保留本地布局与配色，否则新版配好的深夜蓝会被旧版静默改回经典黑。
        val incoming = if (parsed.explicitAppearance) parsed else parsed.copy(
            layout = previous.layout,
            theme = previous.theme,
        )
        if (incoming.sameContent(previous)) {
            // 内容一致时只对齐 revision，避免下次握手来回推同一份配置。
            if (incoming.revision > previous.revision) {
                prefs.edit().putLong(PREF_REVISION, incoming.revision).apply()
            }
            return null
        }
        persist(incoming)
        notifyListeners(incoming, Origin.REMOTE)
        return incoming
    }

    fun encodeCurrent(): String = current().toJson().toString()

    private fun persist(config: PadConfig) {
        val shortcuts = JSONArray()
        config.shortcuts.forEach {
            shortcuts.put(JSONObject()
                .put("label", it.label)
                .put("usage", it.usage)
                .put("modifiers", it.modifiers))
        }
        prefs.edit()
            .putLong(PREF_REVISION, config.revision)
            .putString(PREF_LAYOUT, config.layout.id)
            .putString(PREF_THEME, config.theme.id)
            // skin 作为旧版读取用的影子字段一并落下，方便降级到旧版 App 时保留布局。
            .putString(PREF_SKIN, config.layout.id)
            .putString(PREF_APPS, JSONArray(config.apps).toString())
            .putString(PREF_SHORTCUTS, shortcuts.toString())
            .putFloat(PREF_MOUSE_SENSITIVITY, config.mouseSensitivity)
            .putFloat(PREF_SCROLL_SENSITIVITY, config.scrollSensitivity)
            .apply()
    }

    private fun notifyListeners(config: PadConfig, origin: Origin) {
        listeners.forEach { listener ->
            runCatching { listener(config, origin) }
                .onFailure { Log.w(TAG, "Pad config listener failed", it) }
        }
    }

    private fun readApps(): List<String> {
        val saved = prefs.getString(PREF_APPS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(saved)
            List(array.length()) { array.getString(it) }.take(PadConfig.MAX_APPS)
        }.getOrDefault(emptyList())
    }

    private fun readShortcuts(): List<CustomShortcut> {
        val saved = prefs.getString(PREF_SHORTCUTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                CustomShortcut(
                    item.getString("label"),
                    item.getInt("usage"),
                    item.getInt("modifiers"),
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val TAG = "CodePadConfig"
        const val PREFS_NAME = "codepad_ui"
        private const val PREF_REVISION = "config_revision"
        private const val PREF_SKIN = "skin"
        private const val PREF_LAYOUT = "layout"
        private const val PREF_THEME = "theme"
        private const val PREF_APPS = "selected_apps"
        private const val PREF_SHORTCUTS = "custom_shortcuts"
        const val PREF_MOUSE_SENSITIVITY = "mouse_sensitivity"
        const val PREF_SCROLL_SENSITIVITY = "trackpad_sensitivity"

        @Volatile private var instance: PadConfigStore? = null

        fun get(context: Context): PadConfigStore =
            instance ?: synchronized(this) {
                instance ?: PadConfigStore(context).also { instance = it }
            }
    }
}
