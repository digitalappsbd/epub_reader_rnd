package com.folioreader.ui.activity

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.folioreader.Config
import com.folioreader.Constants.*
import com.folioreader.FolioReader
import com.folioreader.R
import com.folioreader.model.DisplayUnit
import com.folioreader.model.HighlightImpl
import com.folioreader.model.event.MediaOverlayPlayPauseEvent
import com.folioreader.model.locators.ReadLocator
import com.folioreader.model.locators.SearchLocator
import com.folioreader.ui.adapter.FolioPageFragmentAdapter
import com.folioreader.ui.adapter.SearchAdapter
import com.folioreader.ui.fragment.FolioPageFragment
import com.folioreader.ui.fragment.MediaControllerFragment
import com.folioreader.ui.view.DirectionalViewpager
import com.folioreader.ui.view.FolioAppBarLayout
import com.folioreader.ui.view.MediaControllerCallback
import com.folioreader.ui.view.settings.ConfigBottomSheetDialogFragment
import com.folioreader.ui.view.settings.ConfigDayNightBottomSheet
import com.folioreader.util.AppUtil
import com.folioreader.util.FileUtil
import com.folioreader.util.UiUtil
import kotlinx.android.synthetic.main.folio_activity.*
import org.greenrobot.eventbus.EventBus
import org.readium.r2.shared.Link
import org.readium.r2.shared.Publication
import org.readium.r2.streamer.parser.CbzParser
import org.readium.r2.streamer.parser.EpubParser
import org.readium.r2.streamer.parser.PubBox
import org.readium.r2.streamer.server.Server
import java.lang.ref.WeakReference
import kotlin.math.ceil

