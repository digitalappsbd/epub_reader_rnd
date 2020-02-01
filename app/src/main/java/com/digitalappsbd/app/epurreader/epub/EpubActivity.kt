package com.digitalappsbd.app.epurreader.epub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.digitalappsbd.app.epurreader.BuildConfig.DEBUG
import com.digitalappsbd.app.epurreader.DRMManagementActivity
import com.digitalappsbd.app.epurreader.R
import com.digitalappsbd.app.epurreader.db.*
import com.digitalappsbd.app.epurreader.library.activitiesLaunched
import com.digitalappsbd.app.epurreader.outline.R2OutlineActivity
import com.digitalappsbd.app.epurreader.search.MarkJSSearchEngine
import com.digitalappsbd.app.epurreader.search.SearchLocator
import com.digitalappsbd.app.epurreader.search.SearchLocatorAdapter
import com.digitalappsbd.app.epurreader.settings.UserSettings
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_epub.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.anko.appcompat.v7.coroutines.onClose
import org.jetbrains.anko.indeterminateProgressDialog
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.toast
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.NavigatorDelegate
import org.readium.r2.navigator.epub.R2EpubActivity
import org.readium.r2.navigator.epub.Style
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.shared.*
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


class EpubActivity : R2EpubActivity(), CoroutineScope,
  NavigatorDelegate {

  override val currentLocation: Locator?
    get() {
      return booksDB.books.currentLocator(bookId)?.let {
        it
      } ?: run {
        val resource = publication.readingOrder[resourcePager.currentItem]
        val resourceHref = resource.href ?: ""
        val resourceType = resource.typeLink ?: ""
        Locator(
          resourceHref,
          resourceType,
          publication.metadata.title,
          Locations(progression = 0.0)
        )
      }
    }

  override fun locationDidChange(navigator: Navigator?, locator: Locator) {
    booksDB.books.saveProgression(locator, bookId)

    if (locator.locations?.progression == 0.toDouble()) {
      screenReader.currentUtterance = 0
    }
  }

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main

  private lateinit var userSettings: UserSettings
  private var isExploreByTouchEnabled = false
  private var pageEnded = false

  // Provide access to the Bookmarks & Positions Databases
  private lateinit var bookmarksDB: BookmarksDatabase
  private lateinit var booksDB: BooksDatabase
  private lateinit var positionsDB: PositionsDatabase
  private lateinit var highlightDB: HighligtsDatabase

  private lateinit var screenReader: R2ScreenReader

  private var menuDrm: MenuItem? = null
  private var menuToc: MenuItem? = null
  private var menuBmk: MenuItem? = null
  private var menuSearch: MenuItem? = null

  private var menuScreenReader: MenuItem? = null

  private var searchTerm = ""
  private lateinit var searchStorage: SharedPreferences
  private lateinit var searchResultAdapter: SearchLocatorAdapter
  private lateinit var searchResult: MutableList<SearchLocator>

  private var mode: ActionMode? = null
  private var popupWindow: PopupWindow? = null
  private lateinit var accesssibiltyManager: AccessibilityManager

  override fun onCreate(savedInstanceState: Bundle?) {
    if (activitiesLaunched.incrementAndGet() > 1) {
      finish()
    }
    super.onCreate(savedInstanceState)
    initDatabase()
    navigatorDelegate = this
    bookId = intent.getLongExtra("bookId", -1)

    Handler().postDelayed({
      launch {
        menuDrm?.isVisible = intent.getBooleanExtra("drm", false)
      }
    }, 100)

    val appearancePref = preferences.getInt(APPEARANCE_REF, 0)
    val backgroundsColors = mutableListOf("#ffffff", "#faf4e8", "#000000")
    val textColors = mutableListOf("#000000", "#000000", "#ffffff")
    resourcePager.setBackgroundColor(Color.parseColor(backgroundsColors[appearancePref]))
    (resourcePager.focusedChild?.findViewById(org.readium.r2.navigator.R.id.book_title) as? TextView)?.setTextColor(
      Color.parseColor(textColors[appearancePref])
    )
    toggleActionBar()

    resourcePager.offscreenPageLimit = 1

    currentPagerPosition =
      publication.readingOrder.indexOfFirst { it.href == currentLocation?.href }
    resourcePager.currentItem = currentPagerPosition

    titleView.text = publication.metadata.title
    initAudioBook()
    // SEARCH
    searchStorage = getSharedPreferences("org.readium.r2.search", Context.MODE_PRIVATE)
    searchResult = mutableListOf()
    searchResultAdapter = SearchLocatorAdapter(
      this,
      searchResult,
      object : SearchLocatorAdapter.RecyclerViewClickListener {
        override fun recyclerViewListClicked(v: View, position: Int) {

          search_overlay.visibility = View.INVISIBLE
          val searchView = menuSearch?.actionView as SearchView

          searchView.clearFocus()
          if (searchView.isShown) {
            menuSearch?.collapseActionView()
            resourcePager.offscreenPageLimit = 1
          }

          val locator = searchResult[position]
          val intent = Intent()
          intent.putExtra("publicationPath", publicationPath)
          intent.putExtra("epubName", publicationFileName)
          intent.putExtra("publication", publication)
          intent.putExtra("bookId", bookId)
          intent.putExtra("locator", locator)
          onActivityResult(2, Activity.RESULT_OK, intent)
        }

      })
    search_listView.adapter = searchResultAdapter
    search_listView.layoutManager = LinearLayoutManager(this)

    accesssibiltyManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager

    initBottomNavSettings()

  }

  private fun initBottomNavSettings() {
    val anchorView = this.findViewById(R.id.bottom_nav_settings) as BottomNavigationView
    bottom_nav_settings.setOnNavigationItemSelectedListener {
      when (it.itemId) {
        R.id.appearance -> {
          userSettings.userAppearancePopUp()
            .showAtLocation(
              anchorView,
              Gravity.BOTTOM,
              ListPopupWindow.MATCH_PARENT,
              anchorView.height
            )
          return@setOnNavigationItemSelectedListener true
        }
        R.id.font_size -> {
          userSettings.fontSettingsPopUp()
            .showAtLocation(
              anchorView,
              Gravity.BOTTOM,
              ListPopupWindow.MATCH_PARENT,
              anchorView.height
            )
          return@setOnNavigationItemSelectedListener true
        }
        R.id.font_change -> {
          userSettings.fontChangePopUp()
            .showAtLocation(
              anchorView,
              Gravity.BOTTOM,
              ListPopupWindow.MATCH_PARENT,
              anchorView.height
            )
          return@setOnNavigationItemSelectedListener true
        }
        R.id.brightness -> {
          userSettings.brightnessSettingsPopUp()
            .showAtLocation(
              anchorView,
              Gravity.BOTTOM,
              ListPopupWindow.MATCH_PARENT,
              anchorView.height
            )
          return@setOnNavigationItemSelectedListener true
        }
        else -> return@setOnNavigationItemSelectedListener true
      }
    }

  }

  private fun initDatabase() {
    bookmarksDB = BookmarksDatabase(this)
    booksDB = BooksDatabase(this)
    positionsDB = PositionsDatabase(this)
    highlightDB = HighligtsDatabase(this)

  }

  private fun initAudioBook() {
    play_pause.setOnClickListener {
      if (screenReader.isPaused) {
        screenReader.resumeReading()
        play_pause.setImageResource(android.R.drawable.ic_media_pause)
      } else {
        screenReader.pauseReading()
        play_pause.setImageResource(android.R.drawable.ic_media_play)
      }
    }
    fast_forward.setOnClickListener {
      if (screenReader.nextSentence()) {
        play_pause.setImageResource(android.R.drawable.ic_media_pause)
      } else {
        next_chapter.callOnClick()
      }
    }
    next_chapter.setOnClickListener {
      if (goForward(false, completion = {})) {
        screenReader.nextResource()
        play_pause.setImageResource(android.R.drawable.ic_media_pause)
      }
    }
    fast_back.setOnClickListener {
      if (screenReader.previousSentence()) {
        play_pause.setImageResource(android.R.drawable.ic_media_pause)
      } else {
        prev_chapter.callOnClick()
      }
    }
    prev_chapter.setOnClickListener {
      if (goBackward(false, completion = {})) {
        screenReader.previousResource()
        play_pause.setImageResource(android.R.drawable.ic_media_pause)
      }
    }

  }

  override fun onPause() {
    super.onPause()
    screenReader.pauseReading()
  }

  override fun onStop() {
    super.onStop()
    screenReader.stopReading()
  }


  fun updateScreenReaderSpeed(speed: Float, restart: Boolean) {
    var rSpeed = speed

    if (speed < 0.25) {
      rSpeed = 0.25.toFloat()
    } else if (speed > 3.0) {
      rSpeed = 3.0.toFloat()
    }
    screenReader.setSpeechSpeed(rSpeed, restart)
  }


  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_epub, menu)
    menuDrm = menu?.findItem(R.id.drm)
    menuToc = menu?.findItem(R.id.toc)
    menuBmk = menu?.findItem(R.id.bookmark)
    menuSearch = menu?.findItem(R.id.search)

    menuScreenReader = menu?.findItem(R.id.screen_reader)

    menuScreenReader?.isVisible = !isExploreByTouchEnabled

    menuDrm?.isVisible = false

    val searchView = menuSearch?.actionView as SearchView

    searchView.isFocusable = false
    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

      override fun onQueryTextSubmit(query: String?): Boolean {

        searchResult.clear()
        searchResultAdapter.notifyDataSetChanged()

        query?.let {
          search_overlay.visibility = View.VISIBLE
          resourcePager.offscreenPageLimit = publication.readingOrder.size

          //Saving searched term
          searchTerm = query
          //Initializing our custom search interfaces
          val progress =
            indeterminateProgressDialog(getString(R.string.progress_wait_while_searching_book))
          progress.show()

          val markJSSearchInterface = MarkJSSearchEngine(this@EpubActivity)
          Handler().postDelayed({
            markJSSearchInterface.search(query) { (last, result) ->
              searchResult.clear()
              searchResult.addAll(result)
              searchResultAdapter.notifyDataSetChanged()

              //Saving results + keyword only when JS is fully executed on all resources
              val editor = searchStorage.edit()
              val stringResults = Gson().toJson(result)
              editor.putString("result", stringResults)
              editor.putString("term", searchTerm)
              editor.putLong("book", bookId)
              editor.apply()

              if (last) {
                progress.dismiss()
              }
            }
          }, 500)

        }
        return false
      }

      override fun onQueryTextChange(s: String): Boolean {
        return false
      }
    })
    searchView.setOnQueryTextFocusChangeListener { view, b ->
      if (!b) {
        search_overlay.visibility = View.INVISIBLE
      } else {
        search_overlay.visibility = View.VISIBLE
        resourcePager.offscreenPageLimit = publication.readingOrder.size
      }
    }
    searchView.onClose {
      search_overlay.visibility = View.INVISIBLE

    }
    searchView.setOnCloseListener {
      if (searchView.isShown) {
        menuSearch?.collapseActionView()
      }
      search_overlay.visibility = View.INVISIBLE

      true
    }
    searchView.setOnSearchClickListener {
      val previouslySearchBook = searchStorage.getLong("book", -1)
      if (previouslySearchBook == bookId) {
        //Loading previous results + keyword
        val tmp = searchStorage.getString("result", null)
        if (tmp != null) {
          searchResult.clear()
          searchResult.addAll(
            Gson().fromJson(
              tmp,
              Array<SearchLocator>::class.java
            ).asList().toMutableList()
          )
          searchResultAdapter.notifyDataSetChanged()

          val keyword = searchStorage.getString("term", null)
          searchView.setQuery(keyword, false)
          searchView.clearFocus()
        }
        searchView.setQuery(searchStorage.getString("term", null), false)
      }

      search_overlay.visibility = View.VISIBLE
      resourcePager.offscreenPageLimit = publication.readingOrder.size
    }

    menuSearch?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
      override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
        search_overlay.visibility = View.VISIBLE
        resourcePager.offscreenPageLimit = publication.readingOrder.size
        return true
      }

      override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
        search_overlay.visibility = View.INVISIBLE
        return true
      }
    })

    val closeButton = searchView.findViewById(R.id.search_close_btn) as ImageView
    closeButton.setOnClickListener {
      searchResult.clear()
      searchResultAdapter.notifyDataSetChanged()

      searchView.setQuery("", false)

      (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(
        InputMethodManager.SHOW_FORCED,
        InputMethodManager.HIDE_IMPLICIT_ONLY
      )

      val editor = searchStorage.edit()
      editor.remove("result")
      editor.remove("term")
      editor.apply()
    }

    return true
  }


  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {

      R.id.toc -> {
        val intent = Intent(this, R2OutlineActivity::class.java)
        intent.putExtra("publication", publication)
        intent.putExtra("bookId", bookId)
        startActivityForResult(intent, 2)
        return true
      }
      R.id.settings -> {
        userSettings.userSettingsPopUp()
          .showAsDropDown(this.findViewById(R.id.settings), 0, 0, Gravity.END)
        return true
      }
      R.id.screen_reader -> {
        if (!screenReader.isSpeaking && !screenReader.isPaused && item.title == resources.getString(
            R.string.epubactivity_read_aloud_start
          )
        ) {

          //Get user settings speed when opening the screen reader. Get a neutral percentage (corresponding to
          //the normal speech speed) if no user settings exist.
          val speed = preferences.getInt(
            "reader_TTS_speed",
            (2.75 * 3.toDouble() / 11.toDouble() * 100).toInt()
          )
          //Convert percentage to a float value between 0.25 and 3.0
          val ttsSpeed = 0.25.toFloat() + (speed.toFloat() / 100.toFloat()) * 2.75.toFloat()

          updateScreenReaderSpeed(ttsSpeed, false)

          if (screenReader.goTo(resourcePager.currentItem)) {

            item.title = resources.getString(R.string.epubactivity_read_aloud_stop)
            tts_overlay.visibility = View.VISIBLE
            play_pause.setImageResource(android.R.drawable.ic_media_pause)
            allowToggleActionBar = false
          } else {
            Toast.makeText(
              applicationContext,
              "No further chapter contains text to read",
              Toast.LENGTH_LONG
            ).show()
          }

        } else {
          dismissScreenReader()
        }

        return true
      }
      R.id.drm -> {
        if (screenReader.isSpeaking) {
          dismissScreenReader()
        }
        startActivityForResult(
          intentFor<DRMManagementActivity>("publication" to publicationPath),
          1
        )
        return true
      }
      R.id.bookmark -> {
        val resourceIndex = resourcePager.currentItem.toLong()
        val resource = publication.readingOrder[resourcePager.currentItem]
        val resourceHref = resource.href ?: ""
        val resourceType = resource.typeLink ?: ""
        val resourceTitle = resource.title ?: ""
        val currentPage = positionsDB.positions.getCurrentPage(
          bookId,
          resourceHref,
          currentLocation?.locations?.progression!!
        )?.let {
          it
        }

        val bookmark = Bookmark(
          bookId,
          publicationIdentifier,
          resourceIndex,
          resourceHref,
          resourceType,
          resourceTitle,
          Locations(progression = currentLocation?.locations?.progression, position = currentPage),
          LocatorText()
        )

        bookmarksDB.bookmarks.insert(bookmark)?.let {
          launch {
            currentPage?.let {
              toast("Bookmark added at page $currentPage")
            } ?: run {
              toast("Bookmark added")
            }
          }
        } ?: run {
          launch {
            toast("Bookmark already exists")
          }
        }

        return true
      }
      R.id.search -> {
        search_overlay.visibility = View.VISIBLE
        resourcePager.offscreenPageLimit = publication.readingOrder.size

        val searchView = menuSearch?.actionView as SearchView

        searchView.clearFocus()

        return super.onOptionsItemSelected(item)
      }

      android.R.id.home -> {
        search_overlay.visibility = View.INVISIBLE
        resourcePager.offscreenPageLimit = 1
        val searchView = menuSearch?.actionView as SearchView
        searchView.clearFocus()
        return true
      }

      else -> return super.onOptionsItemSelected(item)
    }

  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
      if (data != null && data.getBooleanExtra("returned", false)) {
        finish()
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data)
      if (requestCode == 2 && resultCode == Activity.RESULT_OK && data != null) {
        val locator = data.getSerializableExtra("locator") as Locator
        locator.locations?.fragment?.let { fragment ->

          // TODO handle fragment anchors (id=) instead of catching the json exception
          try {
            val fragments = JSONArray(fragment).getString(0).split(",").associate {
              val (left, right) = it.split("=")
              left to right.toInt()
            }

            val index = fragments.getValue("i").toInt()
            val searchStorage = getSharedPreferences("org.readium.r2.search", Context.MODE_PRIVATE)
            Handler().postDelayed({
              if (publication.metadata.rendition.layout == RenditionLayout.Reflowable) {
                val currentFragment =
                  (resourcePager.adapter as R2PagerAdapter).getCurrentFragment() as R2EpubPageFragment
                val resource = publication.readingOrder[resourcePager.currentItem]
                val resourceHref = resource.href ?: ""
                val resourceType = resource.typeLink ?: ""
                val resourceTitle = resource.title ?: ""

                currentFragment.webView.runJavaScript(
                  "markSearch('${searchStorage.getString(
                    "term",
                    null
                  )}', null, '$resourceHref', '$resourceType', '$resourceTitle', '$index')"
                ) { result ->

                  if (DEBUG) Timber.d("###### $result")

                }
              }
            }, 1200)
          } catch (e: Exception) {
          }
        }
      }
    }
  }

  override fun onActionModeStarted(mode: ActionMode?) {
    super.onActionModeStarted(mode)
    mode?.menu?.run {
      menuInflater.inflate(R.menu.menu_action_mode, this)
      findItem(R.id.highlight).setOnMenuItemClickListener {
        val currentFragment = ((resourcePager.adapter as R2PagerAdapter).mFragments.get(
          (resourcePager.adapter as R2PagerAdapter).getItemId(resourcePager.currentItem)
        )) as? R2EpubPageFragment

        currentFragment?.webView?.getCurrentSelectionRect {
          val rect = JSONObject(it).run {
            try {
              val display = windowManager.defaultDisplay
              val metrics = DisplayMetrics()
              display.getMetrics(metrics)
              val left = getDouble("left")
              val width = getDouble("width")
              val top = getDouble("top") * metrics.density
              val height = getDouble("height") * metrics.density
              Rect(
                left.toInt(),
                top.toInt(),
                width.toInt() + left.toInt(),
                top.toInt() + height.toInt()
              )
            } catch (e: JSONException) {
              null
            }
          }
          showHighlightPopup(size = rect) {
            mode.finish()
          }
        }
        true
      }
      findItem(R.id.note).setOnMenuItemClickListener {
        showAnnotationPopup()
        true
      }
    }
    this.mode = mode
  }

  private fun showHighlightPopup(
    highlightID: String? = null,
    size: Rect?,
    dismissCallback: () -> Unit
  ) {
    popupWindow?.let {
      if (it.isShowing) {
        return
      }
    }
    var highlight: org.readium.r2.navigator.epub.Highlight? = null

    highlightID?.let { id ->
      highlightDB.highlights.list(id).forEach {
        highlight = convertHighlight2NavigationHighlight(it)
      }
    }

    val display = windowManager.defaultDisplay
    val rect = size ?: Rect()

    val mDisplaySize = Point()
    display.getSize(mDisplaySize)

    val popupView = layoutInflater.inflate(
      if (rect.top > rect.height()) R.layout.view_action_mode_reverse else R.layout.view_action_mode,
      null,
      false
    )
    popupView.measure(
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    popupWindow = PopupWindow(
      popupView,
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
    popupWindow?.isFocusable = true

    val x = rect.left
    val y = if (rect.top > rect.height()) rect.top - rect.height() - 80 else rect.bottom

    popupWindow?.showAtLocation(popupView, Gravity.NO_GRAVITY, x, y)

    popupView.run {
      findViewById<View>(R.id.notch).run {
        setX((rect.left * 2).toFloat())
      }
      findViewById<View>(R.id.red).setOnClickListener {
        changeHighlightColor(highlight, Color.rgb(247, 124, 124))
      }
      findViewById<View>(R.id.green).setOnClickListener {
        changeHighlightColor(highlight, Color.rgb(173, 247, 123))
      }
      findViewById<View>(R.id.blue).setOnClickListener {
        changeHighlightColor(highlight, Color.rgb(124, 198, 247))
      }
      findViewById<View>(R.id.yellow).setOnClickListener {
        changeHighlightColor(highlight, Color.rgb(249, 239, 125))
      }
      findViewById<View>(R.id.purple).setOnClickListener {
        changeHighlightColor(highlight, Color.rgb(182, 153, 255))
      }
      findViewById<View>(R.id.annotation).setOnClickListener {
        showAnnotationPopup(highlight)
        popupWindow?.dismiss()
        mode?.finish()
      }
      findViewById<View>(R.id.del).run {
        visibility = if (highlight != null) View.VISIBLE else View.GONE
        setOnClickListener {
          deleteHighlight(highlight)
        }
      }
    }

  }

  private fun changeHighlightColor(
    highlight: org.readium.r2.navigator.epub.Highlight? = null,
    color: Int
  ) {
    if (highlight != null) {
      val navigatorHighlight = org.readium.r2.navigator.epub.Highlight(
        highlight.id,
        highlight.locator,
        color,
        highlight.style,
        highlight.annotationMarkStyle
      )
      showHighlight(navigatorHighlight)
      addHighlight(navigatorHighlight)
    } else {
      createHighlight(color) {
        addHighlight(it)
      }
    }
    popupWindow?.dismiss()
    mode?.finish()
  }

  private fun addHighlight(highlight: org.readium.r2.navigator.epub.Highlight) {
    val annotation = highlightDB.highlights.list(highlight.id).run {
      if (isNotEmpty()) first().annotation
      else ""
    }

    highlightDB.highlights.insert(
      convertNavigationHighlight2Highlight(
        highlight,
        annotation,
        highlight.annotationMarkStyle
      )
    )
  }

  private fun deleteHighlight(highlight: org.readium.r2.navigator.epub.Highlight?) {
    highlight?.let {
      highlightDB.highlights.delete(it.id)
      hideHighlightWithID(it.id)
      popupWindow?.dismiss()
      mode?.finish()
    }
  }

  private fun addAnnotation(
    highlight: org.readium.r2.navigator.epub.Highlight,
    annotation: String
  ) {
    highlightDB.highlights.insert(
      convertNavigationHighlight2Highlight(highlight, annotation, "annotation")
    )
  }

  private fun drawHighlight() {
    val resource = publication.readingOrder[resourcePager.currentItem]
    highlightDB.highlights.listAll(bookId, resource.href!!).forEach {
      val highlight = convertHighlight2NavigationHighlight(it)
      showHighlight(highlight)
    }
  }

  private fun showAnnotationPopup(highlight: org.readium.r2.navigator.epub.Highlight? = null) {
    val view = layoutInflater.inflate(R.layout.popup_note, null, false)
    val alert = AlertDialog.Builder(this)
      .setView(view)
      .create()

    val annotation = highlight?.run {
      highlightDB.highlights.list(id).first().run {
        if (annotation.isEmpty() and annotationMarkStyle.isEmpty()) ""
        else annotation
      }
    }

    with(view) {
      val note = findViewById<EditText>(R.id.note)
      findViewById<TextView>(R.id.positive).setOnClickListener {
        if (note.text.isEmpty().not()) {
          createAnnotation(highlight) {
            addAnnotation(it, note.text.toString())
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
              note.applicationWindowToken,
              InputMethodManager.HIDE_NOT_ALWAYS
            )
          }
        }
        alert.dismiss()
        mode?.finish()
        popupWindow?.dismiss()
      }
      findViewById<TextView>(R.id.negative).setOnClickListener {
        alert.dismiss()
        mode?.finish()
        popupWindow?.dismiss()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
          note.applicationWindowToken,
          InputMethodManager.HIDE_NOT_ALWAYS
        )
      }
      if (highlight != null) {
        findViewById<TextView>(R.id.select_text).text = highlight.locator.text?.highlight
        note.setText(annotation)
      } else {
        currentSelection {
          findViewById<TextView>(R.id.select_text).text = it?.text?.highlight
        }
      }
    }
    alert.show()
  }

  override fun onPageLoaded() {
    super.onPageLoaded()
    drawHighlight()
  }

  override fun highlightActivated(id: String) {
    rectangleForHighlightWithID(id) {
      showHighlightPopup(id, it) {
      }
    }
  }

  override fun highlightAnnotationMarkActivated(id: String) {
    val highlight = highlightDB.highlights.list(id.replace("ANNOTATION", "HIGHLIGHT")).first()
    showAnnotationPopup(convertHighlight2NavigationHighlight(highlight))
  }

  private fun convertNavigationHighlight2Highlight(
    highlight: org.readium.r2.navigator.epub.Highlight,
    annotation: String? = null,
    annotationMarkStyle: String? = null
  ): Highlight {
    val resourceIndex = resourcePager.currentItem.toLong()
    val resource = publication.readingOrder[resourcePager.currentItem]
    val resourceHref = resource.href ?: ""
    val resourceType = resource.typeLink ?: ""
    val resourceTitle = resource.title ?: ""
    val currentPage = positionsDB.positions.getCurrentPage(
      bookId,
      resourceHref,
      currentLocation?.locations?.progression!!
    )?.let {
      it
    }

    val highlightLocations = highlight.locator.locations?.apply {
      progression = currentLocation?.locations?.progression
      position = currentPage
    } ?: Locations()
    val locationText = highlight.locator.text ?: LocatorText()

    return Highlight(
      highlight.id,
      publicationIdentifier,
      "style",
      highlight.color,
      annotation ?: "",
      annotationMarkStyle ?: "",
      resourceIndex,
      resourceHref,
      resourceType,
      resourceTitle,
      highlightLocations,
      locationText,
      bookID = bookId
    )
  }

  private fun convertHighlight2NavigationHighlight(highlight: Highlight) =
    org.readium.r2.navigator.epub.Highlight(
      highlight.highlightID,
      Locator(
        highlight.resourceHref,
        highlight.resourceType,
        locations = highlight.locations,
        text = highlight.locatorText
      ),
      highlight.color,
      Style.highlight,
      highlight.annotationMarkStyle
    )

  override fun onResume() {
    super.onResume()
    updateSettings()
    if (this::screenReader.isInitialized) {
      if (tts_overlay.visibility == View.VISIBLE) {
        if (screenReader.currentResource != resourcePager.currentItem) {
          screenReader.goTo(resourcePager.currentItem)
        }

        if (screenReader.isPaused) {
          screenReader.resumeReading()
          play_pause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
          screenReader.pauseReading()
          play_pause.setImageResource(android.R.drawable.ic_media_play)
        }
        screenReader.onResume()
      }
    } else {
      Handler().postDelayed({
        val port =
          preferences.getString("$publicationIdentifier-publicationPort", 0.toString())?.toInt()
        port?.let {
          screenReader = R2ScreenReader(
            this,
            this,
            this,
            publication,
            port,
            publicationFileName,
            resourcePager.currentItem
          )
        }
      }, 500)
    }
  }

  private fun updateSettings() {
    isExploreByTouchEnabled = accesssibiltyManager.isTouchExplorationEnabled

    if (isExploreByTouchEnabled) {
      publication.userSettingsUIPreset[ReadiumCSSName.ref(SCROLL_REF)] = true
      preferences.edit().putBoolean(SCROLL_REF, true).apply()

      userSettings = UserSettings(
        preferences,
        this,
        publication.userSettingsUIPreset
      )
      userSettings.saveChanges()

      Handler().postDelayed({
        userSettings.resourcePager = resourcePager
        userSettings.updateViewCSS(SCROLL_REF)
      }, 500)
    } else {
      if (publication.cssStyle != ContentLayoutStyle.cjkv.name) {
        publication.userSettingsUIPreset.remove(ReadiumCSSName.ref(SCROLL_REF))
      }

      userSettings = UserSettings(
        preferences,
        this,
        publication.userSettingsUIPreset
      )
      userSettings.resourcePager = resourcePager
    }

  }


  override fun toggleActionBar() {
    val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
    isExploreByTouchEnabled = am.isTouchExplorationEnabled

    if (!isExploreByTouchEnabled && tts_overlay.visibility == View.INVISIBLE) {
      super.toggleActionBar()
    }
    launch(coroutineContext) {
      mode?.finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    activitiesLaunched.getAndDecrement()
    try {
      screenReader.shutdown()
    } catch (e: Exception) {
    }
  }

  override fun onPageEnded(end: Boolean) {
    if (isExploreByTouchEnabled) {
      if (!pageEnded == end && end) {
        toast("End of chapter")
      }
      pageEnded = end
    }
  }

  override fun dismissScreenReader() {
    super.dismissScreenReader()
    screenReader.stopReading()
    menuScreenReader?.title = resources.getString(R.string.epubactivity_read_aloud_start)
    tts_overlay.visibility = View.INVISIBLE
    play_pause.setImageResource(android.R.drawable.ic_media_play)
    allowToggleActionBar = true
  }

  override fun playTextChanged(text: String) {
    super.playTextChanged(text)
    findViewById<TextView>(R.id.tts_textView)?.text = text
    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
      tts_textView!!,
      1,
      30,
      1,
      TypedValue.COMPLEX_UNIT_DIP
    )
  }

  override fun playStateChanged(playing: Boolean) {
    super.playStateChanged(playing)
    if (playing) {
      play_pause?.setImageResource(android.R.drawable.ic_media_pause)
    } else {
      play_pause?.setImageResource(android.R.drawable.ic_media_play)
    }
  }

}