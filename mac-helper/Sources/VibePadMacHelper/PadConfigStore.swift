import Foundation

/// 平板界面配置。Android 端 `PadConfig` 与本结构逐字段对应，JSON 键名必须保持一致。
struct PadShortcut: Equatable {
    var label: String
    var usage: Int
    var modifiers: Int

    var json: [String: Any] { ["label": label, "usage": usage, "modifiers": modifiers] }

    init(label: String, usage: Int, modifiers: Int) {
        self.label = label
        self.usage = usage
        self.modifiers = modifiers
    }

    init?(json: Any) {
        guard let object = json as? [String: Any],
              let label = object["label"] as? String,
              let usage = object["usage"] as? Int,
              !label.isEmpty, usage > 0
        else { return nil }
        self.label = String(label.prefix(12))
        self.usage = usage
        self.modifiers = object["modifiers"] as? Int ?? 0
    }
}

struct PadConfig: Equatable {
    /// 布局维度：3 种排布，与配色独立组合。
    static let layouts = ["classic", "graphite", "titanium"]
    static let layoutNames = ["classic": "经典", "graphite": "深空专业", "titanium": "双手操控"]
    /// 配色维度：可自由扩充，`auto` 表示跟随 Mac 系统外观。
    static let themes = ["classic", "graphite", "titanium", "midnight", "forest", "violet", "auto"]
    static let themeNames = [
        "classic": "经典黑",
        "graphite": "深空灰",
        "titanium": "暖钛浅",
        "midnight": "深夜蓝",
        "forest": "墨绿",
        "violet": "暗紫",
        "auto": "跟随 Mac",
    ]
    static let maxApps = 9
    static let maxShortcuts = 12

    var revision: Int = 0
    var layout: String = "classic"
    var theme: String = "classic"
    /// 对端是否显式声明了 layout/theme。旧版对端只发 `skin`，缺失是「不关心」
    /// 而非「设为默认」，详见 `PadConfigStore.accept` 里的合并逻辑。
    /// 不参与 `sameContent`：它描述来源而非内容，算进去会让同一份数据反复回推。
    var explicitAppearance: Bool = true
    var apps: [String] = []
    var shortcuts: [PadShortcut] = []
    var mouseSensitivity: Double = 1
    var scrollSensitivity: Double = 1

    /// revision 之外的内容是否相同：相同就不必回推，也不必重建平板界面。
    func sameContent(as other: PadConfig) -> Bool {
        layout == other.layout &&
            theme == other.theme &&
            apps == other.apps &&
            shortcuts == other.shortcuts &&
            mouseSensitivity == other.mouseSensitivity &&
            scrollSensitivity == other.scrollSensitivity
    }

    var json: [String: Any] {
        [
            "revision": revision,
            "layout": layout,
            "theme": theme,
            // 旧版平板只认 skin 字段，写成布局 id 以保持兼容：
            // 旧版收到后会套用同名旧皮肤，等价于「布局 + 该布局默认配色」。
            "skin": layout,
            "apps": apps,
            "shortcuts": shortcuts.map(\.json),
            "mouseSensitivity": mouseSensitivity,
            "scrollSensitivity": scrollSensitivity,
        ]
    }

    var payload: Data? { try? JSONSerialization.data(withJSONObject: json) }

    init() {}

    init?(payload: Data) {
        guard let object = try? JSONSerialization.jsonObject(with: payload) as? [String: Any] else {
            return nil
        }
        revision = object["revision"] as? Int ?? 0
        // layout/theme 缺失时回退到旧的 skin 字段，仅作兜底展示值；
        // 是否真的覆盖本地，由 explicitAppearance 在 accept 里决定。
        let legacySkin = object["skin"] as? String
        let layout = object["layout"] as? String ?? legacySkin ?? "classic"
        self.layout = PadConfig.layouts.contains(layout) ? layout : "classic"
        let theme = object["theme"] as? String ?? legacySkin ?? "classic"
        self.theme = PadConfig.themes.contains(theme) ? theme : "classic"
        explicitAppearance = object["layout"] != nil || object["theme"] != nil
        apps = ((object["apps"] as? [Any]) ?? [])
            .compactMap { $0 as? String }
            .filter { !$0.isEmpty }
            .reduce(into: [String]()) { result, id in if !result.contains(id) { result.append(id) } }
        apps = Array(apps.prefix(PadConfig.maxApps))
        shortcuts = Array(((object["shortcuts"] as? [Any]) ?? [])
            .compactMap(PadShortcut.init(json:))
            .prefix(PadConfig.maxShortcuts))
        mouseSensitivity = min(max(object["mouseSensitivity"] as? Double ?? 1, 0.5), 2)
        scrollSensitivity = min(max(object["scrollSensitivity"] as? Double ?? 1, 0.5), 4)
    }
}

