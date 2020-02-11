package com.digitalappsbd.app.epurreader.epub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.digitalappsbd.app.epurreader.R
import kotlinx.android.synthetic.main.layout_chapter_item.view.*
import org.readium.r2.shared.Link

class ChapterAdapter : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

  private var tableOfContext = mutableListOf<Any>()
  fun submitData(tableOfContext: MutableList<Any> = mutableListOf()) {
    this.tableOfContext = tableOfContext
    notifyDataSetChanged()
  }

  class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(item: Any?) = with(itemView) {
      if (item is Pair<*, *>) {
        item as Pair<Int, Link>
        navigation_textView.text = item.second.title
      } else {
        item as Link
        navigation_textView.text = item.title
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
    return ChapterViewHolder(
      LayoutInflater.from(parent.context).inflate(
        R.layout.layout_chapter_item,
        parent,
        false
      )
    )
  }

  override fun getItemCount(): Int = tableOfContext.size
  override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
    holder.bind(tableOfContext[position])
  }

}