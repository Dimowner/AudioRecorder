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

package com.dimowner.audiorecorder.app.trash;

import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.Mapper;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.app.lostrecords.RecordItem;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.exception.ErrorParser;
import com.dimowner.audiorecorder.exception.FailedToRestoreRecord;
import com.dimowner.audiorecorder.util.AndroidUtils;

import java.util.List;

/**
 * Created on 15.12.2019.
 * @author Dimowner
 */
public class TrashPresenter implements TrashContract.UserActionsListener {

	private TrashContract.View view;
	private final BackgroundQueue loadingTasks;
	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;

	public TrashPresenter(BackgroundQueue loadingTasks, BackgroundQueue recordingsTasks,
								 FileRepository fileRepository, LocalRepository localRepository) {
		this.loadingTasks = loadingTasks;
		this.recordingsTasks = recordingsTasks;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
	}

	@Override
	public void bindView(final TrashContract.View v) {
		this.view = v;

		loadingTasks.postRunnable(new Runnable() {
			@Override public void run() {
				final List<RecordItem> list = Mapper.toRecordItemList(localRepository.getTrashRecords());
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							if (list.isEmpty()) {
								view.showEmpty();
							} else {
								view.showRecords(list);
								view.hideEmpty();
							}
						}
					}
				});
			}
		});
	}

	@Override
	public void unbindView() {
		this.view = null;
	}

	@Override
	public void clear() {
		unbindView();
	}

	@Override
	public void onRecordInfo(RecordInfo info) {
		if (view != null) {
			view.showRecordInfo(info);
		}
	}

	@Override
	public void deleteRecordFromTrash(final int id, final String path) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (fileRepository.deleteRecordFile(path)) {
					removeFromTrash(id);
				} else if (fileRepository.deleteRecordFile(path)) { //Try to delete again.
					removeFromTrash(id);
				} else {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showMessage(R.string.error_failed_to_delete);
							}
						}
					});
				}
			}
		});
	}

	private void removeFromTrash(final int id) {
		localRepository.removeFromTrash(id);
		AndroidUtils.runOnUIThread(new Runnable() {
			@Override
			public void run() {
				if (view != null) {
					view.showMessage(R.string.record_deleted_successfully);
					view.recordDeleted(id);
				}
			}
		});
	}

	@Override
	public void deleteAllRecordsFromTrash() {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				List<Record> records  = localRepository.getTrashRecords();
				for (int i = 0; i < records.size(); i++) {
					fileRepository.deleteRecordFile(records.get(i).getPath());
				}
				localRepository.emptyTrash();
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							view.showMessage(R.string.all_records_deleted_successfully);
							view.allRecordsRemoved();
						}
					}
				});
			}
		});
	}

	@Override
	public void restoreRecordFromTrash(final int id) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				try {
					localRepository.restoreFromTrash(id);
				} catch (final FailedToRestoreRecord e) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showMessage(ErrorParser.parseException(e));
							}
						}
					});
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							view.showMessage(R.string.record_restored_successfully);
							view.recordRestored(id);
						}
					}
				});
			}
		});
	}
}
