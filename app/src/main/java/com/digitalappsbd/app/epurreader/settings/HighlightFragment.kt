package com.digitalappsbd.app.epurreader.settings


import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.digitalappsbd.app.epurreader.R
import com.digitalappsbd.app.epurreader.db.Highlight
import com.digitalappsbd.app.epurreader.db.HighligtsDatabase
import kotlinx.android.synthetic.main.fragment_highlight.*
import kotlinx.android.synthetic.main.item_recycle_highlight.view.*
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.readium.r2.shared.Locations
import org.readium.r2.shared.Locator
import org.readium.r2.shared.Publication

/**
 * A simple [Fragment] subclass.
 */
class HighlightFragment : Fragment() {

  private var callBack: UserSettings.OnChapterInterceptor? = null
  private lateinit var highlightsDB: HighligtsDatabase
  private var bookID: Long = 0
  private lateinit var publication: Publication
  override fun onAttach(context: Context) {
    super.onAttach(context)
    callBack = context as UserSettings.OnChapterInterceptor
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_highlight, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    highlightsDB = HighligtsDatabase(requireContext())
    val highlights = highlightsDB.highlights.listAll(bookID)
      .sortedWith(compareBy({ it.resourceIndex }, { it.location.progression })).toMutableList()
    val highlightsAdapter = HighlightsAdapter(requireActivity(), highlights, publication)
    highlight_list.adapter = highlightsAdapter
    highlight_list.setOnItemClickListener { _, _, position, _ ->
      //Link to the resource in the publication
      val highlight = highlights[position]
      val resourceHref = highlight.resourceHref
      val resourceType = highlight.resourceType
      //Progression of the selected bookmark
      val highlightProgression = highlights[position].location.progression
      val locator =
        Locator(
          resourceHref,
          resourceType,
          publication.metadata.title,
          Locations(progression = highlightProgression),
          null
        )
      locator.let {
        callBack?.onChapterClick(it)
      }

    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let {
      bookID = it.getLong(ARG_BOOK_ID)
      publication = it.getSerializable(ARG_PUBLICATION) as Publication
    }
  }

  companion object {
    private const val ARG_BOOK_ID = "ARG_BOOK_ID"
    private const val ARG_PUBLICATION = "ARG_PUBLICATION"
    @JvmStatic
    fun newInstance(
      bookId: Long,
      publication: Publication
    ): HighlightFragment {
      return HighlightFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_BOOK_ID, bookId)
          putSerializable(ARG_PUBLICATION, publication)
        }
      }
    }
  }

  inner class HighlightsAdapter(
    val activity: Activity,
    private val items: MutableList<Highlight>,
    private val publication: Publication
  ) : BaseAdapter() {

    private inner class ViewHolder(row: View?) {
      internal var highlightedText: TextView? = null
      internal var highlightTimestamp: TextView? = null
      internal var highlightChapter: TextView? = null
      internal var highlightOverflow: ImageView? = null
      internal var annotation: TextView? = null

      init {
        this.highlightedText = row?.highlight_text as TextView
        this.highlightTimestamp = row.highlight_time_stamp as TextView
        this.highlightChapter = row.highlight_chapter as TextView
        this.highlightOverflow = row.highlight_overflow as ImageView
        this.annotation = row.annotation as TextView
      }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

      val view: View?
      val viewHolder: ViewHolder
      if (convertView == null) {
        val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        view = inflater.inflate(R.layout.item_recycle_highlight, null)
        viewHolder = ViewHolder(view)
        view?.tag = viewHolder
      } else {
        view = convertView
        viewHolder = view.tag as ViewHolder
      }

      val highlight = getItem(position) as Highlight

      viewHolder.highlightChapter!!.text = getHighlightSpineItem(highlight.resourceHref)
      viewHolder.highlightedText!!.text = highlight.locatorText.highlight
      viewHolder.annotation!!.text = highlight.annotation

      val formattedDate = DateTime(highlight.creationDate).toString(DateTimeFormat.shortDateTime())
      viewHolder.highlightTimestamp!!.text = formattedDate

      viewHolder.highlightOverflow?.setOnClickListener {

        val popupMenu = PopupMenu(parent?.context, viewHolder.highlightChapter)
        popupMenu.menuInflater.inflate(R.menu.menu_bookmark, popupMenu.menu)
        popupMenu.show()

        popupMenu.setOnMenuItemClickListener { item ->
          if (item.itemId == R.id.delete) {
            highlightsDB.highlights.delete(items[position])
            items.removeAt(position)
            notifyDataSetChanged()
          }
          false
        }
      }

      return view as View
    }

    override fun getCount(): Int {
      return items.size
    }

    override fun getItem(position: Int): Any {
      return items[position]
    }

    override fun getItemId(position: Int): Long {
      return position.toLong()
    }

    private fun getHighlightSpineItem(href: String): String? {
      for (link in publication.tableOfContents) {
        if (link.href == href) {
          return link.title
        }
      }
      for (link in publication.readingOrder) {
        if (link.href == href) {
          return link.title
        }
      }
      return null
    }

  }

}
