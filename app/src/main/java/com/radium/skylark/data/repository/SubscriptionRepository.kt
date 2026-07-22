package com.radium.skylark.data.repository

import com.radium.skylark.converter.SubscriptionParser
import com.radium.skylark.converter.model.ParsedSubscription
import com.radium.skylark.data.db.ProfileDao
import com.radium.skylark.data.db.ProfileEntity
import com.radium.skylark.data.remote.SubscriptionFetcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 订阅仓库：拉取 → 解析 → 落库 → 刷新，并提供节点解析。
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val fetcher: SubscriptionFetcher,
) {
    fun observeProfiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    /** 从 URL 添加订阅：拉取内容、解析、保存。 */
    suspend fun addFromUrl(name: String, url: String, userAgent: String = "clash-verge"): Long {
        val content = fetcher.fetch(url, userAgent)
        val count = SubscriptionParser.parse(content).nodes.size
        return profileDao.upsert(
            ProfileEntity(
                name = name.ifBlank { url },
                type = "url",
                url = url,
                userAgent = userAgent,
                lastUpdatedAt = System.currentTimeMillis(),
                rawContent = content,
                nodeCount = count,
            ),
        )
    }

    /** 从粘贴/导入的文本添加订阅。 */
    suspend fun addFromText(name: String, text: String): Long {
        val count = SubscriptionParser.parse(text).nodes.size
        return profileDao.upsert(
            ProfileEntity(
                name = name.ifBlank { "导入的配置" },
                type = "manual",
                lastUpdatedAt = System.currentTimeMillis(),
                rawContent = text,
                nodeCount = count,
            ),
        )
    }

    /** 刷新（重新拉取）一个 url 类型的订阅。 */
    suspend fun refresh(profileId: Long) {
        val profile = profileDao.getById(profileId) ?: return
        val url = profile.url ?: return
        val content = fetcher.fetch(url, profile.userAgent)
        val count = SubscriptionParser.parse(content).nodes.size
        profileDao.update(
            profile.copy(
                rawContent = content,
                nodeCount = count,
                lastUpdatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(profile: ProfileEntity) = profileDao.delete(profile)

    /** 解析某订阅的节点与代理组。 */
    fun parse(profile: ProfileEntity): ParsedSubscription =
        SubscriptionParser.parse(profile.rawContent)
}
