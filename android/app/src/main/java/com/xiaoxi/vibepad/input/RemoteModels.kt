package com.xiaoxi.vibepad.input

data class HelperHealth(
    val accessibilityTrusted: Boolean = false,
    val helperVersion: String = "",
    val protocolVersion: Int = 2,
    val lastInputAgeMs: Long? = null,
    val mouseButtons: Int = 0,
    val modifiers: Int = 0,
    /** Mac 当前前台 App 的 bundle id，用于高亮常用 App；未知时为 null。 */
    val frontmostApp: String? = null,
    /**
     * Mac 当前系统外观：`"dark"` / `"light"`，未知时为 null。
     * 只在配色选了「跟随 Mac」时用到，属于运行时状态，不参与配置同步。
     */
    val macAppearance: String? = null,
) {
    val inputUsable: Boolean
        get() = accessibilityTrusted && protocolVersion == 2
}

data class RemoteApp(
    val name: String,
    val bundleId: String,
    val iconPng: ByteArray? = null,
)

data class TouchBarFrame(
    val frameId: Long,
    val width: Int,
    val height: Int,
    val codec: Int,
    val bytes: ByteArray,
)

interface RemoteDataListener {
    fun onHelperHealth(health: HelperHealth) = Unit
    fun onAppCatalogStarted() = Unit
    fun onRemoteApp(app: RemoteApp) = Unit
    fun onAppCatalogFinished() = Unit
    fun onTouchBarFrame(frame: TouchBarFrame) = Unit
    /** Mac 推来的界面配置 JSON（0x61），内容见 PadConfig。 */
    fun onPadConfig(payload: String) = Unit
    fun onPairingCode(code: String) = Unit
    fun onPairingMessage(message: String, success: Boolean = false) = Unit
}
