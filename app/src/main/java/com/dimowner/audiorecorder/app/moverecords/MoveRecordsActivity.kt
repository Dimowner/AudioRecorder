/*
 * Copyright 2021 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.app.moverecords

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewPropertyAnimator
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.PlaybackService
import com.dimowner.audiorecorder.app.widget.TouchLayout.ThresholdListener
import com.dimowner.audiorecorder.app.widget.WaveformViewNew
import com.dimowner.audiorecorder.databinding.ActivityMoveRecordsBinding
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.util.isVisible
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@ExperimentalCoroutinesApi
class MoveRecordsActivity : Activity() {

	private lateinit var viewModel: MoveRecordsViewModel

	private val adapter = MoveRecordsAdapter()

	var scope = CoroutineScope(Dispatchers.Main)

	private lateinit var binding: ActivityMoveRecordsBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		val colorMap = ARApplication.getInjector().provideColorMap()
		setTheme(colorMap.appThemeResource)
		super.onCreate(savedInstanceState)
		binding = ActivityMoveRecordsBinding.inflate(layoutInflater)
		val view = binding.root
		setContentView(view)

		viewModel = ARApplication.getInjector().provideMoveRecordsViewModel()

		binding.recyclerView.layoutManager = LinearLayoutManager(applicationContext)
		binding.recyclerView.adapter = adapter

		scope.launch {
			viewModel.uiState.collect { onScreenUpdate(it) }
		}
		scope.launch {
			viewModel.uiPlayState.collect { onPlayPanelUpdate(it) }
		}
		scope.launch {
			viewModel.event.collect { handleViewEvent(it) }
		}

		binding.btnPlay.setOnClickListener {
			viewModel.startPlayback()
		}
		binding.btnStop.setOnClickListener {
			viewModel.stopPlayback()
		}
		adapter.itemClickListener = {
			//TODO: Need public storage permission
			viewModel.startPlaybackById(it.id)
		}
		binding.waveformView.showTimeline(false)

		binding.playProgress.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				if (fromUser) {
					val value = AndroidUtils.dpToPx(progress * binding.waveformView.getWaveformLength() / 1000).toInt()
					binding.waveformView.seekPx(value)
					viewModel.seekPlayback(binding.waveformView.pxToMill(value))
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {
//				presenter.disablePlaybackProgressListener()
			}

			override fun onStopTrackingTouch(seekBar: SeekBar) {
//				presenter.enablePlaybackProgressListener()
			}
		})

		binding.waveformView.setOnSeekListener(object : WaveformViewNew.OnSeekListener {
			override fun onStartSeek() {
//				presenter.disablePlaybackProgressListener()
			}

			override fun onSeek(px: Int, mills: Long) {
//				presenter.enablePlaybackProgressListener()
//				//TODO: Find a better way to convert px to mills here
				viewModel.seekPlayback(binding.waveformView.pxToMill(px))
				val length: Int = binding.waveformView.getWaveformLength()
				if (length > 0) {
					binding.playProgress.progress = 1000 * AndroidUtils.pxToDp(px).toInt() / length
				}
				binding.txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
			}

			override fun onSeeking(px: Int, mills: Long) {
				val length: Int = binding.waveformView.getWaveformLength()
				if (length > 0) {
					binding.playProgress.progress = 1000 * AndroidUtils.pxToDp(px).toInt() / length
				}
				binding.txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(mills)
			}
		})

		binding.touchLayout.setBackgroundResource(colorMap.playbackPanelBackground)
		binding.touchLayout.setOnThresholdListener(object : ThresholdListener {
			override fun onTopThreshold() {
				viewModel.stopPlayback()
			}

			override fun onBottomThreshold() {
				viewModel.stopPlayback()
			}

			override fun onTouchDown() {}
			override fun onTouchUp() {}
		})
	}

	private fun onScreenUpdate(state: MoveRecordsScreenState) {
		binding.progress.isVisible = state.showProgress
		binding.txtCount.text = "Count = " + state.count
		adapter.showFooterProgress(state.showFooterProgressItem)
		adapter.submitList(state.list)
		adapter.activeItem = state.activeRecordPos

		if (state.isShowPlayPanel) {
			showPlayerPanel()
		} else {
			hidePlayPanel()
		}
		if (state.playState == PlayState.PLAYING) {
			binding.btnPlay.setImageResource(R.drawable.ic_pause)
		} else {
			binding.btnPlay.setImageResource(R.drawable.ic_play)
		}
	}

	private fun onPlayPanelUpdate(state: MoveRecordsPlayPanelState) {
		binding.txtName.text = state.playRecordName
		binding.playProgress.progress = state.playProgress
		binding.waveformView.setPlayback(state.playProgressMills)
		binding.txtProgress.text = TimeUtils.formatTimeIntervalHourMinSec2(state.playProgressMills)
		binding.txtDuration.text = TimeUtils.formatTimeIntervalHourMinSec2(state.playRecordDuration)
		binding.waveformView.setWaveform(state.activeRecordData, state.playRecordDuration, state.playProgressMills)
	}

	private fun handleViewEvent(event: MoveRecordsEvent) {
		when (event) {
			is MoveRecordsEvent.StartPlaybackService -> {
				PlaybackService.startServiceForeground(applicationContext, event.name)
			}
			is MoveRecordsEvent.ShowError -> {}
		}
	}

	private fun showPlayerPanel() {
		if (!binding.touchLayout.isVisible) {
			binding.touchLayout.isVisible = true
			if (binding.touchLayout.height == 0) {
				binding.touchLayout.translationY = AndroidUtils.dpToPx(800)
			} else {
				binding.touchLayout.translationY = binding.touchLayout.height.toFloat()
			}
			adapter.showFooterPanel(true)
			val animator: ViewPropertyAnimator = binding.touchLayout.animate()
			animator.translationY(0f)
				.setDuration(200)
				.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						val o: Int = binding.recyclerView.computeVerticalScrollOffset()
						val r: Int = binding.recyclerView.computeVerticalScrollRange()
						val e: Int = binding.recyclerView.computeVerticalScrollExtent()
						val k = o.toFloat() / (r - e).toFloat()
						binding.recyclerView.smoothScrollBy(0, (binding.touchLayout.height * k).toInt())
						animator.setListener(null)
					}
				})
				.start()
		}
	}

	private fun hidePlayPanel() {
		if (binding.touchLayout.isVisible) {
			adapter.showFooterPanel(false)
			val animator: ViewPropertyAnimator = binding.touchLayout.animate()
			animator.translationY(binding.touchLayout.height.toFloat())
				.setDuration(200)
				.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						binding.touchLayout.isVisible = false
						animator.setListener(null)
					}
				})
				.start()
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		clear()
	}

	override fun onBackPressed() {
		super.onBackPressed()
		clear()
	}

	private fun clear() {
		ARApplication.getInjector().releaseMoveRecordsViewModel()
		scope.cancel()
	}

	companion object {
		fun getStartIntent(context: Context): Intent {
			return Intent(context, MoveRecordsActivity::class.java)
		}
	}
}