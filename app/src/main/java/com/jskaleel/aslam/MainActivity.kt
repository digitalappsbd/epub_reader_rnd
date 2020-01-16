package com.jskaleel.aslam

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.geometerplus.android.fbreader.FBReader
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    val filePath = getFileFromAssets(this, "khona.epub").absolutePath
    FBReader.openBookActivity(this, filePath)
  }


  @Throws(IOException::class)
  fun getFileFromAssets(context: Context, fileName: String): File = File(context.cacheDir, fileName)
    .also {
      it.outputStream().use { cache ->
        context.assets.open(fileName).use { inputStream ->
          inputStream.copyTo(cache)
        }
      }
    }
}

