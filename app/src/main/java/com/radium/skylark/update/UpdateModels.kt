package com.radium.skylark.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 更新通道。 */
enum class UpdateChannel {
    /** 仅接收正式版。 */
    STABLE,

    /** 接收预发布版（beta），也会提示更新的正式版。 */
    BETA;

    companion object {
        /** 根据当前版本推断默认通道：预发布版默认走 beta。 */
        fun default(currentVersion: String): UpdateChannel =
            if (SemVer.parseOrNull(currentVersion)?.isPreRelease == true) BETA else STABLE
    }
}

/** 一个可安装的发布。 */
data class ReleaseInfo(
    val version: SemVer,
    val tag: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val prerelease: Boolean,
)

/** 检查更新结果。 */
sealed interface UpdateResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateResult
    data class UpToDate(val current: String) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
