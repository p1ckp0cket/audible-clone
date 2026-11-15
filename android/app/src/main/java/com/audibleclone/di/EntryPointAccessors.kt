package com.audibleclone.di

import android.app.Application
import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settingsDataStore(): com.audibleclone.data.local.datastore.SettingsDataStore
}

object EntryPointAccessors {
    fun fromApplication(context: Context): SettingsEntryPoint {
        val app = context.applicationContext as Application
        return hiltEntryPoint(app)
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun hiltEntryPoint(app: Application): SettingsEntryPoint {
        return dagger.hilt.android.EntryPoints.get(app, SettingsEntryPoint::class.java)
    }
}