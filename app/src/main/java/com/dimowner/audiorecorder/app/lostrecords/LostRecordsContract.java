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
package com.dimowner.audiorecorder.app.lostrecords;

import com.dimowner.audiorecorder.Contract;
import com.dimowner.audiorecorder.app.info.RecordInfo;

import java.util.List;

public interface LostRecordsContract {

	interface View extends Contract.View {
		void showLostRecords(List<RecordItem> items);
		void showRecordInfo(RecordInfo info);
		void onDeletedRecord(int id);
		void showEmpty();
		void hideEmpty();
	}

	interface UserActionsListener extends Contract.UserActionsListener<LostRecordsContract.View> {
		void onRecordInfo(RecordInfo info);
		void deleteRecord(RecordItem record);
		void deleteRecords(List<RecordItem> list);
	}
}
