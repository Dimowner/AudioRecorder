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

import com.dimowner.audiorecorder.Contract;
import com.dimowner.audiorecorder.data.database.Record;

import java.util.List;

public interface RecordsContract {

	interface View extends Contract.View {

		void showPlayStart();

		void showPlayPause();

		void showPlayStop();

		void showNextRecord();

		void showPrevRecord();

		void showWaveForm(int[] waveForm);

		void showDuration(String duration);

		void onPlayProgress(long mills, int px);

		void showRecords(List<ListItem> records);
	}

	interface UserActionsListener extends Contract.UserActionsListener<RecordsContract.View> {

		void playClicked();

		void pauseClicked();

		void stopClicked();

		void playNextClicked();

		void playPrevClicked();

		void loadRecords();
	}
}