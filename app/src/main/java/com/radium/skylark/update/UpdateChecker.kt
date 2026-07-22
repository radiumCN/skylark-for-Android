package com.radium.skylark.update

import com.radium.skylark.BuildConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通过 GitHub Releases API 检查更新。
 *
 * 稳定通道只考虑正式版；测试通道会同时考虑预发布与正式版，取更新者。
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(
        channel: UpdateChannel,
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): UpdateResult = withContext(Dispatchers.IO) {
        val current = SemVer.parseOrNull(currentVersion)
            ?: return@withContext UpdateResult.Failed("无法解析当前版本：$currentVersion")

        val releases = runCatching { fetchReleases() }
            .getOrElse { return@withContext UpdateResult.Failed(it.message ?: "网络请求失败") }

        val candidates = releases.asSequence()
            .filter { !it.draft }
            .filter { if (channel == UpdateChannel.STABLE) !it.prerelease else true }
            .mapNotNull { release ->
                SemVer.parseOrNull(release.tagName)?.let { ver -> release to ver }
            }
            .toList()

        val best = candidates.maxByOrNull { it.second } ?: run {
            return@withContext UpdateResult.UpToDate(currentVersion)
        }

        if (best.second > current) {
            val (release, ver) = best
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            UpdateResult.UpdateAvailable(
                ReleaseInfo(
                    version = ver,
                    tag = release.tagName,
                    notes = release.body?.takeIf { it.isNotBlank() }
                        ?: release.name.orEmpty(),
                    htmlUrl = release.htmlUrl,
                    apkUrl = apk?.browserDownloadUrl,
                    prerelease = release.prerelease,
                ),
            )
        } else {
            UpdateResult.UpToDate(currentVersion)
        }
    }

    private fun fetchReleases(): List<GitHubRelease> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Skylark-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("空响应")
            return json.decodeFromString(body)
        }
    }

    private companion object {
        const val REPO = "radiumCN/skylark-for-Android"
    }
}
