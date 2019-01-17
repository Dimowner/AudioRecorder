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

package com.dimowner.audiorecorder.app.main;

import android.content.Context;
import android.net.Uri;

import com.dimowner.audiorecorder.Contract;

import java.io.File;
import java.util.List;

public interface MainContract {

	interface View extends Contract.View {

		void keepScreenOn(boolean on);
		void showRecordingStart();
		void showRecordingStop();
		void onRecordingProgress(long mills, int amp);

		void askRecordingNewName(long id, File file);

		void startRecordingService();
		void stopRecordingService();

		void startPlaybackService(String name);
		void stopPlaybackService();

		void showPlayStart();
		void showPlayPause();
		void showPlayStop();
		void onPlayProgress(long mills, int px, int percent);

		void showImportStart();
		void hideImportProgress();

		void showRecordProcessing();
		void hideRecordProcessing();

		void showWaveForm(int[] waveForm, long duration);
		void showDuration(String duration);
		void showName(String name);

		void updateRecordingView(List<Integer> data);
	}

	interface UserActionsListener extends Contract.UserActionsListener<MainContract.View> {

		void startRecording();
		void stopRecording();

		void startPlayback();
		void pausePlayback();
		void seekPlayback(int px);
		void stopPlayback();

		void renameRecord(long id, String name);

		void loadActiveRecord();

		void importAudioFile(Context context, Uri uri);

		void updateRecordingDir(Context context);

		//TODO: Remove this getters
		boolean isStorePublic();

		String getActiveRecordPath();

		String getActiveRecordName();

		int getActiveRecordId();
	}
}
