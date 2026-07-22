package com.radium.skylark.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 订阅配置组。对应设计文档 §7 的 ProfileEntity（M0 为最小骨架，后续扩展字段）。
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** url / local / manual */
    val type: String,
    val url: String? = null,
    val userAgent: String = "clash-verge",
    val autoUpdateIntervalMinutes: Int = 0,
    val lastUpdatedAt: Long = 0,
    /** 拉取到的原始订阅内容，用于按需重新解析 */
    val rawContent: String = "",
    /** 解析得到的节点数量（缓存展示用） */
    val nodeCount: Int = 0,
)
