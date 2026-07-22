package com.radium.skylark.di

import android.content.Context
import androidx.room.Room
import com.radium.skylark.data.db.ProfileDao
import com.radium.skylark.data.db.SkylarkDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SkylarkDatabase =
        Room.databaseBuilder(context, SkylarkDatabase::class.java, SkylarkDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideProfileDao(database: SkylarkDatabase): ProfileDao = database.profileDao()
}
