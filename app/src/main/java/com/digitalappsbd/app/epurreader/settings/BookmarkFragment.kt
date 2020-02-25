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
import com.digitalappsbd.app.epurreader.db.Bookmark
import com.digitalappsbd.app.epurreader.db.BookmarksDatabase
import kotlinx.android.synthetic.main.fragment_bookmark.*
import kotlinx.android.synthetic.main.item_recycle_bookmark.view.*
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.readium.r2.shared.Locations
import org.readium.r2.shared.Locator
import org.readium.r2.shared.Publication
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class BookmarkFragment : Fragment() {
  private var callBack: UserSettings.OnChapterInterceptor? = null

  private lateinit var bookmarkDB: BookmarksDatabase
  private var bookID: Long = 0
  private lateinit var publication: Publication
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_bookmark, container, false)
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    callBack = context as UserSettings.OnChapterInterceptor
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    bookmarkDB = BookmarksDatabase(requireContext())
    val bookmarks = bookmarkDB.bookmarks.list(bookID)
      .sortedWith(compareBy({ it.resourceIndex }, { it.location.progression })).toMutableList()
    val bookmarksAdapter = BookMarksAdapter(requireActivity(), bookmarks, publication)
    bookmark_list.adapter = bookmarksAdapter
    bookmark_list.setOnItemClickListener { _, _, position, _ ->

      //Link to the resource in the publication
      val bookmark = bookmarks[position]
      val resourceHref = bookmark.resourceHref
      val resourceType = bookmark.resourceType

      //Progression of the selected bookmark
      val bookmarkProgression = bookmarks[position].location.progression

      val locator =
        Locator(
          resourceHref,
          resourceType,
          publication.metadata.title,
          Locations(progression = bookmarkProgression),
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
    ): BookmarkFragment {
      return BookmarkFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_BOOK_ID, bookId)
          putSerializable(ARG_PUBLICATION, publication)
        }
      }
    }
  }

  inner class BookMarksAdapter(
    val activity: Activity,
    private val items: MutableList<Bookmark>,
    private val publication: Publication
  ) : BaseAdapter() {

    private inner class ViewHolder(row: View?) {
      internal var bookmarkChapter: TextView? = null
      internal var bookmarkProgression: TextView? = null
      internal var bookmarkTimestamp: TextView? = null
      internal var bookmarkOverflow: ImageView? = null

      init {
        this.bookmarkChapter = row?.bookmark_chapter as TextView
        this.bookmarkProgression = row.bookmark_progression as TextView
        this.bookmarkTimestamp = row.bookmark_timestamp as TextView
        this.bookmarkOverflow = row.overflow as ImageView

      }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

      val view: View?
      val viewHolder: ViewHolder
      if (convertView == null) {
        val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        view = inflater.inflate(R.layout.item_recycle_bookmark, null)
        viewHolder = ViewHolder(view)
        view?.tag = viewHolder
      } else {
        view = convertView
        viewHolder = view.tag as ViewHolder
      }

      val bookmark = getItem(position) as Bookmark

      var title = getBookSpineItem(bookmark.resourceHref)
      if (title.isNullOrEmpty()) {
        title = "*Title Missing*"
      }
      val bookmarkProgressionValue = bookmark.location.progression ?: 0.0
      val formattedProgression =
        "${((bookmarkProgressionValue * 100).roundToInt())}% through resource"
      val formattedDate = DateTime(bookmark.creationDate).toString(DateTimeFormat.shortDateTime())

      viewHolder.bookmarkChapter?.text = title
      if (bookmarkProgressionValue > 1) {

        viewHolder.bookmarkProgression?.text = String.format(
          "%d:%d",
          TimeUnit.MILLISECONDS.toMinutes(bookmark.location.progression!!.toLong()),
          TimeUnit.MILLISECONDS.toSeconds(bookmark.location.progression!!.toLong()) - TimeUnit.MINUTES.toSeconds(
            TimeUnit.MILLISECONDS.toMinutes(bookmark.location.progression!!.toLong())
          )
        )

      } else {
        viewHolder.bookmarkProgression?.text = formattedProgression
      }
      viewHolder.bookmarkTimestamp?.text = formattedDate

      viewHolder.bookmarkOverflow?.setOnClickListener {

        val popupMenu = PopupMenu(parent?.context, viewHolder.bookmarkChapter)
        popupMenu.menuInflater.inflate(R.menu.menu_bookmark, popupMenu.menu)
        popupMenu.show()

        popupMenu.setOnMenuItemClickListener { item ->
          if (item.itemId == R.id.delete) {
            bookmarkDB.bookmarks.delete(items[position])
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

    private fun getBookSpineItem(href: String): String? {
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

    override fun getItemId(position: Int): Long {
      return position.toLong()
    }

  }
}
