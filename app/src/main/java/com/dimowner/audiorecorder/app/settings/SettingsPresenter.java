package com.dimowner.audiorecorder.app.settings;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.List;

public class SettingsPresenter implements SettingsContract.UserActionsListener {

	private SettingsContract.View view;

	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final BackgroundQueue loadingTasks;
	private final Prefs prefs;

	public SettingsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									 BackgroundQueue recordingsTasks, final BackgroundQueue loadingTasks, Prefs prefs) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.recordingsTasks = recordingsTasks;
		this.loadingTasks = loadingTasks;
		this.prefs = prefs;
	}

	@Override
	public void loadSettings() {
		view.showProgress();
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final List<Long> durations = localRepository.getRecordsDurations();
				long totalDuration = 0;
				for (int i = 0; i < durations.size(); i++) {
					totalDuration += durations.get(i);
				}
				final long space = FileUtil.getFree(fileRepository.getRecordingDir());
				final long timeSeconds = space/AppConstants.RECORD_BYTES_PER_SECOND;
				final long finalTotalDuration = totalDuration;
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration / 1000));
						view.showRecordsCount(durations.size());
						view.showAvailableSpace(TimeUtils.formatTimeIntervalHourMinSec(1000*timeSeconds));
						view.hideProgress();
					}
				});
			}
		});
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
