/*
 * Copyright 2019 Dmitriy Ponomarenko
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
package com.dimowner.audiorecorder.app.trash;

import com.dimowner.audiorecorder.Contract;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.app.lostrecords.RecordItem;

import java.util.List;

public interface TrashContract {

	interface View extends Contract.View {
		void showRecords(List<RecordItem> items);
		void showRecordInfo(RecordInfo info);
		void recordDeleted(int resId);
		void recordRestored(int resId);
		void allRecordsRemoved();
		void showEmpty();
		void hideEmpty();
	}

	interface UserActionsListener extends Contract.UserActionsListener<TrashContract.View> {
		void onRecordInfo(RecordInfo info);
		void deleteRecordFromTrash(final int id, final String path);
		void deleteAllRecordsFromTrash();
		void restoreRecordFromTrash(final int id);
	}
}
