package com.digitalappsbd.app.epurreader.utils

import android.app.Activity
import android.os.Bundle
import com.digitalappsbd.app.epurreader.BuildConfig.DEBUG
import timber.log.Timber


class R2DispatcherActivity : Activity() {
  private val mMapper = R2IntentMapper(
    this, R2IntentHelper()
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    try {
      mMapper.dispatchIntent(intent)
    } catch (iae: IllegalArgumentException) {
      if (DEBUG) Timber.e(iae, "Deep links  - Invalid URI")
    } finally {
      finish()
    }
  }
}