package com.digitalappsbd.app.epurreader.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.readium.r2.shared.Publication

class SavedContentPagerAdapter(
  activity: FragmentActivity,
  private val bookId: Long,
  private val publication: Publication
) : FragmentStateAdapter(activity) {

  private var list: List<String> = listOf("Highlights", "Bookmarks")

  override fun createFragment(position: Int): Fragment {
    return if (position == 0) HighlightFragment.newInstance(bookId, publication)
    else BookmarkFragment.newInstance(bookId, publication)
  }


  fun getPageTitle(position: Int): CharSequence =
    list[position]


  override fun getItemCount(): Int = list.size

}