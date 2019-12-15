package com.dimowner.audiorecorder.app.lostrecords;

import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.OnRecordsLostListener;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.util.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 14.12.2019.
 * @author Dimowner
 */
public class LostRecordsPresenter implements LostRecordsContract.UserActionsListener {

	private LostRecordsContract.View view;
	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;

	public LostRecordsPresenter(BackgroundQueue recordingsTasks, FileRepository fileRepository, LocalRepository localRepository, Prefs prefs) {
		this.recordingsTasks = recordingsTasks;
		this.fileRepository = fileRepository;
		this.localRepository = localRepository;
		this.prefs = prefs;
	}

	@Override
	public void bindView(LostRecordsContract.View v) {
		this.view = v;

		localRepository.setOnRecordsLostListener(new OnRecordsLostListener() {
			@Override
			public void onLostRecords(List<Record> lostRecords) {
				ArrayList<RecordItem> list = new ArrayList<>();
				for (Record r : lostRecords) {
					list.add(new RecordItem(r.getId(), r.getName(), r.getDuration(), r.getPath()));
				}
				view.showLostRecords(list);
			}
		});
		localRepository.getAllRecords();
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
	public void deleteRecords(final List<RecordItem> list) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				for (RecordItem rec : list) {
					localRepository.deleteRecord(rec.getId());
					fileRepository.deleteRecordFile(rec.getPath());
					if (prefs.getActiveRecord() == rec.getId()) {
						prefs.setActiveRecord(-1);
					}
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							view.clearList();
						}
					}
				});
			}
		});
	}

	@Override
	public void deleteRecord(final RecordItem rec) {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				localRepository.deleteRecord(rec.getId());
				fileRepository.deleteRecordFile(rec.getPath());
				if (prefs.getActiveRecord() == rec.getId()) {
					prefs.setActiveRecord(-1);
				}
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							view.onDeletedRecord(rec.getId());
						}
					}
				});
			}
		});
	}

}
