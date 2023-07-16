/*
 * Copyright 2018 Dmytro Ponomarenko
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

import com.dimowner.audiorecorder.app.AppRecorder;
import com.dimowner.audiorecorder.app.AppRecorderImpl;
import com.dimowner.audiorecorder.app.browser.FileBrowserContract;
import com.dimowner.audiorecorder.app.browser.FileBrowserPresenter;
import com.dimowner.audiorecorder.app.lostrecords.LostRecordsContract;
import com.dimowner.audiorecorder.app.lostrecords.LostRecordsPresenter;
import com.dimowner.audiorecorder.app.moverecords.MoveRecordsViewModel;
import com.dimowner.audiorecorder.app.settings.SettingsMapper;
import com.dimowner.audiorecorder.app.setup.SetupContract;
import com.dimowner.audiorecorder.app.setup.SetupPresenter;
import com.dimowner.audiorecorder.app.trash.TrashContract;
import com.dimowner.audiorecorder.app.trash.TrashPresenter;
import com.dimowner.audiorecorder.audio.AudioWaveformVisualization;
import com.dimowner.audiorecorder.audio.player.AudioPlayerNew;
import com.dimowner.audiorecorder.audio.player.PlayerContractNew;
import com.dimowner.audiorecorder.audio.recorder.AudioRecorder;
import com.dimowner.audiorecorder.audio.recorder.ThreeGpRecorder;
import com.dimowner.audiorecorder.audio.recorder.RecorderContract;
import com.dimowner.audiorecorder.audio.recorder.WavRecorder;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.FileRepositoryImpl;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.PrefsImpl;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.LocalRepositoryImpl;
import com.dimowner.audiorecorder.data.database.RecordsDataSource;
import com.dimowner.audiorecorder.app.main.MainContract;
import com.dimowner.audiorecorder.app.main.MainPresenter;
import com.dimowner.audiorecorder.app.records.RecordsContract;
import com.dimowner.audiorecorder.app.records.RecordsPresenter;
import com.dimowner.audiorecorder.app.settings.SettingsContract;
import com.dimowner.audiorecorder.app.settings.SettingsPresenter;
import com.dimowner.audiorecorder.data.database.TrashDataSource;

public class Injector {

	private BackgroundQueue loadingTasks;
	private BackgroundQueue recordingTasks;
	private BackgroundQueue importTasks;
	private BackgroundQueue processingTasks;
	private BackgroundQueue copyTasks;

	private MainContract.UserActionsListener mainPresenter;
	private RecordsContract.UserActionsListener recordsPresenter;
	private SettingsContract.UserActionsListener settingsPresenter;
	private LostRecordsContract.UserActionsListener lostRecordsPresenter;
	private FileBrowserContract.UserActionsListener fileBrowserPresenter;
	private TrashContract.UserActionsListener trashPresenter;
	private SetupContract.UserActionsListener setupPresenter;

	private MoveRecordsViewModel moveRecordsViewModel;

	private AudioPlayerNew audioPlayer = null;

	public Prefs providePrefs(Context context) {
		return PrefsImpl.getInstance(context);
	}

	public RecordsDataSource provideRecordsDataSource(Context context) {
		return RecordsDataSource.getInstance(context);
	}

	public TrashDataSource provideTrashDataSource(Context context) {
		return TrashDataSource.getInstance(context);
	}

	public FileRepository provideFileRepository(Context context) {
		return FileRepositoryImpl.getInstance(context, providePrefs(context));
	}

	public LocalRepository provideLocalRepository(Context context) {
		return LocalRepositoryImpl.getInstance(provideRecordsDataSource(context), provideTrashDataSource(context), provideFileRepository(context), providePrefs(context));
	}

	public AppRecorder provideAppRecorder(Context context) {
		return AppRecorderImpl.getInstance(provideAudioRecorder(context), provideLocalRepository(context),
				provideLoadingTasksQueue(), providePrefs(context));
	}

	public AudioWaveformVisualization provideAudioWaveformVisualization() {
		return new AudioWaveformVisualization(provideProcessingTasksQueue());
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

	public BackgroundQueue provideImportTasksQueue() {
		if (importTasks == null) {
			importTasks = new BackgroundQueue("ImportTasks");
		}
		return importTasks;
	}

	public BackgroundQueue provideProcessingTasksQueue() {
		if (processingTasks == null) {
			processingTasks = new BackgroundQueue("ProcessingTasks");
		}
		return processingTasks;
	}

	public BackgroundQueue provideCopyTasksQueue() {
		if (copyTasks == null) {
			copyTasks = new BackgroundQueue("CopyTasks");
		}
		return copyTasks;
	}

	public ColorMap provideColorMap(Context context) {
		return ColorMap.getInstance(providePrefs(context));
	}

	public SettingsMapper provideSettingsMapper(Context context) {
		return SettingsMapper.getInstance(context);
	}

	public PlayerContractNew.Player provideAudioPlayer() {
		if (audioPlayer == null) {
			synchronized (PlayerContractNew.Player.class) {
				if (audioPlayer == null) {
					audioPlayer = new AudioPlayerNew();
				}
			}
		}
		return audioPlayer;
	}

	public RecorderContract.Recorder provideAudioRecorder(Context context) {
		switch (providePrefs(context).getSettingRecordingFormat()) {
			default:
			case AppConstants.FORMAT_M4A:
				return AudioRecorder.getInstance();
			case AppConstants.FORMAT_WAV:
				return WavRecorder.getInstance();
			case AppConstants.FORMAT_3GP:
				return ThreeGpRecorder.getInstance();
		}
	}

	public MainContract.UserActionsListener provideMainPresenter(Context context) {
		if (mainPresenter == null) {
			mainPresenter = new MainPresenter(providePrefs(context), provideFileRepository(context),
					provideLocalRepository(context), provideAudioPlayer(), provideAppRecorder(context),
					provideRecordingTasksQueue(), provideLoadingTasksQueue(), provideProcessingTasksQueue(),
					provideImportTasksQueue(), provideSettingsMapper(context));
		}
		return mainPresenter;
	}

	public RecordsContract.UserActionsListener provideRecordsPresenter(Context context) {
		if (recordsPresenter == null) {
			recordsPresenter = new RecordsPresenter(provideLocalRepository(context), provideFileRepository(context),
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideAudioPlayer(), provideAppRecorder(context), providePrefs(context));
		}
		return recordsPresenter;
	}

	public SettingsContract.UserActionsListener provideSettingsPresenter(Context context) {
		if (settingsPresenter == null) {
			settingsPresenter = new SettingsPresenter(provideLocalRepository(context), provideFileRepository(context),
					provideRecordingTasksQueue(), provideLoadingTasksQueue(), providePrefs(context),
					provideSettingsMapper(context), provideAppRecorder(context));
		}
		return settingsPresenter;
	}

	public TrashContract.UserActionsListener provideTrashPresenter(Context context) {
		if (trashPresenter == null) {
			trashPresenter = new TrashPresenter(provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideFileRepository(context), provideLocalRepository(context));
		}
		return trashPresenter;
	}

	public SetupContract.UserActionsListener provideSetupPresenter(Context context) {
		if (setupPresenter == null) {
			setupPresenter = new SetupPresenter(providePrefs(context));
		}
		return setupPresenter;
	}

	public MoveRecordsViewModel provideMoveRecordsViewModel(Context context) {
		if (moveRecordsViewModel == null) {
			moveRecordsViewModel = new MoveRecordsViewModel(
					provideLoadingTasksQueue(),
					provideLocalRepository(context),
					provideFileRepository(context),
					provideSettingsMapper(context),
					provideAudioPlayer(),
					provideAppRecorder(context),
					providePrefs(context)
			);
		}
		return moveRecordsViewModel;
	}

	public LostRecordsContract.UserActionsListener provideLostRecordsPresenter(Context context) {
		if (lostRecordsPresenter == null) {
			lostRecordsPresenter = new LostRecordsPresenter(provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideLocalRepository(context), providePrefs(context));
		}
		return lostRecordsPresenter;
	}

	public FileBrowserContract.UserActionsListener provideFileBrowserPresenter(Context context) {
		if (fileBrowserPresenter == null) {
			fileBrowserPresenter = new FileBrowserPresenter(providePrefs(context), provideAppRecorder(context), provideImportTasksQueue(),
					provideLoadingTasksQueue(), provideRecordingTasksQueue(),
					provideLocalRepository(context), provideFileRepository(context));
		}
		return fileBrowserPresenter;
	}

	public void releaseTrashPresenter() {
		if (trashPresenter != null) {
			trashPresenter.clear();
			trashPresenter = null;
		}
	}

	public void releaseLostRecordsPresenter() {
		if (lostRecordsPresenter != null) {
			lostRecordsPresenter.clear();
			lostRecordsPresenter = null;
		}
	}

	public void releaseFileBrowserPresenter() {
		if (fileBrowserPresenter != null) {
			fileBrowserPresenter.clear();
			fileBrowserPresenter = null;
		}
	}

	public void releaseRecordsPresenter() {
		if (recordsPresenter != null) {
			recordsPresenter.clear();
			recordsPresenter = null;
		}
	}

	public void releaseMainPresenter() {
		if (mainPresenter != null) {
			mainPresenter.clear();
			mainPresenter = null;
		}
	}

	public void releaseSettingsPresenter() {
		if (settingsPresenter != null) {
			settingsPresenter.clear();
			settingsPresenter = null;
		}
	}

	public void releaseSetupPresenter() {
		if (setupPresenter != null) {
			setupPresenter.clear();
			setupPresenter = null;
		}
	}

	public void releaseMoveRecordsViewModel() {
		if (moveRecordsViewModel != null) {
			moveRecordsViewModel.clear();
			moveRecordsViewModel = null;
		}
	}

	public void closeTasks() {
		loadingTasks.cleanupQueue();
		loadingTasks.close();
		importTasks.cleanupQueue();
		importTasks.close();
		processingTasks.cleanupQueue();
		processingTasks.close();
		recordingTasks.cleanupQueue();
		recordingTasks.close();
	}
}
