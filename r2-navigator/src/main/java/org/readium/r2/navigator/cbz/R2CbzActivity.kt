/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Mostapha Idoubihi
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator.cbz

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.readium.r2.navigator.*
import org.readium.r2.navigator.extensions.layoutDirectionIsRTL
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.navigator.pager.R2ViewPager
import org.readium.r2.shared.Link
import org.readium.r2.shared.Locations
import org.readium.r2.shared.Locator
import org.readium.r2.shared.Publication
import kotlin.coroutines.CoroutineContext


open class R2CbzActivity : AppCompatActivity(), CoroutineScope, IR2Activity, VisualNavigator {

  override fun go(locator: Locator, animated: Boolean, completion: () -> Unit): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun go(link: Link, animated: Boolean, completion: () -> Unit): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun goForward(animated: Boolean, completion: () -> Unit): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun goBackward(animated: Boolean, completion: () -> Unit): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override val readingProgression: ReadingProgression
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

  override fun goLeft(animated: Boolean, completion: () -> Unit): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun goRight(animated: Boolean, completion: () -> Unit): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  /**
   * Context of this scope.
   */
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main

  override lateinit var preferences: SharedPreferences
  override lateinit var resourcePager: R2ViewPager
  override lateinit var publicationPath: String
  override lateinit var publication: Publication
  override lateinit var publicationIdentifier: String
  override lateinit var publicationFileName: String
  override var bookId: Long = -1

  var resources = arrayListOf<String>()
  lateinit var adapter: R2PagerAdapter

  var currentPagerPosition: Int = 0
  protected var navigatorDelegate: NavigatorDelegate? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_r2_viewpager)

    preferences = getSharedPreferences("org.readium.r2.settings", Context.MODE_PRIVATE)
    resourcePager = findViewById(R.id.resourcePager)
    resourcePager.type = Publication.TYPE.CBZ

    publicationPath =
      intent.getStringExtra("publicationPath") ?: throw Exception("publicationPath required")
    publicationFileName = intent.getStringExtra("publicationFileName")
      ?: throw Exception("publicationFileName required")
    publication = intent.getSerializableExtra("publication") as Publication
    publicationIdentifier = publication.metadata.identifier!!
    title = publication.metadata.title

    for (link in publication.images) {
      resources.add(link.href.toString())
    }

    adapter = R2PagerAdapter(
      supportFragmentManager,
      resources,
      publication.metadata.title,
      Publication.TYPE.CBZ,
      publicationPath
    )

    resourcePager.adapter = adapter

    if (currentPagerPosition == 0) {
      if (layoutDirectionIsRTL()) {
        // The view has RTL layout
        resourcePager.currentItem = resources.size - 1
      } else {
        // The view has LTR layout
        resourcePager.currentItem = currentPagerPosition
      }
    } else {
      resourcePager.currentItem = currentPagerPosition
    }

  }

  override fun onPause() {
    super.onPause()
    val resource = publication.images[resourcePager.currentItem]
    val resourceHref = resource.href ?: ""
    val resourceType = resource.typeLink ?: ""

    navigatorDelegate?.locationDidChange(
      locator = Locator(
        resourceHref,
        resourceType,
        publication.metadata.title,
        Locations()
      )
    )
  }

  override fun nextResource(v: View?) {
    launch {
      if (layoutDirectionIsRTL()) {
        // The view has RTL layout
        resourcePager.currentItem = resourcePager.currentItem - 1
      } else {
        // The view has LTR layout
        resourcePager.currentItem = resourcePager.currentItem + 1
      }
      val resource = publication.images[resourcePager.currentItem]
      val resourceHref = resource.href ?: ""
      val resourceType = resource.typeLink ?: ""

      navigatorDelegate?.locationDidChange(
        locator = Locator(
          resourceHref,
          resourceType,
          publication.metadata.title,
          Locations()
        )
      )
    }
  }

  override fun previousResource(v: View?) {
    launch {
      if (layoutDirectionIsRTL()) {
        // The view has RTL layout
        resourcePager.currentItem = resourcePager.currentItem + 1
      } else {
        // The view has LTR layout
        resourcePager.currentItem = resourcePager.currentItem - 1
      }
      val resource = publication.images[resourcePager.currentItem]
      val resourceHref = resource.href ?: ""
      val resourceType = resource.typeLink ?: ""

      navigatorDelegate?.locationDidChange(
        locator = Locator(
          resourceHref,
          resourceType,
          publication.metadata.title,
          Locations()
        )
      )
    }
  }

  override fun toggleActionBar() {
    if (allowToggleActionBar) {
      launch {
        if (supportActionBar!!.isShowing) {
          resourcePager.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
              or View.SYSTEM_UI_FLAG_IMMERSIVE)
        } else {
          resourcePager.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
      }
    }
  }

  override fun toggleActionBar(v: View?) {
    toggleActionBar()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
      if (data != null) {

        val locator = data.getSerializableExtra("locator") as Locator

        // Set the progression fetched
        navigatorDelegate?.locationDidChange(locator = locator)

        fun setCurrent(resources: ArrayList<*>) {
          for (index in 0 until resources.count()) {
            val resource = resources[index] as String
            if (resource.endsWith(locator.href!!)) {
              resourcePager.currentItem = index
              break
            }
          }
        }

        resourcePager.adapter = adapter

        setCurrent(resources)

        if (supportActionBar!!.isShowing && allowToggleActionBar) {
          resourcePager.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
              or View.SYSTEM_UI_FLAG_IMMERSIVE)
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, data)
  }

}