/// Mac 侧唯一一份平板配置。菜单栏设置窗口和已连接的平板会话都改这里，
/// revision 大的一方胜出；变更后由 `onChange` 广播给其它会话。
final class PadConfigStore {
    private let lock = NSLock()
    private var config = PadConfig()
    private let fileURL: URL

    private var observers: [(PadConfig, UUID?) -> Void] = []

    init() {
        let base = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first ?? URL(fileURLWithPath: NSHomeDirectory()).appendingPathComponent("Library/Application Support")
        let directory = base.appendingPathComponent("VibePad", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        fileURL = directory.appendingPathComponent("pad-config.json")
        if let data = try? Data(contentsOf: fileURL), let stored = PadConfig(payload: data) {
            config = stored
        }
    }

    /// 注册变更回调：参数为最新配置与发起方会话（Mac 自己改时为 nil），
    /// 服务端用它广播给其它会话，设置窗口用它刷新控件。
    func addObserver(_ observer: @escaping (PadConfig, UUID?) -> Void) {
        lock.lock()
        observers.append(observer)
        lock.unlock()
    }

    private func notify(_ config: PadConfig, _ session: UUID?) {
        lock.lock()
        let current = observers
        lock.unlock()
        current.forEach { $0(config, session) }
    }

    func snapshot() -> PadConfig {
        lock.lock()
        defer { lock.unlock() }
        return config
    }

    /// 平板发来的配置：revision 不小于本地时接受并广播，否则保留本地。
    /// 返回值永远是接受后的当前配置，调用方把它回给发来的会话。
    @discardableResult
    func accept(_ incoming: PadConfig, from session: UUID?) -> PadConfig {
        lock.lock()
        let previous = config
        // 旧版对端只发 skin，没有 layout/theme：缺失是「不关心」而非「设为默认」。
        // 保留本地布局与配色，否则新版配好的深夜蓝会被旧版静默改回经典黑。
        var candidate = incoming
        if !incoming.explicitAppearance {
            candidate.layout = previous.layout
            candidate.theme = previous.theme
        }
        let accepted = candidate.revision >= previous.revision && !candidate.sameContent(as: previous)
        if accepted {
            config = candidate
        } else if candidate.revision > previous.revision {
            // 内容一致但对方 revision 更高：对齐编号，避免两端反复互推同一份配置。
            config.revision = candidate.revision
        }
        let current = config
        lock.unlock()
        if accepted {
            persist(current)
            notify(current, session)
        }
        return current
    }

    /// Mac 侧修改：revision + 1 后落盘并推给所有已连接平板。
    func update(_ mutate: (inout PadConfig) -> Void) {
        lock.lock()
        var next = config
        mutate(&next)
        guard !next.sameContent(as: config) else {
            lock.unlock()
            return
        }
        next.revision = config.revision + 1
        config = next
        lock.unlock()
        persist(next)
        notify(next, nil)
    }

    private func persist(_ config: PadConfig) {
        guard let data = config.payload else { return }
        do {
            try data.write(to: fileURL, options: .atomic)
        } catch {
            print("Could not persist VibePad pad config: \(error)")
        }
    }
}

/// 设置窗口用的精简 App 描述，避免把 Helper 内部的 AppCatalog 暴露出去。
struct PadAppSummary: Equatable {
    let name: String
    let bundleID: String
}
