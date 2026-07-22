package com.radium.skylark.update

/**
 * 语义化版本，支持 `0.1.0`（稳定）与 `0.1.0-beta.1`（预发布）。
 *
 * 比较规则遵循 SemVer：主/次/修订依次比较；主版本相同且一方为预发布时，
 * 预发布版本 < 正式版本；两者均为预发布时按 `beta.N` 的数字比较。
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<PreReleaseId> = emptyList(),
) : Comparable<SemVer> {

    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        (major - other.major).let { if (it != 0) return it }
        (minor - other.minor).let { if (it != 0) return it }
        (patch - other.patch).let { if (it != 0) return it }

        // 无预发布标识的版本优先级更高
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1

        val size = maxOf(preRelease.size, other.preRelease.size)
        for (i in 0 until size) {
            val a = preRelease.getOrNull(i)
            val b = other.preRelease.getOrNull(i)
            if (a == null) return -1
            if (b == null) return 1
            val cmp = a.compareTo(b)
            if (cmp != 0) return cmp
        }
        return 0
    }

    override fun toString(): String {
        val base = "$major.$minor.$patch"
        return if (preRelease.isEmpty()) base else base + "-" + preRelease.joinToString(".")
    }

    companion object {
        /** 解析版本字符串；允许前缀 `v`。无法解析时返回 null。 */
        fun parseOrNull(raw: String?): SemVer? {
            if (raw.isNullOrBlank()) return null
            val s = raw.trim().removePrefix("v").removePrefix("V")
            val (core, pre) = s.split("-", limit = 2).let {
                it[0] to it.getOrNull(1)
            }
            val parts = core.split(".")
            if (parts.size < 3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts[2].toIntOrNull() ?: return null
            val ids = pre?.split(".")?.map { PreReleaseId.of(it) } ?: emptyList()
            return SemVer(major, minor, patch, ids)
        }
    }
}

/** 预发布标识：数字段按数值比较，字符串段按字典序，数字段优先级低于字符串段。 */
sealed interface PreReleaseId : Comparable<PreReleaseId> {
    data class Numeric(val value: Int) : PreReleaseId {
        override fun toString(): String = value.toString()
    }

    data class Alphanumeric(val value: String) : PreReleaseId {
        override fun toString(): String = value
    }

    override fun compareTo(other: PreReleaseId): Int = when {
        this is Numeric && other is Numeric -> value - other.value
        this is Numeric && other is Alphanumeric -> -1
        this is Alphanumeric && other is Numeric -> 1
        this is Alphanumeric && other is Alphanumeric -> value.compareTo(other.value)
        else -> 0
    }

    companion object {
        fun of(token: String): PreReleaseId =
            token.toIntOrNull()?.let { Numeric(it) } ?: Alphanumeric(token)
    }
}
