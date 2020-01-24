package com.digitalappsbd.app.epurreader.comic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.digitalappsbd.app.epurreader.BuildConfig.DEBUG
import com.digitalappsbd.app.epurreader.R
import com.digitalappsbd.app.epurreader.library.activitiesLaunched
import com.digitalappsbd.app.epurreader.outline.R2OutlineActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.readium.r2.navigator.divina.R2DiViNaActivity
import org.readium.r2.shared.Locator
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


class DiViNaActivity : R2DiViNaActivity(), CoroutineScope {

  /**
   * Context of this scope.
   */
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main

  private var menuToc: MenuItem? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    if (activitiesLaunched.incrementAndGet() > 1) {
      finish(); }
    super.onCreate(savedInstanceState)

    bookId = intent.getLongExtra("bookId", -1)

    toggleActionBar()
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_divina, menu)
    menuToc = menu?.findItem(R.id.toc)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.toc -> {
        val intent = Intent(this, R2OutlineActivity::class.java)
        intent.putExtra("publication", publication)
        intent.putExtra("bookId", bookId)
        startActivityForResult(intent, 2)
        true
      }
      else -> false
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    data ?: return
    if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
      val locator = data.getSerializableExtra("locator") as Locator
      if (DEBUG) Timber.d("locator href ${locator.href}")

      // Call the player's goTo function with the considered href
      divinaWebView.evaluateJavascript("if (player) { player.goTo('${locator.href}'); };", null)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    activitiesLaunched.getAndDecrement()
  }


}