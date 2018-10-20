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

package com.dimowner.audiorecorder.ui;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.dimowner.audiorecorder.audio.player.AudioPlayerContract;
import com.dimowner.audiorecorder.audio.player.AudioPlayer;
import com.dimowner.audiorecorder.audio.recorder.AudioRecorder;
import com.dimowner.audiorecorder.audio.recorder.AudioRecorderContract;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.exception.CantCreateFileException;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.audio.SoundFile;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.io.IOException;

import timber.log.Timber;

public class MainPresenter implements MainContract.UserActionsListener {

	private MainContract.View view;

	private final AudioRecorder audioRecorder;
	private final AudioPlayer audioPlayer;
	private final FileRepository fileRepository;
	private final Prefs prefs;
	private long songDuration = 0;


	public MainPresenter(final Prefs prefs, final FileRepository fileRepository) {
		this.prefs = prefs;
		this.fileRepository = fileRepository;
		this.audioRecorder = new AudioRecorder(new AudioRecorderContract.RecorderActions() {
			@Override
			public void onPrepareRecord() {
				Timber.v("onPrepareRecord");
				audioRecorder.startRecording();
			}

			@Override
			public void onStartRecord() {
				Timber.v("onStartRecord");
				view.showRecordingStart();
			}

			@Override
			public void onRecordProgress(long mills, int amplitude) {
				Timber.v("onRecordProgress time = %d, apm = %d", mills, amplitude);
				view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(mills));
			}

			@Override
			public void onStopRecord(File output) {
				Timber.v("onStopRecord file = %s", output.getAbsolutePath());
				String prevRecord = prefs.getLastRecordedFile();
				boolean b = fileRepository.deleteRecordFile(prevRecord);
				Timber.v("Deletion is " + b);
				prefs.saveLastRecordedFile(output.getAbsolutePath());
				view.showRecordingStop();
			}

			@Override
			public void onError(Exception throwable) {
				Timber.e(throwable);
				view.showError(ErrorParser.parseException(throwable));
			}
		});

		this.audioPlayer = new AudioPlayer(new AudioPlayerContract.PlayerActions() {

			@Override
			public void onPreparePlay() {
				Timber.d("onPreparePlay");
				// Scroll to start position for the first playback time.
//				scrollToPlaybackPosition(0);
			}

			@Override
			public void onStartPlay() {
				Timber.d("onStartPlay");
//				runOnUiThread(() -> playbackView.setStartPosition(SimpleWaveformView.NO_PROGRESS));
				view.showPlayStart();
			}

			@Override
			public void onPlayProgress(long mills) {
				Timber.v("onPlayProgress: " + mills);
				view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills));
			}

			@Override
			public void onStopPlay() {
				view.showPlayStop();
				Timber.d("onStopPlay");
				view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration));
			}

			@Override
			public void onPausePlay() {
				view.showPlayPause();
				Timber.d("onPausePlay");
			}

			@Override
			public void onSeek(long mills) {
				Timber.d("onSeek = " + mills);
			}

			@Override
			public void onError(Throwable throwable) {
				Timber.d("onPlayError");
			}
		});
	}

	@Override
	public void bindView(MainContract.View view) {
		this.view = view;
	}

	@Override
	public void unbindView() {
		this.view = null;

		audioPlayer.stopListenActions();
		audioPlayer.stop();

		audioRecorder.stopRecording();
	}

	@Override
	public void recordingClicked() {
		Timber.v("recordingClicked");
		if (audioRecorder.isRecording()) {
			audioRecorder.stopRecording();
		} else {
			try {
				audioRecorder.prepare(fileRepository.provideRecordFile().getAbsolutePath());
			} catch (CantCreateFileException e) {
				view.showError(ErrorParser.parseException(e));
			}
		}
	}

	@Override
	public void playClicked() {
		Timber.v("playClicked");
		audioPlayer.playOrPause();
	}

	@Override
	public void deleteAll() {
		Timber.v("deleteAll");
		prefs.clearLastRecordFile();
		loadLastRecord(null);
	}

	@Override
	public void loadLastRecord(final Context context) {
		final String lastFile = prefs.getLastRecordedFile();
		if (lastFile != null && !lastFile.isEmpty()) {
			view.showProgress();
			new Thread("SoundLoading") {
				@Override
				public void run() {
					try {
						final SoundFile soundFile = SoundFile.create(lastFile);
//						final int duration = readRecordingTrackDuration(context, lastFile);
						songDuration = readRecordingTrackDuration(context, lastFile);
						Timber.v("Duration = " + songDuration);

						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								audioPlayer.setData(lastFile);
								view.showSoundFile(soundFile);
								view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration));
								view.hideProgress();
							}
						});
					} catch (IOException e) {
						Timber.e(e);
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								view.showError("Couldn't load audio file!");
								view.hideProgress();
							}
						});
					}
				}
			}.start();
		} else {
			view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(0));
			view.showSoundFile(null);
		}
	}

	private int readRecordingTrackDuration(Context context, String path) {
		Uri uri = Uri.parse(path);
		MediaMetadataRetriever mmr = new MediaMetadataRetriever();
		mmr.setDataSource(context, uri);
		String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
		return Integer.parseInt(durationStr);
	}
}
