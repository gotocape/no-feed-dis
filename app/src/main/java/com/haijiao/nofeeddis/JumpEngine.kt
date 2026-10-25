package com.haijiao.nofeeddis

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 全 App 唯一有技术含量的模块：把「平台 + 关键词」变成一次真实的 App 跳转。
 *
 * ## 一条必须记住的事实
 *
 * `startActivity()` 只回答「有没有 Activity 被启动」，**不回答「跳到了哪个页面」**。
 * 所以本引擎无法判断目标 App 是停在搜索结果页，还是只开了首页。
 *
 * 这带来一个反直觉的后果：**多 URI 候选不是自动降级链**。
 * 只要第 1 条能把 App 唤起来（哪怕只到首页），它就返回 Success，
 * 第 2 条永远没有机会执行。这是平台只认 scheme 不认 path 时的必然结果。
 *
 * 因此候选列表的语义是：**第 1 条 = 正式使用，其余 = 备用/待验证**。
 * 哪条真的能到搜索结果页，只能靠人在设置页逐条「测试」确认。
 * 不要试图用无障碍服务或截图去自动判断 —— 那要么废掉零权限，要么不可靠。
 *
 * ## 三个占位符
 *
 * - `{keyword}`    编码一次。绝大多数平台用这个。
 * - `{keyword2}`   编码两次。用于嵌套 query 的场景，典型是微博的 containerid：
 *                  它的值本身是一段 query string（`100103type=1&q=关键词`），
 *                  关键词要先编码进内层，整个值再编码一次进外层。
 * - `{keywordRaw}` 原样插入，不编码。仅在明确知道对方要原始值时使用。
 */
object JumpEngine {

    const val PLACEHOLDER = "{keyword}"
    const val PLACEHOLDER_DOUBLE = "{keyword2}"
    const val PLACEHOLDER_RAW = "{keywordRaw}"

    /** 关键词上限（按 Unicode 码点计）。超长会让部分 ROM 的 Binder 传输出问题。 */
    const val MAX_KEYWORD_CODE_POINTS = 200

    sealed interface Result {
        /** 已唤起目标 App。注意：不代表到了搜索结果页。 */
        data class Success(val truncated: Boolean) : Result

        /** 关键词为空 / 全空格。 */
        data object EmptyKeyword : Result

        /** 一条都没能唤起（未安装，或所有 URI 的 scheme 都无效）。 */
        data object AllFailed : Result
    }

    /** 模板里是否含任一可用占位符。设置页保存前用它做校验。 */
    fun hasPlaceholder(template: String): Boolean =
        template.contains(PLACEHOLDER) ||
            template.contains(PLACEHOLDER_DOUBLE) ||
            template.contains(PLACEHOLDER_RAW)

    /**
     * 用关键词填充模板。
     *
     * 替换顺序有讲究：先处理 `{keyword2}` 和 `{keywordRaw}`，再处理 `{keyword}`。
     * 因为 `{keyword}` 是 `{keyword2}` 和 `{keywordRaw}` 的前缀，
     * 反过来先替换会把后两者破坏掉。
     */
    fun fillTemplate(template: String, keyword: String): String {
        val once = Uri.encode(keyword)
        return template
            .replace(PLACEHOLDER_DOUBLE, Uri.encode(once))
            .replace(PLACEHOLDER_RAW, keyword)
            .replace(PLACEHOLDER, once)
    }

    fun jump(
        context: Context,
        platform: Platform,
        uris: List<String>,
        rawKeyword: String
    ): Result {
        val trimmed = rawKeyword.trim()
        if (trimmed.isEmpty()) return Result.EmptyKeyword

        val truncated = trimmed.codePointCount(0, trimmed.length) > MAX_KEYWORD_CODE_POINTS
        val keyword = if (truncated) {
            trimmed.takeByCodePoints(MAX_KEYWORD_CODE_POINTS)
        } else {
            trimmed
        }

        // 主包名优先；主包名全部失败后试备用包名（同一产品的不同发行版，如 B 站国际版）
        val packages: List<String?> = buildList {
            platform.pkg?.takeIf { it.isNotBlank() }?.let { add(it) }
            platform.altPkgs.filter { it.isNotBlank() }.forEach { add(it) }
        }.ifEmpty { listOf(null) }

        for (pkg in packages) {
            for (template in uris) {
                if (!hasPlaceholder(template)) continue
                try {
                    val uri = Uri.parse(fillTemplate(template, keyword))
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        pkg?.let { setPackage(it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    // 走到这里只说明「App 被唤起了」。见类注释：无法判断落在哪个页面。
                    return Result.Success(truncated)
                } catch (e: Exception) {
                    // ActivityNotFoundException：该包未安装，或该 scheme 不被它接收
                    // SecurityException：目标 App 拒绝响应
                    // IllegalArgumentException：模板是非法 URI
                    // 三种都只意味着「这条连 App 都打不开」，继续试下一条。
                }
            }
        }

        return Result.AllFailed
    }

    /**
     * 按 Unicode 码点截断。
     * 直接用 String.take(n) 是按 char 截的，会把 emoji 的代理对切成两半产生乱码。
     */
    fun String.takeByCodePoints(max: Int): String {
        if (codePointCount(0, length) <= max) return this
        return substring(0, offsetByCodePoints(0, max))
    }
}
