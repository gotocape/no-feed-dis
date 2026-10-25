package com.haijiao.nofeeddis

/**
 * 内置平台与默认 URI 候选。
 *
 * ## 候选列表的语义（重要，别理解错）
 *
 * **第 1 条会被正式使用，其余只是备用/待验证。**
 * 原因见 [JumpEngine] 类注释：Android 只告诉我们「App 被唤起了」，
 * 不告诉我们「落在了哪个页面」，所以无法自动降级。
 *
 * 哪条真能到搜索结果页，必须在设置页点「测试」人工确认一次，然后把它挪到第一行。
 *
 * ## 实测状态（自己勾）
 *
 * - [x] 抖音   `snssdk1128://search?keyword=`  → 直达搜索结果页（用户实机确认）
 * - [ ] 小红书 待验证
 * - [ ] B站   待验证
 * - [ ] 知乎  待验证
 * - [ ] 夸克   待验证（v1.3 新增，替代已移除的百度）
 * - [ ] 微博  待验证
 * - [ ] 淘宝  待验证（旧 `taobao://` 用户反馈无法跳转，已改用 tbopen 协议）
 *
 * 用户还可以在设置页自建平台，那些存在 Prefs 的 `custom_platforms` 里，不在此文件。
 */
object Defaults {

    val PLATFORMS: List<Platform> = listOf(

        Platform(
            id = "xhs",
            name = "小红书",
            pkg = "com.xingin.xhs",
            defaultUris = listOf(
                // 首选。搜索结果页在 /search/result 下，不是 /search。
                // /search?keyword= 在多数版本只把 App 打开并停在首页或搜索框。
                "xhsdiscover://search/result?keyword={keyword}",
                "xhsdiscover://search?keyword={keyword}",
                "xhsdiscover://rn/search?keyword={keyword}",
                "xhsdiscover://search/result?keyword={keyword}&source=deeplink"
            )
        ),

        Platform(
            id = "douyin",
            name = "抖音",
            pkg = "com.ss.android.ugc.aweme",
            defaultUris = listOf(
                // 已实机确认可用：直达搜索结果页。真实 scheme 是 snssdk1128://，不是 douyin://
                "snssdk1128://search?keyword={keyword}",
                "snssdk1128://search/discover?keyword={keyword}",
                "aweme://search?keyword={keyword}"
            )
        ),

        Platform(
            id = "bili",
            name = "B站",
            pkg = "tv.danmaku.bili",
            // 国际版是另一个包名，scheme 相同。主版没装时自动落到国际版。
            altPkgs = listOf("com.bilibili.app.in"),
            defaultUris = listOf(
                "bilibili://search?keyword={keyword}",
                "bilibili://search?keyword={keyword}&from=deeplink"
            )
        ),

        Platform(
            id = "zhihu",
            name = "知乎",
            pkg = "com.zhihu.android",
            defaultUris = listOf(
                // 知乎参数名是 q，不是 keyword
                "zhihu://search?q={keyword}",
                "zhihu://search?type=content&q={keyword}"
            )
        ),

        Platform(
            id = "quark",
            name = "夸克",
            pkg = "com.quark.browser",
            defaultUris = listOf(
                // 夸克是浏览器，自己没有稳定的「搜索结果」深链。
                // 所以首选直接用神马搜索的网页地址，配合 setPackage 限定在夸克内打开 ——
                // 对浏览器来说这等价于「进 App 搜这个词」，比猜 scheme 可靠得多。
                "https://quark.sm.cn/s?q={keyword}",
                "https://www.quark.cn/s?q={keyword}",
                "quark://search?q={keyword}",
                "quark://search?keyword={keyword}"
            )
        ),

        Platform(
            id = "weibo",
            name = "微博",
            pkg = "com.sina.weibo",
            defaultUris = listOf(
                // containerid 的值本身是一段 query string（100103type=1&q=关键词），
                // 所以 = 与 & 必须先编码成 %3D / %26。
                // 关键词用 {keyword} 表示只编码一次；若失败改成 {keyword2} 试双重编码。
                "sinaweibo://searchall?containerid=100103type%3D1%26q%3D{keyword}",
                "sinaweibo://searchall?containerid=100103type%3D1%26q%3D{keyword2}",
                "sinaweibo://searchall?containerid=100103type%3D61%26q%3D{keyword}",
                "sinaweibo://search?q={keyword}"
            )
        ),

        Platform(
            id = "taobao",
            name = "淘宝",
            pkg = "com.taobao.taobao",
            defaultUris = listOf(
                // 淘宝从 2019 年起收紧自定义 scheme，旧 taobao://s.taobao.com/search?q=
                // 在多数版本只会打开首页或直接无响应。官方通道是 tbopen 协议：
                // 把目标 H5 地址整个 URL 编码后放进 h5Url 参数。
                // 因为 h5Url 里的 q 值还要再编码一次，关键词必须用 {keyword2}（编两次）。
                "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&h5Url=https%3A%2F%2Fs.taobao.com%2Fsearch%3Fq%3D{keyword2}",
                "taobao://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&h5Url=https%3A%2F%2Fs.taobao.com%2Fsearch%3Fq%3D{keyword2}",
                "taobao://s.taobao.com/search?q={keyword}",
                "taobao://m.taobao.com/search?q={keyword}"
            )
        )
    )

    fun byId(id: String?): Platform? = PLATFORMS.firstOrNull { it.id == id }
}
