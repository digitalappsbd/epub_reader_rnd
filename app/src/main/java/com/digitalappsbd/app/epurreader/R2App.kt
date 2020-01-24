package com.digitalappsbd.app.epurreader

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import com.digitalappsbd.app.epurreader.BuildConfig.DEBUG
import nl.komponents.kovenant.android.startKovenant
import nl.komponents.kovenant.android.stopKovenant
import timber.log.Timber

class R2App : Application() {

  override fun onCreate() {
    super.onCreate()
    startKovenant()
    if (DEBUG) Timber.plant(Timber.DebugTree())
  }

  override fun onTerminate() {
    super.onTerminate()
    stopKovenant()
  }
}

val Context.resolver: ContentResolver
  get() = applicationContext.contentResolver
