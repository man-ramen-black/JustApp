package com.black.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import javax.inject.Named
import javax.inject.Singleton

typealias Hilt = EntryPointAccessors

@Module
@InstallIn(SingletonComponent::class)
object HiltModule {
    const val NAME_MAIN_SCOPE = "mainScope"

    @Singleton
    @Provides
    @Named(NAME_MAIN_SCOPE)
    fun provideMainScope(): CoroutineScope {
        return MainScope()
    }
}