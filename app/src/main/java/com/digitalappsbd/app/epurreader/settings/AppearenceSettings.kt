package com.digitalappsbd.app.epurreader.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.*
import com.digitalappsbd.app.epurreader.R
import org.json.JSONArray
import org.readium.r2.navigator.R2BasicWebView
import org.readium.r2.navigator.R2WebView
import org.readium.r2.navigator.epub.fxl.R2FXLLayout
import org.readium.r2.navigator.pager.R2ViewPager
import org.readium.r2.shared.*
import java.io.File

class AppearenceSettings(
  var preferences: SharedPreferences,
  val context: Context,
  private val UIPreset: MutableMap<ReadiumCSSName, Boolean>
) {

  lateinit var resourcePager: R2ViewPager

  private val appearanceValues =
    listOf("readium-default-on", "readium-sepia-on", "readium-night-on")
  private var appearance = 0
  private var userProperties: UserProperties

  init {
    appearance = preferences.getInt(APPEARANCE_REF, appearance)
    userProperties = getAppearanceSettings()

  }

  private fun getAppearanceSettings(): UserProperties {
    val userProperties = UserProperties()
    userProperties.addEnumerable(appearance, appearanceValues, APPEARANCE_REF, APPEARANCE_NAME)
    return userProperties
  }

  private fun makeJson(): JSONArray {
    val array = JSONArray()
    for (userProperty in userProperties.properties) {
      array.put(userProperty.getJson())
    }
    return array
  }


  fun saveAppearanceChanges() {
    val json = makeJson()
    val dir = File(context.filesDir.path + "/" + Injectable.Style.rawValue + "/")
    dir.mkdirs()
    val file = File(dir, "UserProperties.json")
    file.printWriter().use { out ->
      out.println(json)
    }
  }

  private fun updateEnumerable(enumerable: Enumerable) {
    preferences.edit().putInt(enumerable.ref, enumerable.index).apply()
    saveAppearanceChanges()
  }


  private fun updateSwitchable(switchable: Switchable) {
    preferences.edit().putBoolean(switchable.ref, switchable.on).apply()
    saveAppearanceChanges()
  }

  private fun updateIncremental(incremental: Incremental) {
    preferences.edit().putFloat(incremental.ref, incremental.value).apply()
    saveAppearanceChanges()
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


  fun userAppearancePopUp(): PopupWindow {

    val layoutInflater = LayoutInflater.from(context)
    val layout = layoutInflater.inflate(R.layout.layout_appearance_settings, null)
    val userSettingsPopup = PopupWindow(context)
    userSettingsPopup.contentView = layout
    userSettingsPopup.width = ListPopupWindow.WRAP_CONTENT
    userSettingsPopup.height = ListPopupWindow.WRAP_CONTENT
    userSettingsPopup.isOutsideTouchable = true
    userSettingsPopup.isFocusable = true

    val appearance = userProperties.getByRef<Enumerable>(APPEARANCE_REF)


    fun findIndexOfId(id: Int, list: MutableList<RadioButton>): Int {
      for (i in 0..list.size) {
        if (list[i].id == id) {
          return i
        }
      }
      return 0
    }
    // Appearance
    val appearanceGroup = layout.findViewById(R.id.appearance) as RadioGroup
    val appearanceRadios = mutableListOf<RadioButton>()
    appearanceRadios.add(layout.findViewById(R.id.appearance_default) as RadioButton)
    (layout.findViewById(R.id.appearance_default) as RadioButton).contentDescription =
      "Appearance Default"
    appearanceRadios.add(layout.findViewById(R.id.appearance_sepia) as RadioButton)
    (layout.findViewById(R.id.appearance_sepia) as RadioButton).contentDescription =
      "Appearance Sepia"
    appearanceRadios.add(layout.findViewById(R.id.appearance_night) as RadioButton)
    (layout.findViewById(R.id.appearance_night) as RadioButton).contentDescription =
      "Appearance Night"

    UIPreset[ReadiumCSSName.appearance]?.let {
      appearanceGroup.isEnabled = false
      for (appearanceRadio in appearanceRadios) {
        appearanceRadio.isEnabled = false
      }
    } ?: run {
      appearanceRadios[appearance.index].isChecked = true

      appearanceGroup.setOnCheckedChangeListener { _, id ->
        val i = findIndexOfId(id, list = appearanceRadios)
        appearance.index = i
        when (i) {
          0 -> {
            resourcePager.setBackgroundColor(Color.parseColor("#ffffff"))
            (resourcePager.focusedChild?.findViewById(R.id.book_title) as? TextView)?.setTextColor(
              Color.parseColor("#000000")
            )
          }
          1 -> {
            resourcePager.setBackgroundColor(Color.parseColor("#faf4e8"))
            (resourcePager.focusedChild?.findViewById(R.id.book_title) as? TextView)?.setTextColor(
              Color.parseColor("#000000")
            )
          }
          2 -> {
            resourcePager.setBackgroundColor(Color.parseColor("#000000"))
            (resourcePager.focusedChild?.findViewById(R.id.book_title) as? TextView)?.setTextColor(
              Color.parseColor("#ffffff")
            )
          }
        }
        updateEnumerable(appearance)
        updateViewCSS(APPEARANCE_REF)
      }
    }

    return userSettingsPopup
  }
}