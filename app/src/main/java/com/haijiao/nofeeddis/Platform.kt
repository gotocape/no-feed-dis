package com.haijiao.nofeeddis

/**
 * 一个平台 = 显示名 + 主包名 + URI 候选。
 *
 * [pkg] 为 null 表示不限定包名，交给系统分发（可能弹选择器）。
 * 一般都应填包名，配合 setPackage() 避免跳错 App。
 *
 * [altPkgs] 是**备用包名**，用于同一产品有多个发行版的场景：
 * - B 站：大陆版 `tv.danmaku.bili`，国际版 `com.bilibili.app.in`
 * 主包名的所有 URI 都失败后，才会依次试备用包名。
 *
 * [custom] 区分来源：
 * - false = 代码里内置的（可改规则、可隐藏，但不可删）
 * - true  = 用户自建，存在本地 JSON 里（可改可删）
 *
 * 注意：**可见性不在这里**。它存在 Prefs 的隐藏 id 集合里，
 * 这样内置平台升级（比如新增夸克、移除百度）不会覆盖用户的选择。
 */
data class Platform(
    val id: String,
    val name: String,
    val pkg: String?,
    val defaultUris: List<String>,
    val altPkgs: List<String> = emptyList(),
    val custom: Boolean = false
)
