package com.folioreader.ui.view.settings

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.folioreader.Config
import com.folioreader.R
import com.folioreader.model.event.ReloadDataEvent
import com.folioreader.ui.activity.FolioActivity
import com.folioreader.ui.activity.FolioActivityCallback
import com.folioreader.ui.fragment.MediaControllerFragment
import com.folioreader.util.AppUtil
import com.folioreader.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.view_day_night_configue.*
import org.greenrobot.eventbus.EventBus

class ConfigDayNightBottomSheet : BottomSheetDialogFragment() {

  companion object {
    const val FADE_DAY_NIGHT_MODE = 500
    @JvmField
    val LOG_TAG: String = ConfigDayNightBottomSheet::class.java.simpleName
  }


  private lateinit var config: Config
  private var isNightMode = false

  private lateinit var activityCallback: FolioActivityCallback

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.view_day_night_configue, container)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    if (activity is FolioActivity)
      activityCallback = activity as FolioActivity

    view.viewTreeObserver.addOnGlobalLayoutListener {
      val dialog = dialog as BottomSheetDialog
      val bottomSheet =
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
      val behavior = BottomSheetBehavior.from(bottomSheet!!)
      behavior.state = BottomSheetBehavior.STATE_EXPANDED
      behavior.peekHeight = 0
    }

    config = AppUtil.getSavedConfig(activity)!!
    initViews()
  }

  override fun onDestroy() {
    super.onDestroy()
    view?.viewTreeObserver?.addOnGlobalLayoutListener(null)
  }

  private fun initViews() {
    inflateView()
    isNightMode = config.isNightMode
    if (isNightMode) {
      container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.night))
    } else {
      container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
    }

    if (isNightMode) {
      view_config_ib_day_mode.isSelected = false
      view_config_ib_night_mode.isSelected = true
      UiUtil.setColorIntToDrawable(config.themeColor, view_config_ib_night_mode.drawable)
      UiUtil.setColorResToDrawable(R.color.app_gray, view_config_ib_day_mode.drawable)
    } else {
      view_config_ib_day_mode.isSelected = true
      view_config_ib_night_mode.isSelected = false
      UiUtil.setColorIntToDrawable(config.themeColor, view_config_ib_day_mode!!.drawable)
      UiUtil.setColorResToDrawable(R.color.app_gray, view_config_ib_night_mode.drawable)
    }
  }

  private fun inflateView() {

    view_config_ib_day_mode.setOnClickListener {
      isNightMode = true
      toggleBlackTheme()
      view_config_ib_day_mode.isSelected = true
      view_config_ib_night_mode.isSelected = false
      setToolBarColor()
      setAudioPlayerBackground()
      UiUtil.setColorResToDrawable(R.color.app_gray, view_config_ib_night_mode.drawable)
      UiUtil.setColorIntToDrawable(config.themeColor, view_config_ib_day_mode.drawable)
    }

    view_config_ib_night_mode.setOnClickListener {
      isNightMode = false
      toggleBlackTheme()
      view_config_ib_day_mode.isSelected = false
      view_config_ib_night_mode.isSelected = true
      UiUtil.setColorResToDrawable(R.color.app_gray, view_config_ib_day_mode.drawable)
      UiUtil.setColorIntToDrawable(config.themeColor, view_config_ib_night_mode.drawable)
      setToolBarColor()
      setAudioPlayerBackground()
    }

  }


  private fun toggleBlackTheme() {

    val day = ContextCompat.getColor(context!!, R.color.white)
    val night = ContextCompat.getColor(context!!, R.color.night)

    val colorAnimation = ValueAnimator.ofObject(
      ArgbEvaluator(),
      if (isNightMode) night else day, if (isNightMode) day else night
    )
    colorAnimation.duration = FADE_DAY_NIGHT_MODE.toLong()

    colorAnimation.addUpdateListener { animator ->
      val value = animator.animatedValue as Int
      container.setBackgroundColor(value)
    }

    colorAnimation.addListener(object : Animator.AnimatorListener {
      override fun onAnimationStart(animator: Animator) {}

      override fun onAnimationEnd(animator: Animator) {
        isNightMode = !isNightMode
        config.isNightMode = isNightMode
        AppUtil.saveConfig(activity, config)
        EventBus.getDefault().post(ReloadDataEvent())
      }

      override fun onAnimationCancel(animator: Animator) {}

      override fun onAnimationRepeat(animator: Animator) {}
    })

    colorAnimation.duration = FADE_DAY_NIGHT_MODE.toLong()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

      val attrs = intArrayOf(android.R.attr.navigationBarColor)
      val typedArray = activity?.theme?.obtainStyledAttributes(attrs)
      val defaultNavigationBarColor = typedArray?.getColor(
        0,
        ContextCompat.getColor(requireContext(), R.color.white)
      )
      val black = ContextCompat.getColor(requireContext(), R.color.black)

      val navigationColorAnim = ValueAnimator.ofObject(
        ArgbEvaluator(),
        if (isNightMode) black else defaultNavigationBarColor,
        if (isNightMode) defaultNavigationBarColor else black
      )

      navigationColorAnim.addUpdateListener { valueAnimator ->
        val value = valueAnimator.animatedValue as Int
        activity?.window?.navigationBarColor = value
      }

      navigationColorAnim.duration = FADE_DAY_NIGHT_MODE.toLong()
      navigationColorAnim.start()
    }

    colorAnimation.start()
  }


  private fun setToolBarColor() {
    if (isNightMode) {
      activityCallback.setDayMode()
    } else {
      activityCallback.setNightMode()
    }
  }

  private fun setAudioPlayerBackground() {

    var mediaControllerFragment: Fragment? =
      fragmentManager?.findFragmentByTag(MediaControllerFragment.LOG_TAG)
        ?: return
    mediaControllerFragment = mediaControllerFragment as MediaControllerFragment
    if (isNightMode) {
      mediaControllerFragment.setDayMode()
    } else {
      mediaControllerFragment.setNightMode()
    }
  }
}