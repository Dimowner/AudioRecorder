/*
 * Copyright 2020 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app;

import com.dimowner.audiorecorder.audio.recorder.RecorderContract;
import com.dimowner.audiorecorder.exception.AppException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class AppRecorderImpl implements AppRecorder {

	private RecorderContract.Recorder audioRecorder;

	private final RecorderContract.RecorderCallback recorderCallback;
	private final List<AppRecorderCallback> appCallbacks;

	private volatile static AppRecorderImpl instance;

	public static AppRecorderImpl getInstance(RecorderContract.Recorder recorder) {
		if (instance == null) {
			synchronized (AppRecorderImpl.class) {
				if (instance == null) {
					instance = new AppRecorderImpl(recorder);
				}
			}
		}
		return instance;
	}

	private AppRecorderImpl(RecorderContract.Recorder recorder) {
		this.audioRecorder = recorder;
		this.appCallbacks = new ArrayList<>();

		recorderCallback = new RecorderContract.RecorderCallback() {
			@Override
			public void onPrepareRecord() {
				audioRecorder.startRecording();
			}

			@Override
			public void onStartRecord(File output) {
				onRecordingStarted(output);
			}

			@Override
			public void onPauseRecord() {
				onRecordingPaused();
			}

			@Override
			public void onRecordProgress(final long mills, final int amplitude) {
				onRecordingProgress(mills, amplitude);
			}

			@Override
			public void onStopRecord(final File output) {
				onRecordingStopped(output);
			}

			@Override
			public void onError(AppException e) {
				Timber.e(e);
				onRecordingError(e);
			}
		};
		audioRecorder.setRecorderCallback(recorderCallback);
	}

	@Override
	public void addRecordingCallback(AppRecorderCallback callback) {
		appCallbacks.add(callback);
	}

	@Override
	public void removeRecordingCallback(AppRecorderCallback callback) {
		appCallbacks.remove(callback);
	}

	@Override
	public void setRecorder(RecorderContract.Recorder recorder) {
		this.audioRecorder = recorder;
		this.audioRecorder.setRecorderCallback(recorderCallback);
	}

	@Override
	public void startRecording(String filePath, int channelCount, int sampleRate, int bitrate) {
		if (!audioRecorder.isRecording()) {
			audioRecorder.prepare(filePath, channelCount, sampleRate, bitrate);
		}
	}

	@Override
	public void pauseRecording() {
		if (audioRecorder.isRecording()) {
			audioRecorder.pauseRecording();
		}
	}

	@Override
	public void resumeRecording() {
		if (audioRecorder.isPaused()) {
			audioRecorder.startRecording();
		}
	}

	@Override
	public void stopRecording() {
		if (audioRecorder.isRecording()) {
			audioRecorder.stopRecording();
		}
	}

	@Override
	public boolean isRecording() {
		return audioRecorder.isRecording();
	}

	@Override
	public boolean isPaused() {
		return audioRecorder.isPaused();
	}

	@Override
	public void release() {
		audioRecorder.stopRecording();
		appCallbacks.clear();
	}

	private void onRecordingStarted(File output) {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingStarted(output);
			}
		}
	}

	private void onRecordingPaused() {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingPaused();
			}
		}
	}

	private void onRecordingStopped(File file) {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingStopped(file);
			}
		}
	}

	private void onRecordingProgress(long mills, int amp) {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingProgress(mills, amp);
			}
		}
	}

	private void onRecordingError(AppException e) {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onError(e);
			}
		}
	}
}
