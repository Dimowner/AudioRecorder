package com.dimowner.audiorecorder.app.settings;

import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.util.AndroidUtils;

import java.util.List;

public class SettingsPresenter implements SettingsContract.UserActionsListener {

	private SettingsContract.View view;

	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final Prefs prefs;

	public SettingsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									BackgroundQueue recordingsTasks, Prefs prefs) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.recordingsTasks = recordingsTasks;
		this.prefs = prefs;
	}

	@Override
	public void loadSettings() {

	}

	@Override
	public void setThemeColor(int colorRes) {

	}

	@Override
	public void setRecordingQuality(int quality) {

	}

	@Override
	public void setRecordingChannelCount(int count) {

	}

	@Override
	public void deleteAllRecords() {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				List<Record> records  = localRepository.getAllRecords();
				for (int i = 0; i < records.size(); i++) {
					fileRepository.deleteRecordFile(records.get(i).getPath());
				}
				boolean b2 = localRepository.deleteAllRecords();
				prefs.setActiveRecord(-1);
				if (b2) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showAllRecordsDeleted();
							}
						}});
				} else {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showFailDeleteAllRecords();
							}
						}});
				}
			}
		});
	}

	@Override
	public void bindView(SettingsContract.View view) {
		this.view = view;
		this.localRepository.open();
	}

	@Override
	public void unbindView() {
		this.localRepository.close();
		this.view = null;
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
	}
}
