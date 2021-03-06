package com.digitalappsbd.app.epurreader.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.digitalappsbd.app.epurreader.R
import com.digitalappsbd.app.epurreader.epub.ChapterAdapter
import com.digitalappsbd.app.epurreader.epub.EpubActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.mcxiaoke.koi.ext.dpToPx
import com.mcxiaoke.koi.ext.getActivity
import org.json.JSONArray
import org.readium.r2.navigator.R2BasicWebView
import org.readium.r2.navigator.R2WebView
import org.readium.r2.navigator.epub.fxl.R2FXLLayout
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.navigator.pager.R2ViewPager
import org.readium.r2.shared.*
import java.io.File


class UserSettings(
  var preferences: SharedPreferences,
  val context: Context,
  private val UIPreset: MutableMap<ReadiumCSSName, Boolean>,
  val publication: Publication,
  val bookId: Long
) {

  lateinit var resourcePager: R2ViewPager

  private val appearanceValues =
    listOf("readium-default-on", "readium-sepia-on", "readium-night-on")
  private val fontFamilyValues =
    listOf(
      "Original",
      "PT Serif",
      "Roboto",
      "Source Sans Pro",
      "Vollkorn",
      "OpenDyslexic",
      "SiyamRupali"
    )
  private val textAlignmentValues = listOf("justify", "start")
  private val columnCountValues = listOf("auto", "1", "2")

  private var fontSize = 100f
  private var fontOverride = false
  private var fontFamily = 0
  private var appearance = 0
  private var verticalScroll = false

  //Advanced settings
  private var publisherDefaults = false
  private var textAlignment = 0
  private var columnCount = 0
  private var wordSpacing = 0f
  private var letterSpacing = 0f
  private var pageMargins = 2f
  private var lineHeight = 1f

  private var userProperties: UserProperties
  private val tableOfContext = mutableListOf<Pair<Int, Link>>()
  private var callBack: OnChapterInterceptor

  init {
    appearance = preferences.getInt(APPEARANCE_REF, appearance)
    verticalScroll = preferences.getBoolean(SCROLL_REF, verticalScroll)
    fontFamily = preferences.getInt(FONT_FAMILY_REF, fontFamily)
    if (fontFamily != 0) {
      fontOverride = true
    }
    publisherDefaults = preferences.getBoolean(PUBLISHER_DEFAULT_REF, publisherDefaults)
    textAlignment = preferences.getInt(TEXT_ALIGNMENT_REF, textAlignment)
    columnCount = preferences.getInt(COLUMN_COUNT_REF, columnCount)


    fontSize = preferences.getFloat(FONT_SIZE_REF, fontSize)
    wordSpacing = preferences.getFloat(WORD_SPACING_REF, wordSpacing)
    letterSpacing = preferences.getFloat(LETTER_SPACING_REF, letterSpacing)
    pageMargins = preferences.getFloat(PAGE_MARGINS_REF, pageMargins)
    lineHeight = preferences.getFloat(LINE_HEIGHT_REF, lineHeight)
    userProperties = getUserSettings()

    //Setting up screen brightness
    val backLightValue = preferences.getInt("reader_brightness", 50).toFloat() / 100
    val layoutParams = (context as AppCompatActivity).window.attributes
    layoutParams.screenBrightness = backLightValue
    context.window.attributes = layoutParams
    callBack = context.getActivity() as OnChapterInterceptor
  }

  private fun getUserSettings(): UserProperties {

    val userProperties = UserProperties()
    // Publisher default system
    userProperties.addSwitchable(
      "readium-advanced-off",
      "readium-advanced-on",
      publisherDefaults,
      PUBLISHER_DEFAULT_REF,
      PUBLISHER_DEFAULT_NAME
    )
    // Font override
    userProperties.addSwitchable(
      "readium-font-on",
      "readium-font-off",
      fontOverride,
      FONT_OVERRIDE_REF,
      FONT_OVERRIDE_NAME
    )
    // Column count
    userProperties.addEnumerable(
      columnCount,
      columnCountValues,
      COLUMN_COUNT_REF,
      COLUMN_COUNT_NAME
    )
    // Appearance
    userProperties.addEnumerable(appearance, appearanceValues, APPEARANCE_REF, APPEARANCE_NAME)
    // Page margins
    userProperties.addIncremental(
      pageMargins,
      0.5f,
      4f,
      0.25f,
      "",
      PAGE_MARGINS_REF,
      PAGE_MARGINS_NAME
    )
    // Text alignment
    userProperties.addEnumerable(
      textAlignment,
      textAlignmentValues,
      TEXT_ALIGNMENT_REF,
      TEXT_ALIGNMENT_NAME
    )
    // Font family
    userProperties.addEnumerable(fontFamily, fontFamilyValues, FONT_FAMILY_REF, FONT_FAMILY_NAME)
    // Font size
    userProperties.addIncremental(fontSize, 100f, 300f, 25f, "%", FONT_SIZE_REF, FONT_SIZE_NAME)
    // Line height
    userProperties.addIncremental(lineHeight, 1f, 2f, 0.25f, "", LINE_HEIGHT_REF, LINE_HEIGHT_NAME)
    // Word spacing
    userProperties.addIncremental(
      wordSpacing,
      0f,
      0.5f,
      0.25f,
      "rem",
      WORD_SPACING_REF,
      WORD_SPACING_NAME
    )
    // Letter spacing
    userProperties.addIncremental(
      letterSpacing,
      0f,
      0.5f,
      0.0625f,
      "em",
      LETTER_SPACING_REF,
      LETTER_SPACING_NAME
    )
    // Scroll
    userProperties.addSwitchable(
      "readium-scroll-on",
      "readium-scroll-off",
      verticalScroll,
      SCROLL_REF,
      SCROLL_NAME
    )

    return userProperties
  }

  private fun makeJson(): JSONArray {
    val array = JSONArray()
    for (userProperty in userProperties.properties) {
      array.put(userProperty.getJson())
    }
    return array
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

  private fun updateEnumerable(enumerable: Enumerable) {
    preferences.edit().putInt(enumerable.ref, enumerable.index).apply()
    saveChanges()
  }


  private fun updateSwitchable(switchable: Switchable) {
    preferences.edit().putBoolean(switchable.ref, switchable.on).apply()
    saveChanges()
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

  fun fontControllerPopUp(): PopupWindow {

    val layoutInflater = LayoutInflater.from(context)
    val layout = layoutInflater.inflate(R.layout.layout_change_font, null)
    val fontSettingsPopup = PopupWindow(context)
    fontSettingsPopup.contentView = layout
    fontSettingsPopup.width = ListPopupWindow.MATCH_PARENT
    fontSettingsPopup.height = ListPopupWindow.WRAP_CONTENT
    fontSettingsPopup.isOutsideTouchable = true
    fontSettingsPopup.isFocusable = true

    val fontFamily = (userProperties.getByRef<Enumerable>(FONT_FAMILY_REF))
    val fontOverride = (userProperties.getByRef<Switchable>(FONT_OVERRIDE_REF))
    val recylerview: RecyclerView =
      layout.findViewById(R.id.spinner_action_settings_intervall_values) as RecyclerView

    val fonts = context.resources.getStringArray(R.array.font_list)
    val fontChangeAdapter = FontChangeAdapter { pos ->
      fontFamily.index = pos
      fontOverride.on = (pos != 0)
      updateSwitchable(fontOverride)
      updateEnumerable(fontFamily)
      updateViewCSS(FONT_OVERRIDE_REF)
      updateViewCSS(FONT_FAMILY_REF)
    }
    recylerview.adapter = fontChangeAdapter
    recylerview.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    fontChangeAdapter.submitList(fonts.asList())

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

    fontSettingsPopup.setBackgroundDrawable(null)
    return fontSettingsPopup

  }

  fun userSettingsPopUp(): PopupWindow {

    val layoutInflater = LayoutInflater.from(context)
    val layout = layoutInflater.inflate(R.layout.popup_window_user_settings, null)
    val userSettingsPopup = PopupWindow(context)
    userSettingsPopup.contentView = layout
    userSettingsPopup.width = 320.dpToPx()
    userSettingsPopup.height = ListPopupWindow.WRAP_CONTENT
    userSettingsPopup.isOutsideTouchable = true
    userSettingsPopup.isFocusable = true

    val publisherDefault = userProperties.getByRef<Switchable>(PUBLISHER_DEFAULT_REF)
    val scrollMode = userProperties.getByRef<Switchable>(SCROLL_REF)
    val alignment = userProperties.getByRef<Enumerable>(TEXT_ALIGNMENT_REF)
    val columnsCount = userProperties.getByRef<Enumerable>(COLUMN_COUNT_REF)
    val pageMargins = userProperties.getByRef<Incremental>(PAGE_MARGINS_REF)
    val wordSpacing = userProperties.getByRef<Incremental>(WORD_SPACING_REF)
    val letterSpacing = userProperties.getByRef<Incremental>(LETTER_SPACING_REF)
    val lineHeight = userProperties.getByRef<Incremental>(LINE_HEIGHT_REF)

    fun findIndexOfId(id: Int, list: MutableList<RadioButton>): Int {
      for (i in 0..list.size) {
        if (list[i].id == id) {
          return i
        }
      }
      return 0
    }
    // Publisher defaults
    val publisherDefaultSwitch = layout.findViewById(R.id.publisher_default) as Switch
    publisherDefaultSwitch.contentDescription = "\u00A0"

    publisherDefaultSwitch.isChecked = publisherDefault.on
    publisherDefaultSwitch.setOnCheckedChangeListener { _, b ->
      publisherDefault.on = b
      updateSwitchable(publisherDefault)
      updateViewCSS(PUBLISHER_DEFAULT_REF)
    }

    // Vertical scroll
    val scrollModeSwitch = layout.findViewById(R.id.scroll_mode) as Switch
    UIPreset[ReadiumCSSName.scroll]?.let { isSet ->
      scrollModeSwitch.isChecked = isSet
      scrollModeSwitch.isEnabled = false
    } ?: run {
      scrollModeSwitch.isChecked = scrollMode.on
      scrollModeSwitch.setOnCheckedChangeListener { _, b ->
        scrollMode.on = scrollModeSwitch.isChecked

        updateSwitchable(scrollMode)
        updateViewCSS(SCROLL_REF)

        val currentFragment = (resourcePager.adapter as R2PagerAdapter).getCurrentFragment()
        val previousFragment = (resourcePager.adapter as R2PagerAdapter).getPreviousFragment()
        val nextFragment = (resourcePager.adapter as R2PagerAdapter).getNextFragment()
        if (currentFragment is R2EpubPageFragment) {
          currentFragment.webView.scrollToPosition(currentFragment.webView.progression)
          (previousFragment as? R2EpubPageFragment)?.webView?.scrollToEnd()
          (nextFragment as? R2EpubPageFragment)?.webView?.scrollToStart()
          currentFragment.webView.setScrollMode(b)
          (previousFragment as? R2EpubPageFragment)?.webView?.setScrollMode(b)
          (nextFragment as? R2EpubPageFragment)?.webView?.setScrollMode(b)
          when (b) {
            true -> {
              currentFragment.view?.setPadding(0, 0, 0, 0)
              previousFragment?.view?.setPadding(0, 0, 0, 0)
              nextFragment?.view?.setPadding(0, 0, 0, 0)
            }
            false -> {
              currentFragment.view?.setPadding(0, 60, 0, 40)
              previousFragment?.view?.setPadding(0, 60, 0, 40)
              nextFragment?.view?.setPadding(0, 60, 0, 40)
            }
          }
        }
      }
    }

    // Text alignment
    val alignmentGroup = layout.findViewById(R.id.TextAlignment) as RadioGroup
    val alignmentRadios = mutableListOf<RadioButton>()
    alignmentRadios.add(layout.findViewById(R.id.alignment_left))
    (layout.findViewById(R.id.alignment_left) as RadioButton).contentDescription = "Alignment Left"

    alignmentRadios.add(layout.findViewById(R.id.alignment_justify))
    (layout.findViewById(R.id.alignment_justify) as RadioButton).contentDescription =
      "Alignment Justified"

    UIPreset[ReadiumCSSName.textAlignment]?.let {
      alignmentGroup.isEnabled = false
      alignmentGroup.isActivated = false
      for (alignmentRadio in alignmentRadios) {
        alignmentRadio.isEnabled = false
      }
    } ?: run {
      alignmentRadios[alignment.index].isChecked = true
      alignmentRadios[0].setCompoundDrawablesWithIntrinsicBounds(
        null,
        (if (alignment.index == 0) context.getDrawable(R.drawable.icon_justify_white) else context.getDrawable(
          R.drawable.icon_justify
        )),
        null, null
      )
      alignmentRadios[1].setCompoundDrawablesWithIntrinsicBounds(
        null,
        (if (alignment.index == 0) context.getDrawable(R.drawable.icon_left) else context.getDrawable(
          R.drawable.icon_left_white
        )),
        null, null
      )

      alignmentGroup.setOnCheckedChangeListener { _, i ->
        alignment.index = findIndexOfId(i, alignmentRadios)
        alignmentRadios[0].setCompoundDrawablesWithIntrinsicBounds(
          null,
          (if (alignment.index == 0) context.getDrawable(R.drawable.icon_justify_white) else context.getDrawable(
            R.drawable.icon_justify
          )),
          null, null
        )
        alignmentRadios[1].setCompoundDrawablesWithIntrinsicBounds(
          null,
          (if (alignment.index == 0) context.getDrawable(R.drawable.icon_left) else context.getDrawable(
            R.drawable.icon_left_white
          )),
          null, null
        )
        publisherDefaultSwitch.isChecked = false
        updateEnumerable(alignment)
        updateViewCSS(TEXT_ALIGNMENT_REF)
      }
    }

    // Column count
    val columnsCountGroup = layout.findViewById(R.id.columns) as RadioGroup
    val columnsRadios = mutableListOf<RadioButton>()
    columnsRadios.add(layout.findViewById(R.id.column_auto))
    (layout.findViewById(R.id.column_auto) as RadioButton).contentDescription = "Columns Auto"

    columnsRadios.add(layout.findViewById(R.id.column_one))
    (layout.findViewById(R.id.column_one) as RadioButton).contentDescription = "Columns 1"

    columnsRadios.add(layout.findViewById(R.id.column_two))
    (layout.findViewById(R.id.column_two) as RadioButton).contentDescription = "Columns 2"

    UIPreset[ReadiumCSSName.columnCount]?.let {
      columnsCountGroup.isEnabled = false
      columnsCountGroup.isActivated = false
      for (columnRadio in columnsRadios) {
        columnRadio.isEnabled = false
      }
    } ?: run {
      columnsRadios[columnsCount.index].isChecked = true
      columnsCountGroup.setOnCheckedChangeListener { _, id ->
        val i = findIndexOfId(id, columnsRadios)
        columnsCount.index = i
        publisherDefaultSwitch.isChecked = false
        updateEnumerable(columnsCount)
        updateViewCSS(COLUMN_COUNT_REF)
      }
    }

    // Page margins
    val pageMarginsDecreaseButton = layout.findViewById(R.id.pm_decrease) as ImageButton
    val pageMarginsIncreaseButton = layout.findViewById(R.id.pm_increase) as ImageButton
    val pageMarginsDisplay = layout.findViewById(R.id.pm_display) as TextView
    pageMarginsDisplay.text = pageMargins.value.toString()

    UIPreset[ReadiumCSSName.pageMargins]?.let {
      pageMarginsDecreaseButton.isEnabled = false
      pageMarginsIncreaseButton.isEnabled = false
    } ?: run {
      pageMarginsDecreaseButton.setOnClickListener {
        pageMargins.decrement()
        pageMarginsDisplay.text = pageMargins.value.toString()
        publisherDefaultSwitch.isChecked = false
        updateIncremental(pageMargins)
        updateViewCSS(PAGE_MARGINS_REF)
      }

      pageMarginsIncreaseButton.setOnClickListener {
        pageMargins.increment()
        pageMarginsDisplay.text = pageMargins.value.toString()
        publisherDefaultSwitch.isChecked = false
        updateIncremental(pageMargins)
        updateViewCSS(PAGE_MARGINS_REF)
      }
    }

    // Word spacing
    val wordSpacingDecreaseButton = layout.findViewById(R.id.ws_decrease) as ImageButton
    val wordSpacingIncreaseButton = layout.findViewById(R.id.ws_increase) as ImageButton
    val wordSpacingDisplay = layout.findViewById(R.id.ws_display) as TextView
    wordSpacingDisplay.text =
      (if (wordSpacing.value == wordSpacing.min) "auto" else wordSpacing.value.toString())

    UIPreset[ReadiumCSSName.wordSpacing]?.let {
      wordSpacingDecreaseButton.isEnabled = false
      wordSpacingIncreaseButton.isEnabled = false
    } ?: run {
      wordSpacingDecreaseButton.setOnClickListener {
        wordSpacing.decrement()
        wordSpacingDisplay.text =
          (if (wordSpacing.value == wordSpacing.min) "auto" else wordSpacing.value.toString())
        publisherDefaultSwitch.isChecked = false
        updateIncremental(wordSpacing)
        updateViewCSS(WORD_SPACING_REF)
      }

      wordSpacingIncreaseButton.setOnClickListener {
        wordSpacing.increment()
        wordSpacingDisplay.text = wordSpacing.value.toString()
        publisherDefaultSwitch.isChecked = false
        updateIncremental(wordSpacing)
        updateViewCSS(WORD_SPACING_REF)
      }
    }

    // Letter spacing
    val letterSpacingDecreaseButton = layout.findViewById(R.id.ls_decrease) as ImageButton
    val letterSpacingIncreaseButton = layout.findViewById(R.id.ls_increase) as ImageButton
    val letterSpacingDisplay = layout.findViewById(R.id.ls_display) as TextView
    letterSpacingDisplay.text =
      (if (letterSpacing.value == letterSpacing.min) "auto" else letterSpacing.value.toString())

    UIPreset[ReadiumCSSName.letterSpacing]?.let {
      letterSpacingDecreaseButton.isEnabled = false
      letterSpacingIncreaseButton.isEnabled = false
    } ?: run {
      letterSpacingDecreaseButton.setOnClickListener {
        letterSpacing.decrement()
        letterSpacingDisplay.text =
          (if (letterSpacing.value == letterSpacing.min) "auto" else letterSpacing.value.toString())
        publisherDefaultSwitch.isChecked = false
        updateIncremental(letterSpacing)
        updateViewCSS(LETTER_SPACING_REF)
      }

      letterSpacingIncreaseButton.setOnClickListener {
        letterSpacing.increment()
        letterSpacingDisplay.text =
          (if (letterSpacing.value == letterSpacing.min) "auto" else letterSpacing.value.toString())
        publisherDefaultSwitch.isChecked = false
        updateIncremental(letterSpacing)
        updateViewCSS(LETTER_SPACING_REF)
      }
    }

    // Line height
    val lineHeightDecreaseButton = layout.findViewById(R.id.lh_decrease) as ImageButton
    val lineHeightIncreaseButton = layout.findViewById(R.id.lh_increase) as ImageButton
    val lineHeightDisplay = layout.findViewById(R.id.lh_display) as TextView
    lineHeightDisplay.text =
      (if (lineHeight.value == lineHeight.min) "auto" else lineHeight.value.toString())

    UIPreset[ReadiumCSSName.lineHeight]?.let {
      lineHeightDecreaseButton.isEnabled = false
      lineHeightIncreaseButton.isEnabled = false
    } ?: run {
      lineHeightDecreaseButton.setOnClickListener {
        lineHeight.decrement()
        lineHeightDisplay.text =
          (if (lineHeight.value == lineHeight.min) "auto" else lineHeight.value.toString())
        publisherDefaultSwitch.isChecked = false
        updateIncremental(lineHeight)
        updateViewCSS(LINE_HEIGHT_REF)
      }
      lineHeightIncreaseButton.setOnClickListener {
        lineHeight.increment()
        lineHeightDisplay.text =
          (if (lineHeight.value == lineHeight.min) "auto" else lineHeight.value.toString())
        publisherDefaultSwitch.isChecked = false
        updateIncremental(lineHeight)
        updateViewCSS(LINE_HEIGHT_REF)
      }
    }
    // Speech speed
    val speechSeekBar = layout.findViewById(R.id.TTS_speech_speed) as SeekBar

    //Get the user settings value or set the progress bar to a neutral position (1 time speech speed).
    val speed =
      preferences.getInt("reader_TTS_speed", (2.75 * 3.toDouble() / 11.toDouble() * 100).toInt())

    speechSeekBar.progress = speed
    speechSeekBar.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(bar: SeekBar, progress: Int, from_user: Boolean) {
          // Nothing
        }

        override fun onStartTrackingTouch(bar: SeekBar) {
          // Nothing
        }

        override fun onStopTrackingTouch(bar: SeekBar) {
          //Convert seekBar percent to a float value between 0.25 and 3.
          val speechSpeed =
            0.25.toFloat() + (bar.progress.toFloat() / 100.toFloat()) * 2.75.toFloat()
          preferences.edit().putInt("reader_TTS_speed", bar.progress).apply()
          // TODO this might need to be refactored
          (context as EpubActivity).updateScreenReaderSpeed(speechSpeed, true)
        }
      })
    userSettingsPopup.setBackgroundDrawable(null)
    return userSettingsPopup
  }

  interface OnChapterInterceptor {
    fun onChapterClick(locator: Locator)
  }

  fun appearanceSettingsPopUp(): PopupWindow {
    val layoutInflater = LayoutInflater.from(context)
    val layout = layoutInflater.inflate(R.layout.layout_appearance_controller, null)
    val brightnessPopUp = PopupWindow(context)
    brightnessPopUp.contentView = layout
    brightnessPopUp.width = ListPopupWindow.MATCH_PARENT
    brightnessPopUp.height = ListPopupWindow.WRAP_CONTENT
    brightnessPopUp.isOutsideTouchable = true
    brightnessPopUp.isFocusable = true
    // Brightness
    val brightnessSeekBar = layout.findViewById(R.id.brightness) as SeekBar
    val brightness = preferences.getInt("reader_brightness", 50)
    brightnessSeekBar.progress = brightness
    brightnessSeekBar.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, progress: Int, from_user: Boolean) {
          val backLightValue = progress.toFloat() / 100
          val layoutParams = (context as AppCompatActivity).window.attributes
          layoutParams.screenBrightness = backLightValue
          context.window.attributes = layoutParams
          preferences.edit().putInt("reader_brightness", progress).apply()
        }

        override fun onStartTrackingTouch(bar: SeekBar) {}

        override fun onStopTrackingTouch(bar: SeekBar) {}
      })

    // Appearance
    val appearance = userProperties.getByRef<Enumerable>(APPEARANCE_REF)

    fun findIndexOfId(id: Int, list: MutableList<RadioButton>): Int {
      for (i in 0..list.size) {
        if (list[i].id == id) {
          return i
        }
      }
      return 0
    }

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
    brightnessPopUp.setBackgroundDrawable(null)
    return brightnessPopUp
  }

  private fun onChapterClick(position: Int, brightnessPopUp: PopupWindow? = null) {
    val resource = tableOfContext[position].second
    val resourceHref = resource.href
    val resourceType = resource.typeLink ?: ""
    var locator: Locator? = null

    resourceHref?.let {

      if (resourceHref.indexOf("#") > 0) {
        val id = resourceHref.substring(resourceHref.indexOf('#'))

        locator = Locator(
          resourceHref,
          resourceType,
          publication.metadata.title,
          Locations(fragment = id),
          null

        )
      } else {

        locator = Locator(
          resourceHref,
          resourceType,
          publication.metadata.title,
          Locations(progression = 0.0),
          null
        )


      }

    }
    locator?.let {
      callBack.onChapterClick(it)
    }

    brightnessPopUp?.dismiss()

  }


  fun chapterPopUp(): PopupWindow {
    val layoutInflater = LayoutInflater.from(context)
    val layout = layoutInflater.inflate(R.layout.layout_chapter, null)
    val brightnessPopUp = PopupWindow(
      layout,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
      true
    )
    brightnessPopUp.animationStyle = R.style.popup_window_animation_phone
    val chapterAdapter = ChapterAdapter { onChapterClick(it, brightnessPopUp) }
    val recylerview: RecyclerView =
      layout.findViewById(R.id.chapter_list) as RecyclerView
    recylerview.layoutManager = LinearLayoutManager(context)
    recylerview.adapter = chapterAdapter

    val contents: MutableList<Link> = when {
      publication.tableOfContents.isNotEmpty() -> {
        publication.tableOfContents
      }
      publication.readingOrder.isNotEmpty() -> {
        publication.readingOrder
      }
      publication.images.isNotEmpty() -> {
        publication.images
      }
      else -> mutableListOf()
    }

    for (link in contents) {
      val children = childrenOf(Pair(0, link))
      // Append parent.
      tableOfContext.add(Pair(0, link))
      // Append children, and their children... recursive.
      tableOfContext.addAll(children)
    }

    chapterAdapter.submitData(tableOfContext.toMutableList())
    brightnessPopUp.setBackgroundDrawable(null)
    return brightnessPopUp
  }

  private fun childrenOf(parent: Pair<Int, Link>): MutableList<Pair<Int, Link>> {
    val indentation = parent.first + 1
    val children = mutableListOf<Pair<Int, Link>>()
    for (link in parent.second.children) {
      children.add(Pair(indentation, link))
      children.addAll(childrenOf(Pair(indentation, link)))
    }
    return children
  }

  fun highlightPopUp(activity: Activity): PopupWindow {
    val layoutInflater = LayoutInflater.from(context)
    val layout = layoutInflater.inflate(R.layout.layout_highlights_bookmark, null)
    val highlightPopUp = PopupWindow(
      layout,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
      true
    )
    highlightPopUp.isTouchable = true
    highlightPopUp.animationStyle = R.style.popup_window_animation_phone
    val viewpager: ViewPager2 =
      layout.findViewById(R.id.viewpager)
    val tabLayout: TabLayout = layout.findViewById(R.id.tabLayout)
    val pagerAdapter = SavedContentPagerAdapter(activity as FragmentActivity, bookId, publication)
    viewpager.apply {
      adapter = pagerAdapter
      orientation = ViewPager2.ORIENTATION_HORIZONTAL
    }
    TabLayoutMediator(tabLayout, viewpager, true) { tab, position ->
      tab.text = pagerAdapter.getPageTitle(position)
    }.attach()
    highlightPopUp.setBackgroundDrawable(null)
    val backHome = layout.findViewById<ImageView>(R.id.image_back)
    backHome.setOnClickListener {
      highlightPopUp.dismiss()
    }
    return highlightPopUp
  }
}