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

package com.dimowner.audiorecorder.ui.records;

import com.dimowner.audiorecorder.Mapper;
import com.dimowner.audiorecorder.audio.player.AudioPlayerContract;
import com.dimowner.audiorecorder.audio.player.AudioPlayer;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.util.AndroidUtils;
import java.util.List;
import timber.log.Timber;

public class RecordsPresenter implements RecordsContract.UserActionsListener {

	private RecordsContract.View view;
	private final AudioPlayer audioPlayer;
	private final LocalRepository localRepository;


	public RecordsPresenter(final LocalRepository localRepository) {
		this.localRepository = localRepository;
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
//				view.showDuration(TimeUtils.formatTimeIntervalMinSecMills(songDuration));
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
	public void bindView(RecordsContract.View view) {
		this.view = view;
		this.localRepository.open();
	}

	@Override
	public void unbindView() {
		this.view = null;
		this.localRepository.close();

		audioPlayer.stopListenActions();
		audioPlayer.stop();
	}

	@Override
	public void playClicked() {
		Timber.v("playClicked");
		audioPlayer.playOrPause();
	}

	@Override
	public void pauseClicked() {

	}

	@Override
	public void stopClicked() {

	}

	@Override
	public void playNextClicked() {

	}

	@Override
	public void playPrevClicked() {

	}

	@Override
	public void loadRecords() {
		view.showProgress();
		new Thread("LoadRecords") {
			@Override
			public void run() {
				final List<Record> recordList = localRepository.getAllRecords();
				if (recordList.size() > 0) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							view.showRecords(Mapper.recordsToListItems(recordList));
							view.hideProgress();
						}
					});
				} else {
					view.hideProgress();
				}
			}
		}.start();
	}
}
