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

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.view.ViewPropertyAnimator
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.PlaybackService
import com.dimowner.audiorecorder.app.widget.TouchLayout.ThresholdListener
import com.dimowner.audiorecorder.app.widget.WaveformViewNew
import com.dimowner.audiorecorder.databinding.ActivityMoveRecordsBinding
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.util.RippleUtils
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.util.isVisible
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.io.File

const val REQ_CODE_READ_EXTERNAL_STORAGE = 505

@ExperimentalCoroutinesApi
class MoveRecordsActivity : Activity() {

	private lateinit var viewModel: MoveRecordsViewModel

	private val adapter = MoveRecordsAdapter()

	var scope = CoroutineScope(Dispatchers.Main)

	private lateinit var binding: ActivityMoveRecordsBinding

	private val connection: ServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(className: ComponentName, service: IBinder) {
			val binder = service as MoveRecordsService.LocalBinder
			val decodeService = binder.getService()
			decodeService.setMoveRecordsListener(object : MoveRecordsServiceListener {

				override fun onRecordMoved() {
					viewModel.loadRecords()
				}

				override fun onFinishMove() {
					viewModel.loadRecords()
				}
			})
		}

		override fun onServiceDisconnected(arg0: ComponentName) {
			viewModel.loadRecords()
		}

		override fun onBindingDied(name: ComponentName) {
			viewModel.loadRecords()
		}
	}

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

		binding.btnMoveAll.background = RippleUtils.createRippleShape(
			ContextCompat.getColor(applicationContext, R.color.white_transparent_80),
			ContextCompat.getColor(applicationContext, R.color.white_transparent_50),
			applicationContext.resources.getDimension(R.dimen.spacing_normal)
		)
		binding.btnMoveAll.setOnClickListener {
			viewModel.moveAllRecords()
		}
		binding.btnPlay.setOnClickListener {
			viewModel.startPlayback()
		}
		binding.btnStop.setOnClickListener {
			viewModel.stopPlayback()
		}
		binding.btnInfo.setOnClickListener {
			showInfoDialog()
		}
		binding.txtCountAndLocation.setOnClickListener {
			viewModel.openRecordsLocation()
		}

		adapter.itemClickListener = {
			viewModel.startPlaybackById(it.id)
		}
		adapter.moveRecordClickListener = {
			MoveRecordsService.startNotification(applicationContext, it.id)
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
				viewModel.disablePlaybackProgressListener()
			}

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				viewModel.enablePlaybackProgressListener()
			}
		})

		binding.waveformView.setOnSeekListener(object : WaveformViewNew.OnSeekListener {
			override fun onStartSeek() {
				viewModel.disablePlaybackProgressListener()
			}

			override fun onSeek(px: Int, mills: Long) {
				viewModel.enablePlaybackProgressListener()
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
		if (checkStoragePermission()) {
			viewModel.loadRecords()
			showInfoDialog()
		}
	}

	override fun onStart() {
		super.onStart()
		val serviceIntent = Intent(this, MoveRecordsService::class.java)
		bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
	}

	override fun onStop() {
		unbindService(connection)
		super.onStop()
	}

	private fun showInfoDialog() {
		if (intent.hasExtra(PREF_KEY_SHOW_INFO) && intent.getBooleanExtra(PREF_KEY_SHOW_INFO, false)) {
			AndroidUtils.showDialog(
				this,
				-1,
				R.string.btn_ok,
				-1,
				R.string.move_records_needed,
				R.string.move_records_info,
				true,
				{},
				null
			)
		}
	}

	private fun onScreenUpdate(state: MoveRecordsScreenState) {
		binding.progress.isVisible = state.showProgress
		if (state.recordsLocation.isNotEmpty()) {
			binding.txtCountAndLocation.text =
				getString(R.string.records_location, state.recordsLocation)
		} else {
			binding.txtCountAndLocation.text = ""
		}
		binding.btnMoveAll.isVisible = state.isMoveAllVisible
		binding.txtEmpty.isVisible = state.isEmptyVisible
		binding.txtTitle.text = getString(R.string.move_records, state.recordsCount)
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
			is MoveRecordsEvent.OpenRecordsLocation -> {
				openRecordsLocation(event.file)
			}
			is MoveRecordsEvent.ShowError -> {}
			is MoveRecordsEvent.MoveAllRecords -> {
				MoveRecordsService.startNotification(applicationContext, event.list)
			}
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

	private fun openRecordsLocation(file: File) {
		val intent = Intent(Intent.ACTION_VIEW)
		val fileUri = FileProvider.getUriForFile(
			applicationContext,
			applicationContext.packageName + ".app_file_provider",
			file
		)
		intent.setDataAndType(fileUri, DocumentsContract.Document.MIME_TYPE_DIR)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		try {
			startActivity(intent)
		} catch (e: ActivityNotFoundException) {
			Timber.e(e)
		}
	}

	private fun checkStoragePermission(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
				&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
			) {
				requestPermissions(
					arrayOf(
						Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.READ_EXTERNAL_STORAGE
					),
					REQ_CODE_READ_EXTERNAL_STORAGE
				)
				return false
			}
		}
		return true
	}

	override fun onDestroy() {
		super.onDestroy()
		clear()
	}

	override fun onBackPressed() {
		super.onBackPressed()
		clear()
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE
			&& grantResults.isNotEmpty()
			&& grantResults[0] == PackageManager.PERMISSION_GRANTED
			&& grantResults[1] == PackageManager.PERMISSION_GRANTED
		) {
			viewModel.loadRecords()
			showInfoDialog()
		}
	}

	private fun clear() {
		ARApplication.getInjector().releaseMoveRecordsViewModel()
		scope.cancel()
	}

	companion object {
		const val PREF_KEY_SHOW_INFO = "PREF_KEY_SHOW_INFO"

		fun getStartIntent(context: Context, showInfo: Boolean): Intent {
			return Intent(context, MoveRecordsActivity::class.java).apply {
				putExtra(PREF_KEY_SHOW_INFO, showInfo)
			}
		}
	}
}