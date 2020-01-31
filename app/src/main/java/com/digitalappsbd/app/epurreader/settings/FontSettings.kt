package com.digitalappsbd.app.epurreader.settings

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.PopupWindow
import androidx.appcompat.widget.ListPopupWindow
import com.digitalappsbd.app.epurreader.R
import org.json.JSONArray
import org.readium.r2.navigator.R2BasicWebView
import org.readium.r2.navigator.R2WebView
import org.readium.r2.navigator.epub.fxl.R2FXLLayout
import org.readium.r2.navigator.pager.R2ViewPager
import org.readium.r2.shared.*
import java.io.File

class FontSettings(
  var preferences: SharedPreferences,
  val context: Context,
  private val UIPreset: MutableMap<ReadiumCSSName, Boolean>
) {

  lateinit var resourcePager: R2ViewPager
  private var fontSize = 100f

  private var userProperties: UserProperties

  init {

    fontSize = preferences.getFloat(FONT_SIZE_REF, fontSize)
    userProperties = getUserSettings()
  }

  private fun getUserSettings(): UserProperties {
    val userProperties = UserProperties()
    userProperties.addIncremental(fontSize, 100f, 300f, 25f, "%", FONT_SIZE_REF, FONT_SIZE_NAME)
    return userProperties
  }

  fun saveChanges() {
    val json = makeJson()
    val dir = File(context.filesDir.path + "/" + Injectable.Style.rawValue + "/")
    dir.mkdirs()
    val file = File(dir, "UserProperties.json")
    file.printWriter().use { out ->
      out.println(json)
    }
  }

  private fun makeJson(): JSONArray {
    val array = JSONArray()
    for (userProperty in userProperties.properties) {
      array.put(userProperty.getJson())
    }
    return array
  }


  fun fontSettingsPopUp(): PopupWindow {
    val layoutInflater = LayoutInflater.from(context)
    val layout = layoutInflater.inflate(R.layout.layout_font_settings, null)
    val popupWindow = PopupWindow(context)
    popupWindow.contentView = layout
    popupWindow.width = ListPopupWindow.MATCH_PARENT
    popupWindow.height = ListPopupWindow.WRAP_CONTENT
    popupWindow.isOutsideTouchable = true
    popupWindow.isFocusable = true
    val fontSize = userProperties.getByRef<Incremental>(FONT_SIZE_REF)

    val fontDecreaseButton = layout.findViewById(R.id.font_decrease) as ImageButton
    val fontIncreaseButton = layout.findViewById(R.id.font_increase) as ImageButton

    UIPreset[ReadiumCSSName.fontSize]?.let {
      fontDecreaseButton.isEnabled = false
      fontIncreaseButton.isEnabled = false
    } ?: run {
      fontDecreaseButton.setOnClickListener {
        fontSize.decrement()
        updateIncremental(fontSize)
        updateViewCSS(FONT_SIZE_REF)
      }

      fontIncreaseButton.setOnClickListener {
        fontSize.increment()
        updateIncremental(fontSize)
        updateViewCSS(FONT_SIZE_REF)
      }
    }

    return popupWindow

  }

  private fun updateIncremental(incremental: Incremental) {
    preferences.edit().putFloat(incremental.ref, incremental.value).apply()
    saveChanges()
  }

  fun updateViewCSS(ref: String) {
    for (i in 0 until resourcePager.childCount) {
      val webView = resourcePager.getChildAt(i).findViewById(R.id.webView) as? R2WebView
      webView?.let {
        applyCSS(webView, ref)
      } ?: run {
        val zoomView = resourcePager.getChildAt(i).findViewById(R.id.r2FXLLayout) as R2FXLLayout
        val webView1 = zoomView.findViewById(R.id.firstWebView) as? R2BasicWebView
        val webView2 = zoomView.findViewById(R.id.secondWebView) as? R2BasicWebView
        val webViewSingle = zoomView.findViewById(R.id.webViewSingle) as? R2BasicWebView

        webView1?.let {
          applyCSS(webView1, ref)
        }
        webView2?.let {
          applyCSS(webView2, ref)
        }
        webViewSingle?.let {
          applyCSS(webViewSingle, ref)
        }
      }
    }
  }

  private fun applyCSS(view: R2BasicWebView, ref: String) {
    val userSetting = userProperties.getByRef<UserProperty>(ref)
    view.setProperty(userSetting.name, userSetting.toString())
  }


}