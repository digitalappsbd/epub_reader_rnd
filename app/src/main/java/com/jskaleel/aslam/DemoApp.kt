package com.jskaleel.aslam

import androidx.multidex.MultiDex
import com.google.firebase.FirebaseApp
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump
import org.geometerplus.android.fbreader.FBReaderApplication
import org.geometerplus.android.fbreader.util.FBReaderConfig

class DemoApp : FBReaderApplication() {
  override fun onCreate() {
    super.onCreate()
    FirebaseApp.initializeApp(this)

    MultiDex.install(this@DemoApp)
    ViewPump.init(
      ViewPump.builder()
        .addInterceptor(
          CalligraphyInterceptor(
            CalligraphyConfig.Builder()
              .build()
          )
        )
        .build()
    )

    FBReaderConfig.init(this)
  }
}