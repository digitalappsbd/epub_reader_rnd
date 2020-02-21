package com.digitalappsbd.app.epurreader.settings


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.digitalappsbd.app.epurreader.R

/**
 * A simple [Fragment] subclass.
 */
class HighlightFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_highlight, container, false)
  }

  companion object {

    private const val ARG_SECTION_NUMBER = "section_number"
    @JvmStatic
    fun newInstance(): HighlightFragment {
      return HighlightFragment()
    }
  }

}
