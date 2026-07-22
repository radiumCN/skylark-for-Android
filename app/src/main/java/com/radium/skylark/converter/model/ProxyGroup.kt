package com.radium.skylark.converter.model

/**
 * 代理组（对应 Clash 的 proxy-groups，映射为 sing-box 的 selector / urltest）。
 */
data class ProxyGroup(
    val name: String,
    val type: GroupType,
    /** 成员引用：可为节点 tag、其它组名，或特殊值 DIRECT / REJECT */
    val members: List<String>,
    val testUrl: String? = null,
    val intervalSeconds: Int? = null,
    val tolerance: Int? = null,
) {
    enum class GroupType {
        /** Clash `select` -> sing-box selector */
        SELECT,

        /** Clash `url-test` / `fallback` / `load-balance` -> sing-box urltest */
        URLTEST,
    }
}

/**
 * 一次订阅解析的统一结果：节点 + 组。
 */
data class ParsedSubscription(
    val nodes: List<ProxyNode>,
    val groups: List<ProxyGroup> = emptyList(),
) {
    companion object {
        val EMPTY = ParsedSubscription(emptyList(), emptyList())
    }
}
