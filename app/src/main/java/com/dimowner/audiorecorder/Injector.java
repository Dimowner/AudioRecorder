/*
 * Copyright 2018 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder;

import android.content.Context;

import com.dimowner.audiorecorder.audio.player.AudioPlayer;
import com.dimowner.audiorecorder.audio.player.PlayerContract;
import com.dimowner.audiorecorder.audio.recorder.AudioRecorder;
import com.dimowner.audiorecorder.audio.recorder.RecorderContract;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.FileRepositoryImpl;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.PrefsImpl;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.LocalRepositoryImpl;
import com.dimowner.audiorecorder.data.database.RecordsDataSource;
import com.dimowner.audiorecorder.ui.MainContract;
import com.dimowner.audiorecorder.ui.MainPresenter;
import com.dimowner.audiorecorder.ui.records.RecordsContract;
import com.dimowner.audiorecorder.ui.records.RecordsPresenter;
import com.dimowner.audiorecorder.ui.settings.SettingsContract;
import com.dimowner.audiorecorder.ui.settings.SettingsPresenter;

public class Injector {

	private Context context;

	private BackgroundQueue loadingTasks;
	private BackgroundQueue recordingTasks;

	private MainContract.UserActionsListener mainPresenter;
	private RecordsContract.UserActionsListener recordsPresenter;
	private SettingsContract.UserActionsListener settingsPresenter;

	public Injector(Context context) {
		this.context = context;
	}

	public Prefs providePrefs() {
		return PrefsImpl.getInstance(context);
	}

	public RecordsDataSource provideRecordsDataSource() {
		return RecordsDataSource.getInstance(context);
	}

	public FileRepository provideFileRepository() {
		return FileRepositoryImpl.getInstance(context, providePrefs());
	}

	public LocalRepository provideLocalRepository() {
		return LocalRepositoryImpl.getInstance(provideRecordsDataSource());
	}

	public BackgroundQueue provideLoadingTasksQueue() {
		if (loadingTasks == null) {
			loadingTasks = new BackgroundQueue("LoadingTasks");
		}
		return loadingTasks;
	}

	public BackgroundQueue provideRecordingTasksQueue() {
		if (recordingTasks == null) {
			recordingTasks = new BackgroundQueue("RecordingTasks");
		}
		return recordingTasks;
	}

	public ColorMap provideColorMap() {
		return ColorMap.getInstance();
	}

	public PlayerContract.Player provideAudioPlayer() {
		return AudioPlayer.getInstance();
	}

	public RecorderContract.Recorder provideAudioRecorder() {
		return AudioRecorder.getInstance();
	}

	public MainContract.UserActionsListener provideMainPresenter() {
		if (mainPresenter == null) {
			mainPresenter = new MainPresenter(providePrefs(), provideFileRepository(),
					provideLocalRepository(), provideAudioPlayer(), provideAudioRecorder(),
					provideLoadingTasksQueue(), provideRecordingTasksQueue());
		}
		return mainPresenter;
	}

	public RecordsContract.UserActionsListener provideRecordsPresenter() {
		if (recordsPresenter == null) {
			recordsPresenter = new RecordsPresenter(provideLocalRepository(), provideFileRepository(),
					provideLoadingTasksQueue(), provideRecordingTasksQueue(), provideAudioPlayer(), providePrefs());
		}
		return recordsPresenter;
	}

	public SettingsContract.UserActionsListener provideSettingsPresenter() {
		if (settingsPresenter == null) {
			settingsPresenter = new SettingsPresenter(provideLocalRepository(), provideFileRepository(),
					provideRecordingTasksQueue(), providePrefs());
		}
		return settingsPresenter;
	}

	public void clearRecordsPresenter() {
		if (recordsPresenter != null) {
			recordsPresenter.unbindView();
			recordsPresenter.clear();
			recordsPresenter = null;
		}
	}

	public void clearMainPresenter() {
		if (mainPresenter != null) {
			mainPresenter.unbindView();
			mainPresenter.clear();
			mainPresenter = null;
		}
	}

	public void clearSettingsPresenter() {
		if (settingsPresenter != null) {
			settingsPresenter.unbindView();
			settingsPresenter.clear();
			settingsPresenter = null;
		}
	}
}
