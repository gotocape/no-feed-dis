package com.haijiao.nofeeddis

/**
 * 平台列表的唯一出口。
 *
 * 把「内置」和「用户自建」合并成一个列表，并在这里统一处理可见性，
 * 避免 MainActivity / SettingsActivity 各自拼装、各写一套过滤逻辑。
 *
 * 所有对规则的写操作也走这里，因为内置平台和自定义平台的存储位置不同：
 * - 内置：规则覆盖写在 `uris_<id>`
 * - 自定义：规则就写在平台自己的 JSON 里
 */
class PlatformRepository(private val prefs: Prefs) {

    /** 全部平台（内置在前、自定义在后）。设置页用。 */
    fun all(): List<Platform> = Defaults.PLATFORMS + prefs.customPlatforms()

    /** 首页只显示可见的平台。 */
    fun visible(): List<Platform> = all().filter { prefs.isVisible(it.id) }

    fun byId(id: String?): Platform? = id?.let { key -> all().firstOrNull { it.id == key } }

    fun urisOf(platform: Platform): List<String> = prefs.urisFor(platform)

    fun isCustomized(platform: Platform): Boolean =
        !platform.custom && prefs.isCustomized(platform.id)

    // ---------- 规则读写 ----------

    fun saveUris(platform: Platform, uris: List<String>) {
        if (platform.custom) {
            updateCustom(platform, uris = uris)
        } else {
            prefs.saveUris(platform.id, uris)
        }
    }

    /** 自定义平台没有「出厂默认」可恢复，调用方应先判断 [Platform.custom]。 */
    fun resetUris(platform: Platform) {
        if (!platform.custom) prefs.resetUris(platform.id)
    }

    // ---------- 自定义平台增删改 ----------

    fun createCustom(name: String, pkg: String?, uris: List<String>): Platform {
        val platform = Platform(
            id = "custom_" + System.currentTimeMillis(),
            name = name,
            pkg = pkg,
            defaultUris = uris,
            custom = true
        )
        prefs.saveCustomPlatforms(prefs.customPlatforms() + platform)
        return platform
    }

    fun updateCustom(
        platform: Platform,
        name: String = platform.name,
        pkg: String? = platform.pkg,
        uris: List<String> = platform.defaultUris
    ) {
        if (!platform.custom) return
        val list = prefs.customPlatforms().toMutableList()
        val index = list.indexOfFirst { it.id == platform.id }
        if (index < 0) return
        list[index] = platform.copy(name = name, pkg = pkg, defaultUris = uris)
        prefs.saveCustomPlatforms(list)
    }

    fun deleteCustom(platform: Platform) {
        if (!platform.custom) return
        prefs.saveCustomPlatforms(prefs.customPlatforms().filterNot { it.id == platform.id })
        prefs.resetUris(platform.id)
        // 删掉的正好是上次选中的，清空，让首页回退到第一个可见平台
        if (prefs.lastPlatformId == platform.id) prefs.lastPlatformId = null
        prefs.setVisible(platform.id, true)
    }

    // ---------- 可见性 ----------

    fun isVisible(platform: Platform): Boolean = prefs.isVisible(platform.id)

    fun setVisible(platform: Platform, visible: Boolean) = prefs.setVisible(platform.id, visible)
}
