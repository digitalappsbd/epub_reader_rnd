/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator

import android.content.Context
import android.os.Build
import android.text.Html
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.readium.r2.shared.SCROLL_REF
import org.readium.r2.shared.getAbsolute


/**
 * Created by Aferdita Muriqi on 12/2/17.
 */

open class R2BasicWebView(context: Context, attrs: AttributeSet) : WebView(context, attrs) {

  lateinit var activity: AppCompatActivity
  lateinit var listener: IR2Activity
  lateinit var navigator: Navigator

  var progression: Double = 0.0
  var overrideUrlLoading = true
  var resourceUrl: String? = null

  var callback: OnOverScrolledCallback? = null

  private val uiScope = CoroutineScope(Dispatchers.Main)

  init {
    setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
  }

  interface OnOverScrolledCallback {
    fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean)
  }

  fun setOnOverScrolledCallback(callback: OnOverScrolledCallback) {
    this.callback = callback
  }

  override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
    if (callback != null) {
      callback?.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }
    super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
  }


  @android.webkit.JavascriptInterface
  open fun scrollRight(animated: Boolean = false) {
    uiScope.launch {
      if (activity.supportActionBar!!.isShowing && listener.allowToggleActionBar) {
        listener.resourcePager?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
            or View.SYSTEM_UI_FLAG_IMMERSIVE)
      }
      val scrollMode = listener.preferences.getBoolean(SCROLL_REF, false)
      if (scrollMode) {
        if (listener.publication.metadata.direction == "rtl") {
          this@R2BasicWebView.evaluateJavascript("scrollRightRTL();") { result ->
            if (result.contains("edge")) {
              navigator.goBackward(animated = animated)
            }
          }
        } else {
          navigator.goForward(animated = animated)
        }
      } else {
        if (!this@R2BasicWebView.canScrollHorizontally(1)) {
          navigator.goForward(animated = animated)
        }
        this@R2BasicWebView.evaluateJavascript("scrollRight();", null)
      }
    }
  }

  @android.webkit.JavascriptInterface
  open fun scrollLeft(animated: Boolean = false) {
    uiScope.launch {
      if (activity.supportActionBar!!.isShowing && listener.allowToggleActionBar) {
        listener.resourcePager?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
            or View.SYSTEM_UI_FLAG_IMMERSIVE)
      }
      val scrollMode = listener.preferences.getBoolean(SCROLL_REF, false)
      if (scrollMode) {
        if (listener.publication.metadata.direction == "rtl") {
          this@R2BasicWebView.evaluateJavascript("scrollLeftRTL();") { result ->
            if (result.contains("edge")) {
              navigator.goForward(animated = animated)
            }
          }
        } else {
          navigator.goBackward(animated = animated)
        }
      } else {
        // fix this for when vertical scrolling is enabled
        if (!this@R2BasicWebView.canScrollHorizontally(-1)) {
          navigator.goBackward(animated = animated)
        }
        this@R2BasicWebView.evaluateJavascript("scrollLeft();", null)
      }
    }
  }

  @android.webkit.JavascriptInterface
  fun progressionDidChange(positionString: String) {
    progression = positionString.toDouble()
    listener.progressionDidChange(progression)
  }

  @android.webkit.JavascriptInterface
  fun centerTapped() {
    listener.toggleActionBar()
  }

  @android.webkit.JavascriptInterface
  fun handleClick(html: String) {
    val doc = Jsoup.parse(html)
    val link = doc.select("a[epub:type=noteref]")?.first()
    link?.let { noteref ->
      val href = noteref.attr("href")
      if (href.indexOf("#") > 0) {
        val id = href.substring(href.indexOf('#') + 1)
        var absolute = getAbsolute(href, resourceUrl!!)
        absolute = absolute.substring(0, absolute.indexOf("#"))
        val document = Jsoup.connect(absolute).get()
        val aside = document.select("aside#$id").first()?.html()
        aside?.let {
          val safe = Jsoup.clean(aside, Whitelist.relaxed())

          // Initialize a new instance of LayoutInflater service
          val inflater =
            activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

          // Inflate the custom layout/view
          val customView = inflater.inflate(R.layout.popup_footnote, null)

          // Initialize a new instance of popup window
          val mPopupWindow = PopupWindow(
            customView,
            ListPopupWindow.WRAP_CONTENT,
            ListPopupWindow.WRAP_CONTENT
          )
          mPopupWindow.isOutsideTouchable = true
          mPopupWindow.isFocusable = true

          // Set an elevation value for popup window
          // Call requires API level 21
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPopupWindow.elevation = 5.0f
          }

          val textView = customView.findViewById(R.id.footnote) as TextView
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.text = Html.fromHtml(safe, Html.FROM_HTML_MODE_COMPACT)
          } else {
            textView.text = Html.fromHtml(safe)
          }

          // Get a reference for the custom view close button
          val closeButton = customView.findViewById(R.id.ib_close) as ImageButton

          // Set a click listener for the popup window close button
          closeButton.setOnClickListener {
            // Dismiss the popup window
            mPopupWindow.dismiss()
          }

          // Finally, show the popup window at the center location of root relative layout
          mPopupWindow.showAtLocation(this, Gravity.CENTER, 0, 0)

          overrideUrlLoading = false
        }
      }
    }
  }

  @android.webkit.JavascriptInterface
  fun highlightActivated(id: String) {
    uiScope.launch {
      listener.highlightActivated(id)
    }
  }

  @android.webkit.JavascriptInterface
  fun highlightAnnotationMarkActivated(id: String) {
    uiScope.launch {
      listener.highlightAnnotationMarkActivated(id)
    }
  }


  fun Boolean.toInt() = if (this) 1 else 0

  fun scrollToStart() {
    this.evaluateJavascript("scrollToStart();", null)
  }

  fun scrollToEnd() {
    this.evaluateJavascript("scrollToEnd();", null)
  }

  fun scrollToPosition(progression: Double) {
    this.evaluateJavascript(
      "scrollToPosition(\"$progression\", \"${listener.publication.metadata.direction}\");",
      null
    )
  }

  fun setScrollMode(scrollMode: Boolean) {
    this.evaluateJavascript("setScrollMode($scrollMode)", null)
  }

  fun setProperty(key: String, value: String) {
    this.evaluateJavascript("setProperty(\"$key\", \"$value\");") {
      listener.onPageLoaded()
    }
  }

  fun removeProperty(key: String) {
    this.evaluateJavascript("removeProperty(\"$key\");", null)
  }

  fun getCurrentSelectionInfo(callback: (String) -> Unit) {
    this.evaluateJavascript("getCurrentSelectionInfo();") {
      callback(it)
    }
  }

  fun getCurrentSelectionRect(callback: (String) -> Unit) {
    this.evaluateJavascript("getSelectionRect();") {
      callback(it)
    }
  }

  fun createHighlight(locator: String?, color: String?, callback: (String) -> Unit) {
    uiScope.launch {
      this@R2BasicWebView.evaluateJavascript("createHighlight($locator, $color, true);") {
        callback(it)
      }
    }
  }

  fun destroyHighlight(id: String) {
    uiScope.launch {
      this@R2BasicWebView.evaluateJavascript("destroyHighlight(\"$id\");", null)
    }
  }

  fun createAnnotation(id: String) {
    uiScope.launch {
      this@R2BasicWebView.evaluateJavascript("createAnnotation(\"$id\");", null)
    }
  }

  fun rectangleForHighlightWithID(id: String, callback: (String) -> Unit) {
    uiScope.launch {
      this@R2BasicWebView.evaluateJavascript("rectangleForHighlightWithID(\"$id\");") {
        callback(it)
      }
    }
  }

  fun runJavaScript(javascript: String, callback: (String) -> Unit) {
    this.evaluateJavascript(javascript) { result ->
      callback(result)
    }
  }

}