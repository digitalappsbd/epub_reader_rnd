package com.digitalappsbd.app.epurreader.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.digitalappsbd.app.epurreader.R
import kotlinx.android.synthetic.main.layout_change_font_item.view.*

class FontChangeAdapter(private val onFontChanged: (Int) -> Unit) :
  ListAdapter<String, FontChangeAdapter.FontViewHolder>(StringDC()) {

  private var selectedPosition = -1

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = FontViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.layout_change_font_item, parent, false)
  )

  override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
    holder.bind(getItem(position))
    holder.itemView.setOnClickListener {
      onFontChanged(position)
      selectedPosition = position
      notifyDataSetChanged()
    }
    if (position == selectedPosition) {
      holder.itemView.text_font.setTextColor(
        ContextCompat.getColor(
          holder.itemView.context,
          android.R.color.holo_green_dark
        )
      )
    } else {
      holder.itemView.text_font.setTextColor(
        ContextCompat.getColor(
          holder.itemView.context,
          android.R.color.black
        )
      )
    }
  }


  inner class FontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(item: String) = with(itemView) {
      text_font.text = item
    }
  }

  private class StringDC : DiffUtil.ItemCallback<String>() {

    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

  }
}