package com.digitalappsbd.epurreader

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.folioreader.Config
import com.folioreader.FolioReader
import com.folioreader.FolioReader.OnClosedListener
import com.folioreader.model.HighLight
import com.folioreader.model.locators.ReadLocator
import com.folioreader.util.AppUtil.Companion.getSavedConfig
import com.folioreader.util.OnHighlightListener
import com.folioreader.util.ReadLocatorListener
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class MainActivity : AppCompatActivity(), OnHighlightListener, ReadLocatorListener,
  OnClosedListener {

  private var folioReader: FolioReader? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    folioReader = FolioReader.get()
      .setOnHighlightListener(this)
      .setReadLocatorListener(this)
      .setOnClosedListener(this)
    var config =
      getSavedConfig(applicationContext)
    if (config == null) config = Config()
    config.allowedDirection = Config.AllowedDirection.VERTICAL_AND_HORIZONTAL

    folioReader?.setConfig(config, true)
      ?.openBook(R.raw.accessible_epub_3)

  }

  override fun onFolioReaderClosed() {
    Log.v(MainActivity::class.java.name, "-> onFolioReaderClosed");
  }

  override fun onHighlight(highlight: HighLight?, type: HighLight.HighLightAction?) {
    Toast.makeText(
      this,
      "highlight id = " + highlight!!.uuid + " type = " + type,
      Toast.LENGTH_SHORT
    ).show()
  }

  override fun saveReadLocator(readLocator: ReadLocator?) {
    Log.i(
      MainActivity::class.java.name,
      "-> saveReadLocator -> " + readLocator?.toJson()
    )
  }

  private fun getHighlightsAndSave() {
    Thread(Runnable {
      var highlightList: ArrayList<HighLight?>? = null
      val objectMapper = ObjectMapper()
      try {
        highlightList = objectMapper.readValue<ArrayList<HighLight?>>(
          loadAssetTextAsString("highlights/highlights_data.json"),
          object :
            TypeReference<List<HighlightData?>?>() {})
      } catch (e: IOException) {
        e.printStackTrace()
      }
      if (highlightList == null) {
        folioReader!!.saveReceivedHighLights(highlightList) {
          //You can do anything on successful saving highlight list
        }
      }
    }).start()
  }

  private fun loadAssetTextAsString(name: String): String? {
    var `in`: BufferedReader? = null
    try {
      val buf = StringBuilder()
      val `is` = assets.open(name)
      `in` = BufferedReader(InputStreamReader(`is`))
      var str: String?
      var isFirst = true
      while (`in`.readLine().also { str = it } != null) {
        if (isFirst) isFirst = false else buf.append('\n')
        buf.append(str)
      }
      return buf.toString()
    } catch (e: IOException) {
      Log.e("HomeActivity", "Error opening asset $name")
    } finally {
      if (`in` != null) {
        try {
          `in`.close()
        } catch (e: IOException) {
          Log.e("HomeActivity", "Error closing asset $name")
        }
      }
    }
    return null
  }

  override fun onDestroy() {
    super.onDestroy()
    FolioReader.clear()
  }
}
