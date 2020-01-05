/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.audio.player;

import com.dimowner.audiorecorder.exception.AppException;

public interface PlayerContract {

	interface PlayerCallback {
		void onPreparePlay();
		void onStartPlay();
		void onPlayProgress(long mills);
		void onStopPlay();
		void onPausePlay();
		void onSeek(long mills);
		void onError(AppException throwable);
	}

	interface Player {
		void addPlayerCallback(PlayerContract.PlayerCallback callback);
		boolean removePlayerCallback(PlayerContract.PlayerCallback callback);
		void setData(String data);
		void playOrPause();
		void seek(long mills);
		void pause();
		void stop();
		boolean isPlaying();
		boolean isPause();
		long getPauseTime();
		void release();
	}
}
