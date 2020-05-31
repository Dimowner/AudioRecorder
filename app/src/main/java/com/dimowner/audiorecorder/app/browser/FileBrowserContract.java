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

package com.dimowner.audiorecorder.app.browser;

import android.content.Context;

import com.dimowner.audiorecorder.Contract;
import com.dimowner.audiorecorder.app.info.RecordInfo;

import java.util.List;

public interface FileBrowserContract {

	interface View extends Contract.View {
		void showFileItems(List<RecordInfo> items);
		void showSelectedPrivateDir();
		void showSelectedPublicDir();
		void showRecordInfo(RecordInfo info);
		void onDeletedRecord(String path);
		void onImportedRecord(String path);
		void updatePath(String path);

		void showEmpty();
		void hideEmpty();

		void showRecordProcessing();
		void hideRecordProcessing();

		void showImportStart();
		void hideImportProgress();
	}

	interface UserActionsListener extends Contract.UserActionsListener<FileBrowserContract.View> {
		void selectPrivateDir(Context context);
		void selectPublicDir(Context context);
		void loadFiles(Context context);
		void onRecordInfo(RecordInfo info);
		void deleteRecord(RecordInfo record);
		void importAudioFile(Context context, RecordInfo info);
	}
}
