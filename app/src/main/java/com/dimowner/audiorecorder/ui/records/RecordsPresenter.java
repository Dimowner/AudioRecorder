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

import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.Mapper;
import com.dimowner.audiorecorder.audio.player.PlayerContract;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.util.AndroidUtils;
import java.util.List;
import timber.log.Timber;

public class RecordsPresenter implements RecordsContract.UserActionsListener {

	private RecordsContract.View view;
	private final PlayerContract.Player audioPlayer;
	private PlayerContract.PlayerCallback playerCallback;
	private final BackgroundQueue loadingTasks;
	private final LocalRepository localRepository;
	private final Prefs prefs;


	public RecordsPresenter(final LocalRepository localRepository, BackgroundQueue loadingTasks,
				PlayerContract.Player player, Prefs prefs) {
		this.localRepository = localRepository;
		this.loadingTasks = loadingTasks;
		this.audioPlayer = player;
		this.playerCallback = null;
		this.prefs = prefs;
	}

	@Override
	public void bindView(RecordsContract.View v) {
		this.view = v;
		this.localRepository.open();
		if (playerCallback == null) {
			this.playerCallback = new PlayerContract.PlayerCallback() {

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
					if (view != null) {
						Timber.v("onPlayProgress: " + mills);
						view.onPlayProgress(mills, AndroidUtils.convertMillsToPx(mills));
					}
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
			};
		}
		audioPlayer.addPlayerCallback(playerCallback);
	}

	@Override
	public void unbindView() {
		this.localRepository.close();
		audioPlayer.removePlayerCallback(playerCallback);
		this.view = null;
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
//		audioPlayer.release();
//		loadingTasks.close();
	}

	@Override
	public void startPlayback(String path) {
		Timber.v("startPlayback path: " + path);
		audioPlayer.setData(path);
		audioPlayer.playOrPause();
	}

	@Override
	public void pausePlayback() {
		if (audioPlayer.isPlaying()) {
			audioPlayer.pause();
		}
	}

	@Override
	public void stopPlayback() {
		audioPlayer.stop();
	}

	@Override
	public void playNext() {

	}

	@Override
	public void playPrev() {

	}

	@Override
	public void loadRecords() {
		view.showProgress();
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final List<Record> recordList = localRepository.getAllRecords();
				if (recordList.size() > 0) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() {
							if (view != null) {
								view.showRecords(Mapper.recordsToListItems(recordList));
								view.hideProgress();
							}
						}});
				} else {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override public void run() {
							if (view !=null) { view.hideProgress(); }
						}});
				}
			}
		});
	}

	@Override
	public void setActiveRecord(long id) {
		prefs.setActiveRecord(id);
	}
}
