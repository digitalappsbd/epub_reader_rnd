package com.digitalappsbd.app.epurreader.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SavedContentPagerAdapter(
  activity: FragmentActivity
) : FragmentStateAdapter(activity) {

  private var list: List<String> = listOf("Highlights", "Bookmarks")

  override fun createFragment(position: Int): Fragment {
    return if (position == 0) HighlightFragment.newInstance()
    else BookmarkFragment.newInstance()
  }


  fun getPageTitle(position: Int): CharSequence =
    list[position]


  override fun getItemCount(): Int = list.size

}