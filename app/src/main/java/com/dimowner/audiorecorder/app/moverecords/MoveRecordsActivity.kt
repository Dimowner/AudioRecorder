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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.databinding.ActivityMoveRecordsBinding
import com.dimowner.audiorecorder.util.isVisible
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@ExperimentalCoroutinesApi
class MoveRecordsActivity : Activity() {

	private lateinit var viewModel: MoveRecordsViewModel

	private val adapter = MoveRecordsAdapter()

	val scope = CoroutineScope(Dispatchers.Main)

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

		binding.btnStart.setOnClickListener {
			viewModel.showProgress(true)
		}
		binding.btnEnd.setOnClickListener {
			viewModel.showProgress(false)
		}
	}

	private fun onScreenUpdate(state: MoveRecordsScreenState) {
		binding.progress.isVisible = state.showProgress
		binding.txtCount.text = "Count = " + state.count
		adapter.showFooter(state.showFooterItem)
		adapter.submitList(state.list)
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