class FolioActivity : AppCompatActivity(), FolioActivityCallback, MediaControllerCallback,
  View.OnSystemUiVisibilityChangeListener {

  private var mFolioPageViewPager: DirectionalViewpager? = null
  private var actionBar: ActionBar? = null
  private var appBarLayout: FolioAppBarLayout? = null
  private var toolbar: Toolbar? = null
  private var distractionFreeMode: Boolean = false
  private var handler: Handler? = null

  private var currentChapterIndex: Int = 0
  private var mFolioPageFragmentAdapter: FolioPageFragmentAdapter? = null
  private var entryReadLocator: ReadLocator? = null
  private var lastReadLocator: ReadLocator? = null
  private var outState: Bundle? = null
  private var savedInstanceState: Bundle? = null

  private var r2StreamerServer: Server? = null
  private var pubBox: PubBox? = null
  private var spine: List<Link>? = null

  private var mBookId: String? = null
  private var bookFileName: String? = null
  private var mEpubFilePath: String? = null
  private var mEpubSourceType: EpubSourceType? = null
  private var mEpubRawId = 0
  private var mediaControllerFragment: MediaControllerFragment? = null
  private var direction: Config.Direction = Config.Direction.VERTICAL
  private var portNumber: Int = DEFAULT_PORT_NUMBER
  private var streamerUri: Uri? = null

  private var searchUri: Uri? = null
  private var searchAdapterDataBundle: Bundle? = null
  private var searchQuery: CharSequence? = null
  private var searchLocator: SearchLocator? = null

  private var displayMetrics: DisplayMetrics? = null
  private var density: Float = 0.toFloat()
  private var topActivity: Boolean? = null
  private var taskImportance: Int = 0

  /*Todo DayNight*/
  private lateinit var config: Config
  private var isNightMode = false

  companion object {

    @JvmField
    val LOG_TAG: String = FolioActivity::class.java.simpleName

    const val INTENT_EPUB_SOURCE_PATH = "com.folioreader.epub_asset_path"
    const val INTENT_EPUB_SOURCE_TYPE = "epub_source_type"
    const val EXTRA_READ_LOCATOR = "com.folioreader.extra.READ_LOCATOR"
    private const val BUNDLE_READ_LOCATOR_CONFIG_CHANGE = "BUNDLE_READ_LOCATOR_CONFIG_CHANGE"
    private const val BUNDLE_DISTRACTION_FREE_MODE = "BUNDLE_DISTRACTION_FREE_MODE"
    const val EXTRA_SEARCH_ITEM = "EXTRA_SEARCH_ITEM"
    const val ACTION_SEARCH_CLEAR = "ACTION_SEARCH_CLEAR"
    private const val HIGHLIGHT_ITEM = "highlight_item"
  }


  private val searchReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      Log.v(LOG_TAG, "-> searchReceiver -> onReceive -> " + intent.action!!)

      val action = intent.action ?: return
      when (action) {
        ACTION_SEARCH_CLEAR -> clearSearchLocator()
      }
    }
  }

  private val currentFragment: FolioPageFragment?
    get() = if (mFolioPageFragmentAdapter != null && mFolioPageViewPager != null) {
      mFolioPageFragmentAdapter?.getItem(mFolioPageViewPager!!.currentItem) as FolioPageFragment
    } else {
      null
    }

  enum class EpubSourceType { RAW, ASSETS, SD_CARD }
  private enum class RequestCode(internal val value: Int) { CONTENT_HIGHLIGHT(77), SEARCH(101) }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    Log.v(LOG_TAG, "-> onNewIntent")

    val action = getIntent().action
    if (action != null && action == FolioReader.ACTION_CLOSE_FOLIOREADER) {

      if (topActivity == null || topActivity == false) {
        finish()
        var appInBackground = false
        if (Build.VERSION.SDK_INT < 26) {
          if (ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND == taskImportance)
            appInBackground = true
        } else {
          if (ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED == taskImportance)
            appInBackground = true
        }
        if (appInBackground)
          moveTaskToBack(true)
      }
    }
  }


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    handler = Handler()
    configureDisplay()
    setConfig(savedInstanceState)
    initDistractionFreeMode(savedInstanceState)

    setContentView(R.layout.folio_activity)

    this.savedInstanceState = savedInstanceState
    if (savedInstanceState != null) {
      searchAdapterDataBundle = savedInstanceState.getBundle(SearchAdapter.DATA_BUNDLE)
      searchQuery = savedInstanceState.getCharSequence(SearchActivity.BUNDLE_SAVE_SEARCH_QUERY)
    }

    getDataFromExtras()
    initActionBar()
    initMediaController()
    setupBookAfterPermission()
    initBottomNav()

  }


  override fun onResume() {
    super.onResume()
    topActivity = true
    val action = intent.action
    if (action != null && action == FolioReader.ACTION_CLOSE_FOLIOREADER) {
      finish()
    }
  }

  override fun pause() {
    EventBus.getDefault().post(
      MediaOverlayPlayPauseEvent(
        spine!![currentChapterIndex].href, false, false
      )
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    if (outState != null)
      outState!!.putSerializable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE, lastReadLocator)

    val localBroadcastManager = LocalBroadcastManager.getInstance(this)
    localBroadcastManager.unregisterReceiver(searchReceiver)
    localBroadcastManager.unregisterReceiver(closeBroadcastReceiver)

    if (r2StreamerServer != null)
      r2StreamerServer!!.stop()

    if (isFinishing) {
      localBroadcastManager.sendBroadcast(Intent(FolioReader.ACTION_FOLIOREADER_CLOSED))
      FolioReader.get().retrofit = null
      FolioReader.get().r2StreamerApi = null
    }
  }

  override fun onStop() {
    super.onStop()
    topActivity = false
  }

  private fun initBottomNav() {
    navigation.setOnNavigationItemSelectedListener {
      if (it.itemId == R.id.action_day_night) {
        ConfigDayNightBottomSheet().show(
          supportFragmentManager,
          ConfigDayNightBottomSheet.LOG_TAG
        )
        return@setOnNavigationItemSelectedListener true
      } else return@setOnNavigationItemSelectedListener true
    }
  }

  override fun setDayMode() {
    actionBar?.setBackgroundDrawable(
      ColorDrawable(ContextCompat.getColor(this, R.color.white))
    )
    toolbar?.setTitleTextColor(ContextCompat.getColor(this, R.color.black))
  }

  override fun setNightMode() {

    actionBar?.setBackgroundDrawable(
      ColorDrawable(ContextCompat.getColor(this, R.color.black))
    )
    toolbar?.setTitleTextColor(ContextCompat.getColor(this, R.color.night_title_text_color))
  }

  private fun configureDisplay() {
    val display = windowManager.defaultDisplay
    displayMetrics = resources.displayMetrics
    display.getRealMetrics(displayMetrics)
    density = displayMetrics?.density ?: 0F
    LocalBroadcastManager.getInstance(this).registerReceiver(
      closeBroadcastReceiver,
      IntentFilter(FolioReader.ACTION_CLOSE_FOLIOREADER)
    )
  }

  private fun getDataFromExtras() {
    intent?.let {
      mBookId = it.getStringExtra(FolioReader.EXTRA_BOOK_ID)
      it.extras?.let { bundle ->
        mEpubSourceType = bundle.getSerializable(INTENT_EPUB_SOURCE_TYPE) as EpubSourceType
        if (mEpubSourceType == EpubSourceType.RAW) {
          mEpubRawId = bundle.getInt(INTENT_EPUB_SOURCE_PATH)
        } else {
          mEpubFilePath = bundle.getString(INTENT_EPUB_SOURCE_PATH)
        }
      }

    }

  }

  private fun setupBookAfterPermission() {
    if (ContextCompat.checkSelfPermission(
        this@FolioActivity,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this@FolioActivity,
        getWriteExternalStoragePerms(),
        WRITE_EXTERNAL_STORAGE_REQUEST
      )
    } else {
      setupBook()
    }
  }

  private fun initActionBar() {

    appBarLayout = findViewById(R.id.appBarLayout)
    toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    actionBar = supportActionBar

    val config = AppUtil.getSavedConfig(applicationContext)!!

    val drawable = ContextCompat.getDrawable(this, R.drawable.ic_drawer)
    UiUtil.setColorIntToDrawable(config.themeColor, drawable!!)
    toolbar!!.navigationIcon = drawable

    if (config.isNightMode) {
      setNightMode()
    } else {
      setDayMode()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val color: Int
      color = if (config.isNightMode) {
        ContextCompat.getColor(this, R.color.black)
      } else {
        val attrs = intArrayOf(android.R.attr.navigationBarColor)
        val typedArray = theme.obtainStyledAttributes(attrs)
        typedArray.getColor(0, ContextCompat.getColor(this, R.color.white))
      }
      window.navigationBarColor = color
    }

    appBarLayout?.setTopMargin(statusBarHeight)
  }

  private val statusBarHeight: Int
    get() {
      var result = 0
      val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
      if (resourceId > 0)
        result = resources.getDimensionPixelSize(resourceId)
      return result
    }


  private fun initMediaController() {
    mediaControllerFragment = MediaControllerFragment.getInstance(supportFragmentManager, this)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)

    val config = AppUtil.getSavedConfig(applicationContext)!!
    UiUtil.setColorIntToDrawable(config.themeColor, menu.findItem(R.id.itemSearch).icon)
    UiUtil.setColorIntToDrawable(config.themeColor, menu.findItem(R.id.itemConfig).icon)
    UiUtil.setColorIntToDrawable(config.themeColor, menu.findItem(R.id.itemTts).icon)

    if (!config.isShowTts)
      menu.findItem(R.id.itemTts).isVisible = false

    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {

    when (item.itemId) {
      android.R.id.home -> {
        startContentHighlightActivity()
        return true

      }
      R.id.itemSearch -> {
        if (searchUri == null)
          return true
        val intent = Intent(this, SearchActivity::class.java)
        intent.putExtra(SearchActivity.BUNDLE_SPINE_SIZE, spine?.size ?: 0)
        intent.putExtra(SearchActivity.BUNDLE_SEARCH_URI, searchUri)
        intent.putExtra(SearchAdapter.DATA_BUNDLE, searchAdapterDataBundle)
        intent.putExtra(SearchActivity.BUNDLE_SAVE_SEARCH_QUERY, searchQuery)
        startActivityForResult(intent, RequestCode.SEARCH.value)
        return true

      }
      R.id.itemConfig -> {
        showConfigBottomSheetDialogFragment()
        return true

      }
      R.id.itemTts -> {
        showMediaController()
        return true
      }
      else -> return super.onOptionsItemSelected(item)
    }

  }

  private fun startContentHighlightActivity() {

    val intent = Intent(this@FolioActivity, ContentHighlightActivity::class.java)

    intent.putExtra(PUBLICATION, pubBox!!.publication)
    try {
      intent.putExtra(CHAPTER_SELECTED, spine!![currentChapterIndex].href)
    } catch (e: NullPointerException) {
      Log.w(LOG_TAG, "-> ", e)
      intent.putExtra(CHAPTER_SELECTED, "")
    } catch (e: IndexOutOfBoundsException) {
      Log.w(LOG_TAG, "-> ", e)
      intent.putExtra(CHAPTER_SELECTED, "")
    }

    intent.putExtra(FolioReader.EXTRA_BOOK_ID, mBookId)
    intent.putExtra(BOOK_TITLE, bookFileName)

    startActivityForResult(intent, RequestCode.CONTENT_HIGHLIGHT.value)
    overridePendingTransition(R.anim.slide_in_up, R.anim.slide_out_up)
  }

  private fun showConfigBottomSheetDialogFragment() {
    ConfigBottomSheetDialogFragment().show(
      supportFragmentManager,
      ConfigBottomSheetDialogFragment.LOG_TAG
    )
  }

  private fun showMediaController() {
    mediaControllerFragment?.show(supportFragmentManager)
  }

  private fun setupBook() {
    try {
      initBook()
      onBookInitSuccess()
    } catch (e: Exception) {
      onBookInitFailure()
    }

  }

  @Throws(Exception::class)
  private fun initBook() {
    bookFileName = FileUtil.getEpubFilename(this, mEpubSourceType!!, mEpubFilePath, mEpubRawId)
    val path = FileUtil.saveEpubFileAndLoadLazyBook(
      this, mEpubSourceType, mEpubFilePath,
      mEpubRawId, bookFileName
    )
    val extension: Publication.EXTENSION
    var extensionString: String? = null
    try {
      extensionString = FileUtil.getExtensionUppercase(path)
      extension = Publication.EXTENSION.valueOf(extensionString)
    } catch (e: IllegalArgumentException) {
      throw Exception("-> Unknown book file extension `$extensionString`", e)
    }

    pubBox = when (extension) {
      Publication.EXTENSION.EPUB -> {
        val epubParser = EpubParser()
        epubParser.parse(path!!, "")
      }
      Publication.EXTENSION.CBZ -> {
        val cbzParser = CbzParser()
        cbzParser.parse(path!!, "")
      }
      else -> {
        null
      }
    }

    portNumber = intent.getIntExtra(FolioReader.EXTRA_PORT_NUMBER, DEFAULT_PORT_NUMBER)
    portNumber = AppUtil.getAvailablePortNumber(portNumber)

    r2StreamerServer = Server(portNumber)
    pubBox?.publication?.let {
      r2StreamerServer?.addEpub(
        it, pubBox!!.container,
        "/" + bookFileName!!, null
      )
    }

    r2StreamerServer?.start()

    FolioReader.initRetrofit(streamerUrl)
  }

  private fun onBookInitFailure() {
  }

  private fun onBookInitSuccess() {

    val publication = pubBox!!.publication
    spine = publication.readingOrder
    title = publication.metadata.title

    if (mBookId == null) {
      mBookId = if (publication.metadata.identifier.isNotEmpty()) {
        publication.metadata.identifier
      } else {
        if (publication.metadata.title.isNotEmpty()) {
          publication.metadata.title.hashCode().toString()
        } else {
          bookFileName!!.hashCode().toString()
        }
      }
    }
    for (link in publication.links) {
      if (link.rel.contains("search")) {
        searchUri = Uri.parse("http://" + link.href!!)
        break
      }
    }
    if (searchUri == null)
      searchUri = Uri.parse(streamerUrl + "search")

    configFolio()
  }

  override fun getStreamerUrl(): String {

    if (streamerUri == null) {
      streamerUri =
        Uri.parse(String.format(STREAMER_URL_TEMPLATE, LOCALHOST, portNumber, bookFileName))
    }
    return streamerUri.toString()
  }

  override fun onDirectionChange(newDirection: Config.Direction) {
    var folioPageFragment: FolioPageFragment? = currentFragment ?: return
    entryReadLocator = folioPageFragment!!.getLastReadLocator()
    val searchLocatorVisible = folioPageFragment.searchLocatorVisible

    direction = newDirection

    mFolioPageViewPager?.setDirection(newDirection)
    mFolioPageFragmentAdapter = FolioPageFragmentAdapter(
      supportFragmentManager,
      spine, bookFileName, mBookId
    )
    mFolioPageViewPager?.adapter = mFolioPageFragmentAdapter
    mFolioPageViewPager?.currentItem = currentChapterIndex

    folioPageFragment = currentFragment ?: return
    searchLocatorVisible?.let {
      folioPageFragment.highlightSearchLocator(searchLocatorVisible)
    }
  }

  private fun initDistractionFreeMode(savedInstanceState: Bundle?) {
    window.decorView.setOnSystemUiVisibilityChangeListener(this)
    hideSystemUI()
    showSystemUI()
    distractionFreeMode =
      savedInstanceState != null && savedInstanceState.getBoolean(BUNDLE_DISTRACTION_FREE_MODE)
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    if (distractionFreeMode) {
      handler!!.post { hideSystemUI() }
    }
  }

  override fun getTopDistraction(unit: DisplayUnit): Int {
    var topDistraction = 0
    if (!distractionFreeMode) {
      topDistraction = statusBarHeight
      if (actionBar != null)
        topDistraction += actionBar!!.height
    }

    return when (unit) {
      DisplayUnit.PX -> topDistraction

      DisplayUnit.DP -> {
        topDistraction /= density.toInt()
        topDistraction
      }
      else -> throw IllegalArgumentException("-> Illegal argument -> unit = $unit")
    }
  }

  override fun getBottomDistraction(unit: DisplayUnit): Int {

    var bottomDistraction = 0
    if (!distractionFreeMode)
      bottomDistraction = appBarLayout!!.navigationBarHeight

    return when (unit) {
      DisplayUnit.PX -> bottomDistraction

      DisplayUnit.DP -> {
        bottomDistraction /= density.toInt()
        bottomDistraction
      }

      else -> throw IllegalArgumentException("-> Illegal argument -> unit = $unit")
    }
  }

  private fun computeViewportRect(): Rect {
    val viewportRect = Rect(appBarLayout?.insets)
    if (distractionFreeMode)
      viewportRect.left = 0
    viewportRect.top = getTopDistraction(DisplayUnit.PX)
    if (distractionFreeMode) {
      viewportRect.right = displayMetrics?.widthPixels ?: 0
    } else {
      viewportRect.right = (displayMetrics?.widthPixels ?: 0) - viewportRect.right
    }
    viewportRect.bottom = (displayMetrics?.heightPixels ?: 0) - getBottomDistraction(DisplayUnit.PX)

    return viewportRect
  }

  override fun getViewportRect(unit: DisplayUnit): Rect {

    val viewportRect = computeViewportRect()
    when (unit) {
      DisplayUnit.PX -> return viewportRect

      DisplayUnit.DP -> {
        viewportRect.left /= density.toInt()
        viewportRect.top /= density.toInt()
        viewportRect.right /= density.toInt()
        viewportRect.bottom /= density.toInt()
        return viewportRect
      }

      DisplayUnit.CSS_PX -> {
        viewportRect.left = ceil((viewportRect.left / density).toDouble()).toInt()
        viewportRect.top = ceil((viewportRect.top / density).toDouble()).toInt()
        viewportRect.right = ceil((viewportRect.right / density).toDouble()).toInt()
        viewportRect.bottom = ceil((viewportRect.bottom / density).toDouble()).toInt()
        return viewportRect
      }

      else -> throw IllegalArgumentException("-> Illegal argument -> unit = $unit")
    }
  }

  override fun getActivity(): WeakReference<FolioActivity> {
    return WeakReference(this)
  }

  override fun onSystemUiVisibilityChange(visibility: Int) {
    distractionFreeMode = visibility != View.SYSTEM_UI_FLAG_VISIBLE
    if (actionBar != null) {
      if (distractionFreeMode) {
        actionBar?.hide()
      } else {
        actionBar?.show()
      }
    }
  }

  override fun toggleSystemUI() {

    if (distractionFreeMode) {
      showSystemUI()
    } else {
      hideSystemUI()
    }
  }

  private fun showSystemUI() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    if (appBarLayout != null)
      appBarLayout!!.setTopMargin(statusBarHeight)
    onSystemUiVisibilityChange(View.SYSTEM_UI_FLAG_VISIBLE)
  }

  private fun hideSystemUI() {
    Log.v(LOG_TAG, "-> hideSystemUI")
    val decorView = window.decorView
    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_FULLSCREEN)
  }

  override fun getEntryReadLocator(): ReadLocator? {
    if (entryReadLocator != null) {
      val tempReadLocator = entryReadLocator
      entryReadLocator = null
      return tempReadLocator
    }
    return null
  }

  override fun goToChapter(href: String): Boolean {

    for (link in spine!!) {
      if (href.contains(link.href!!)) {
        currentChapterIndex = spine!!.indexOf(link)
        mFolioPageViewPager!!.currentItem = currentChapterIndex
        val folioPageFragment = currentFragment
        folioPageFragment!!.scrollToFirst()
        folioPageFragment.scrollToAnchorId(href)
        return true
      }
    }
    return false
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RequestCode.SEARCH.value) {
      if (resultCode == Activity.RESULT_CANCELED)
        return

      searchAdapterDataBundle = data!!.getBundleExtra(SearchAdapter.DATA_BUNDLE)
      searchQuery = data.getCharSequenceExtra(SearchActivity.BUNDLE_SAVE_SEARCH_QUERY)

      if (resultCode == SearchActivity.ResultCode.ITEM_SELECTED.value) {

        searchLocator = data.getParcelableExtra(EXTRA_SEARCH_ITEM)
        if (mFolioPageViewPager == null) return
        currentChapterIndex = getChapterIndex(HREF, searchLocator!!.href)
        mFolioPageViewPager!!.currentItem = currentChapterIndex
        val folioPageFragment = currentFragment ?: return
        folioPageFragment.highlightSearchLocator(searchLocator!!)
        searchLocator = null
      }

    } else if (requestCode == RequestCode.CONTENT_HIGHLIGHT.value && resultCode == Activity.RESULT_OK &&
      data!!.hasExtra(TYPE)
    ) {

      val type = data.getStringExtra(TYPE)

      if (type == CHAPTER_SELECTED) {
        goToChapter(data.getStringExtra(SELECTED_CHAPTER_POSITION))

      } else if (type == HIGHLIGHT_SELECTED) {
        val highlightImpl = data.getParcelableExtra<HighlightImpl>(HIGHLIGHT_ITEM)
        currentChapterIndex = highlightImpl.pageNumber
        mFolioPageViewPager!!.currentItem = currentChapterIndex
        val folioPageFragment = currentFragment ?: return
        folioPageFragment.scrollToHighlightId(highlightImpl.rangy)
      }
    }
  }


  override fun getCurrentChapterIndex(): Int {
    return currentChapterIndex
  }

  private fun configFolio() {

    mFolioPageViewPager = findViewById(R.id.folioPageViewPager)
    mFolioPageViewPager?.addOnPageChangeListener(object :
      DirectionalViewpager.OnPageChangeListener {
      override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
      ) {
      }

      override fun onPageSelected(position: Int) {
        EventBus.getDefault().post(
          MediaOverlayPlayPauseEvent(
            spine?.get(currentChapterIndex)?.href, false, true
          )
        )
        mediaControllerFragment?.setPlayButtonDrawable()
        currentChapterIndex = position
      }

      override fun onPageScrollStateChanged(state: Int) {

        if (state == DirectionalViewpager.SCROLL_STATE_IDLE) {
          val position = mFolioPageViewPager!!.currentItem
          Log.v(
            LOG_TAG, "-> onPageScrollStateChanged -> DirectionalViewpager -> " +
                "position = " + position
          )

          var folioPageFragment =
            mFolioPageFragmentAdapter!!.getItem(position - 1) as FolioPageFragment?
          if (folioPageFragment != null) {
            folioPageFragment.scrollToLast()
            if (folioPageFragment.mWebview != null)
              folioPageFragment.mWebview!!.dismissPopupWindow()
          }

          folioPageFragment =
            mFolioPageFragmentAdapter!!.getItem(position + 1) as FolioPageFragment?
          if (folioPageFragment != null) {
            folioPageFragment.scrollToFirst()
            if (folioPageFragment.mWebview != null)
              folioPageFragment.mWebview!!.dismissPopupWindow()
          }
        }
      }
    })

    mFolioPageViewPager!!.setDirection(direction)
    mFolioPageFragmentAdapter = FolioPageFragmentAdapter(
      supportFragmentManager,
      spine, bookFileName, mBookId
    )
    mFolioPageViewPager!!.adapter = mFolioPageFragmentAdapter

    if (searchLocator != null) {

      currentChapterIndex = getChapterIndex(HREF, searchLocator!!.href)
      mFolioPageViewPager!!.currentItem = currentChapterIndex
      val folioPageFragment = currentFragment ?: return
      folioPageFragment.highlightSearchLocator(searchLocator!!)
      searchLocator = null

    } else {

      val readLocator: ReadLocator?
      if (savedInstanceState == null) {
        readLocator = intent.getParcelableExtra(EXTRA_READ_LOCATOR)
        entryReadLocator = readLocator
      } else {
        readLocator = savedInstanceState!!.getParcelable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE)
        lastReadLocator = readLocator
      }
      currentChapterIndex = getChapterIndex(readLocator)
      mFolioPageViewPager!!.currentItem = currentChapterIndex
    }

    LocalBroadcastManager.getInstance(this).registerReceiver(
      searchReceiver,
      IntentFilter(ACTION_SEARCH_CLEAR)
    )
  }

  private fun getChapterIndex(readLocator: ReadLocator?): Int {

    if (readLocator == null) {
      return 0
    } else if (!TextUtils.isEmpty(readLocator.href)) {
      return getChapterIndex(HREF, readLocator.href)
    }

    return 0
  }

  private fun getChapterIndex(caseString: String, value: String): Int {
    for (i in spine!!.indices) {
      when (caseString) {
        HREF -> if (spine!![i].href == value)
          return i
      }
    }
    return 0
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    Log.v(LOG_TAG, "-> onSaveInstanceState")
    this.outState = outState

    outState.putBoolean(BUNDLE_DISTRACTION_FREE_MODE, distractionFreeMode)
    outState.putBundle(SearchAdapter.DATA_BUNDLE, searchAdapterDataBundle)
    outState.putCharSequence(SearchActivity.BUNDLE_SAVE_SEARCH_QUERY, searchQuery)
  }

  override fun storeLastReadLocator(lastReadLocator: ReadLocator) {
    Log.v(LOG_TAG, "-> storeLastReadLocator")
    this.lastReadLocator = lastReadLocator
  }

  private fun setConfig(savedInstanceState: Bundle?) {

    var config: Config?
    val intentConfig = intent.getParcelableExtra<Config>(Config.INTENT_CONFIG)
    val overrideConfig = intent.getBooleanExtra(Config.EXTRA_OVERRIDE_CONFIG, false)
    val savedConfig = AppUtil.getSavedConfig(this)

    config = if (savedInstanceState != null) {
      savedConfig

    } else if (savedConfig == null) {
      intentConfig ?: Config()

    } else {
      if (intentConfig != null && overrideConfig) {
        intentConfig
      } else {
        savedConfig
      }
    }

    if (config == null)
      config = Config()

    AppUtil.saveConfig(this, config)
    direction = config.direction
  }

  override fun play() {
    EventBus.getDefault().post(
      MediaOverlayPlayPauseEvent(
        spine!![currentChapterIndex].href, true, false
      )
    )
  }


  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    when (requestCode) {
      WRITE_EXTERNAL_STORAGE_REQUEST -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        setupBook()
      } else {
        Toast.makeText(this, getString(R.string.cannot_access_epub_message), Toast.LENGTH_LONG)
          .show()
        finish()
      }
    }
  }

  override fun getDirection(): Config.Direction {
    return direction
  }

  private fun clearSearchLocator() {
    Log.v(LOG_TAG, "-> clearSearchLocator")

    val fragments = mFolioPageFragmentAdapter!!.fragments
    for (i in fragments.indices) {
      val folioPageFragment = fragments[i] as FolioPageFragment?
      folioPageFragment?.clearSearchLocator()
    }

    val savedStateList = mFolioPageFragmentAdapter!!.savedStateList
    if (savedStateList != null) {
      for (i in savedStateList.indices) {
        val savedState = savedStateList[i]
        val bundle = FolioPageFragmentAdapter.getBundleFromSavedState(savedState)
        bundle?.putParcelable(FolioPageFragment.BUNDLE_SEARCH_LOCATOR, null)
      }
    }
  }

  private val closeBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      Log.v(LOG_TAG, "-> closeBroadcastReceiver -> onReceive -> " + intent.action!!)

      val action = intent.action
      if (action != null && action == FolioReader.ACTION_CLOSE_FOLIOREADER) {

        try {
          val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
          val tasks = activityManager.runningAppProcesses
          taskImportance = tasks[0].importance
        } catch (e: Exception) {
          Log.e(LOG_TAG, "-> ", e)
        }

        val closeIntent = Intent(applicationContext, FolioActivity::class.java)
        closeIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        closeIntent.action = FolioReader.ACTION_CLOSE_FOLIOREADER
        this@FolioActivity.startActivity(closeIntent)
      }
    }
  }
}