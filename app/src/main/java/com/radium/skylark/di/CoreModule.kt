package com.radium.skylark.di

import com.radium.skylark.bg.ProxyCore
import com.radium.skylark.bg.StubProxyCore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 内核绑定：当前指向桩实现，接入 `libbox.aar` 后改绑真实内核。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindProxyCore(impl: StubProxyCore): ProxyCore
}
