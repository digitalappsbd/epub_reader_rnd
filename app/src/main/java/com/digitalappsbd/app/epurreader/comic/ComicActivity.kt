package com.digitalappsbd.app.epurreader.comic

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.digitalappsbd.app.epurreader.R
import com.digitalappsbd.app.epurreader.db.BooksDatabase
import com.digitalappsbd.app.epurreader.library.activitiesLaunched
import com.digitalappsbd.app.epurreader.outline.R2OutlineActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.NavigatorDelegate
import org.readium.r2.navigator.cbz.R2CbzActivity
import org.readium.r2.shared.Locations
import org.readium.r2.shared.Locator
import kotlin.coroutines.CoroutineContext

class ComicActivity : R2CbzActivity(), CoroutineScope, NavigatorDelegate {


  override val currentLocation: Locator?
    get() {
      return booksDB.books.currentLocator(bookId)?.let {
        it
      } ?: run {
        val resource = publication.images[resourcePager.currentItem]
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
  }

  /**
   * Context of this scope.
   */
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main

  private var menuToc: MenuItem? = null

  private lateinit var booksDB: BooksDatabase

  override fun onCreate(savedInstanceState: Bundle?) {
    if (activitiesLaunched.incrementAndGet() > 1) {
      finish(); }
    super.onCreate(savedInstanceState)

    booksDB = BooksDatabase(this)

    navigatorDelegate = this
    bookId = intent.getLongExtra("bookId", -1)

    currentPagerPosition = publication.images.indexOfFirst { it.href == currentLocation?.href }
    resourcePager.currentItem = currentPagerPosition

    toggleActionBar()
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_comic, menu)
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

  override fun onDestroy() {
    super.onDestroy()
    activitiesLaunched.getAndDecrement()
  }


}