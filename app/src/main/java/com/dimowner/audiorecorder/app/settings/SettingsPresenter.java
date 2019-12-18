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
		if (view != null) {
			view.showProgress();
		}
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final List<Long> durations = localRepository.getRecordsDurations();
				long totalDuration = 0;
				for (int i = 0; i < durations.size(); i++) {
					totalDuration += durations.get(i);
				}
				final long finalTotalDuration = totalDuration;
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						if (view != null) {
							view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration / 1000));
							view.showRecordsCount(durations.size());
							updateAvailableSpace();
							view.hideProgress();
						}
					}
				});
			}
		});
		if (view != null) {
			view.showStoreInPublicDir(prefs.isStoreDirPublic());
			view.showAskToRenameAfterRecordingStop(prefs.isAskToRenameAfterStopRecording());
			view.showRecordInStereo(prefs.getRecordChannelCount() == AppConstants.RECORD_AUDIO_STEREO);
			view.showKeepScreenOn(prefs.isKeepScreenOn());
			int format = prefs.getFormat();
			view.showRecordingFormat(format);
			if (format == AppConstants.RECORDING_FORMAT_WAV) {
				view.hideBitrateSelector();
			} else {
				view.showBitrateSelector();
			}
			view.showNamingFormat(prefs.getNamingFormat());
		}


		int pos;
		switch (prefs.getSampleRate()) {
			case AppConstants.RECORD_SAMPLE_RATE_8000:
				pos = 0;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_16000:
				pos = 1;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_32000:
				pos = 2;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_48000:
				pos = 4;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_44100:
			default:
				pos = 3;
		}
		if (view != null) {
			view.showRecordingSampleRate(pos);
		}

		switch (prefs.getBitrate()) {
			case AppConstants.RECORD_ENCODING_BITRATE_24000:
				pos = 0;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_48000:
			default:
				pos = 1;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_96000:
				pos = 2;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_128000:
				pos = 3;
				break;
			case AppConstants.RECORD_ENCODING_BITRATE_192000:
				pos = 4;
				break;
		}
		if (view != null) {
			view.showRecordingBitrate(pos);
		}
	}

	@Override
	public void storeInPublicDir(boolean b) {
		prefs.setStoreDirPublic(b);
	}

	@Override
	public void keepScreenOn(boolean keep) {
		prefs.setKeepScreenOn(keep);
	}

	@Override
	public void askToRenameAfterRecordingStop(boolean b) {
		prefs.setAskToRenameAfterStopRecording(b);
	}

	@Override
	public void recordInStereo(boolean stereo) {
		prefs.setRecordInStereo(stereo);
		updateAvailableSpace();
	}

	@Override
	public void setRecordingBitrate(int pos) {
		int rate;
		switch (pos) {
			case 0:
				rate = AppConstants.RECORD_ENCODING_BITRATE_24000;
				break;
			case 1:
			default:
				rate = AppConstants.RECORD_ENCODING_BITRATE_48000;
				break;
			case 2:
				rate = AppConstants.RECORD_ENCODING_BITRATE_96000;
				break;
			case 3:
				rate = AppConstants.RECORD_ENCODING_BITRATE_128000;
				break;
			case 4:
				rate = AppConstants.RECORD_ENCODING_BITRATE_192000;
				break;
		}
		prefs.setBitrate(rate);
		updateAvailableSpace();
	}

	@Override
	public void setRecordingFormat(int format) {
		prefs.setFormat(format);
		updateAvailableSpace();
		if (view != null) {
			if (format == AppConstants.RECORDING_FORMAT_WAV) {
				view.hideBitrateSelector();
			} else {
				view.showBitrateSelector();
			}
		}
	}

	@Override
	public void setNamingFormat(int format) {
		prefs.setNamingFormat(format);
	}

	@Override
	public void setSampleRate(int pos) {
		int rate;
		switch (pos) {
			case 0:
				rate = AppConstants.RECORD_SAMPLE_RATE_8000;
				break;
			case 1:
				rate = AppConstants.RECORD_SAMPLE_RATE_16000;
				break;
			case 2:
				rate = AppConstants.RECORD_SAMPLE_RATE_32000;
				break;
			case 4:
				rate = AppConstants.RECORD_SAMPLE_RATE_48000;
				break;
			case 3:
			default:
				rate = AppConstants.RECORD_SAMPLE_RATE_44100;
		}
		prefs.setSampleRate(rate);
		updateAvailableSpace();
	}

	@Override
	public void deleteAllRecords() {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
//				List<Record> records  = localRepository.getAllRecords();
//				for (int i = 0; i < records.size(); i++) {
//					fileRepository.deleteRecordFile(records.get(i).getPath());
//				}
//				boolean b2 = localRepository.deleteAllRecords();
//				prefs.setActiveRecord(-1);
//				if (b2) {
//					AndroidUtils.runOnUIThread(new Runnable() {
//						@Override
//						public void run() {
//							if (view != null) {
//								view.showAllRecordsDeleted();
//							}
//						}});
//				} else {
//					AndroidUtils.runOnUIThread(new Runnable() {
//						@Override
//						public void run() {
//							if (view != null) {
//								view.showFailDeleteAllRecords();
//							}
//						}});
//				}
			}
		});
	}

	@Override
	public void bindView(SettingsContract.View view) {
		this.view = view;
	}

	@Override
	public void unbindView() {
		if (view != null) {
			this.view = null;
		}
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
	}

	private void updateAvailableSpace() {
		final long space = FileUtil.getFree(fileRepository.getRecordingDir());
		final long time = spaceToTimeSecs(space, prefs.getFormat(), prefs.getSampleRate(), prefs.getRecordChannelCount());
		if (view != null) {
			view.showAvailableSpace(TimeUtils.formatTimeIntervalHourMinSec(time));
		}
	}

	private long spaceToTimeSecs(long spaceBytes, int format, int sampleRate, int channels) {
		if (format == AppConstants.RECORDING_FORMAT_M4A) {
			return 1000 * (spaceBytes/(AppConstants.RECORD_ENCODING_BITRATE_48000 /8));
		} else if (format == AppConstants.RECORDING_FORMAT_WAV) {
			return 1000 * (spaceBytes/(sampleRate * channels * 2));
		} else {
			return 0;
		}
	}
}
