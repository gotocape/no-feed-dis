package com.haijiao.nofeeddis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地存储，全部走 SharedPreferences，落在 App 私有目录。
 * 卸载即清除，无任何外部存储读写，因此不需要存储权限。
 *
 * 存五样东西：
 *   1. 内置平台被改过的 URI（每平台一个 key，行分隔）—— `uris_<id>`
 *   2. 用户自建平台（JSON 数组）—— `custom_platforms`
 *   3. 被隐藏的平台 id 集合 —— `hidden_platforms`
 *   4. 上次选中的平台 —— `last_platform`
 *   5. 「记住上次选择」开关 —— `remember_last`
 *
 * ## 可见性为什么单独存
 *
 * 如果把 visible 塞进平台对象里，那么每次内置平台表变动（新增夸克、
 * 移除百度）都要重建整张表，用户的选择就被覆盖了。
 * 单独存一个「隐藏 id 集合」，只在用户主动切换时写入，
 * 内置表怎么变都不影响 —— 也让老版本升级时的默认值自然成立（空集 = 全可见）。
 *
 * ## JSON 为什么用 org.json
 *
 * Android 系统自带，不需要引入 kotlinx-serialization 或 Gson。
 * 数据量极小（最多十几个平台），手写序列化完全够用，也不值得为它加依赖。
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("nofeeddis", Context.MODE_PRIVATE)

    // ---------- URI 规则 ----------

    /** 取某平台当前生效的 URI 列表：用户改过的优先，否则用内置默认。 */
    fun urisFor(platform: Platform): List<String> {
        val raw = sp.getString(KEY_URIS_PREFIX + platform.id, null) ?: return platform.defaultUris
        val parsed = parse(raw)
        return if (parsed.isEmpty()) platform.defaultUris else parsed
    }

    /** 仅用于内置平台。自定义平台的规则写在自己的 JSON 里，见 [saveCustomPlatforms]。 */
    fun saveUris(platformId: String, uris: List<String>) {
        sp.edit().putString(KEY_URIS_PREFIX + platformId, uris.joinToString("\n")).apply()
    }

    fun resetUris(platformId: String) {
        sp.edit().remove(KEY_URIS_PREFIX + platformId).apply()
    }

    fun isCustomized(platformId: String): Boolean =
        sp.contains(KEY_URIS_PREFIX + platformId)

    // ---------- 自定义平台 ----------

    fun customPlatforms(): List<Platform> {
        val raw = sp.getString(KEY_CUSTOM_PLATFORMS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val urisArr = obj.optJSONArray("uris") ?: JSONArray()
                val uris = (0 until urisArr.length())
                    .map { urisArr.optString(it) }
                    .filter { it.isNotBlank() }
                if (uris.isEmpty()) return@mapNotNull null
                Platform(
                    id = id,
                    name = obj.optString("name").ifBlank { id },
                    pkg = obj.optString("pkg").takeIf { it.isNotBlank() },
                    defaultUris = uris,
                    custom = true
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomPlatforms(list: List<Platform>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("pkg", p.pkg.orEmpty())
                    put("uris", JSONArray(p.defaultUris))
                }
            )
        }
        sp.edit().putString(KEY_CUSTOM_PLATFORMS, arr.toString()).apply()
    }

    // ---------- 可见性 ----------

    fun hiddenIds(): Set<String> =
        sp.getStringSet(KEY_HIDDEN_PLATFORMS, emptySet())
            ?.toSet()
            .orEmpty()

    fun isVisible(platformId: String): Boolean = platformId !in hiddenIds()

    fun setVisible(platformId: String, visible: Boolean) {
        val next = hiddenIds().toMutableSet()
        if (visible) next.remove(platformId) else next.add(platformId)
        sp.edit().putStringSet(KEY_HIDDEN_PLATFORMS, next).apply()
    }

    // ---------- 其它 ----------

    var lastPlatformId: String?
        get() = sp.getString(KEY_LAST_PLATFORM, null)
        set(value) {
            sp.edit().putString(KEY_LAST_PLATFORM, value).apply()
        }

    var rememberLast: Boolean
        get() = sp.getBoolean(KEY_REMEMBER_LAST, true)
        set(value) {
            sp.edit().putBoolean(KEY_REMEMBER_LAST, value).apply()
        }

    companion object {
        private const val KEY_URIS_PREFIX = "uris_"
        private const val KEY_CUSTOM_PLATFORMS = "custom_platforms"
        private const val KEY_HIDDEN_PLATFORMS = "hidden_platforms"
        private const val KEY_LAST_PLATFORM = "last_platform"
        private const val KEY_REMEMBER_LAST = "remember_last"

        /** 多行文本 → URI 列表。去空行、去 # 开头的注释行。 */
        fun parse(text: CharSequence?): List<String> =
            text?.toString().orEmpty()
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
    }
}
