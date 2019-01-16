package com.dimowner.audiorecorder.app;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.audio.recorder.RecorderContract;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.util.AndroidUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

public class AppRecorderImpl implements AppRecorder {

	private final RecorderContract.Recorder audioRecorder;
	private final BackgroundQueue recordingsTasks;

	private final BackgroundQueue processingTasks;
	private final LocalRepository localRepository;
	private final RecorderContract.RecorderCallback recorderCallback;
	private final List<AppRecorderCallback> appCallbacks;
	private final Prefs prefs;
	private List<Integer> recordingData;

	private volatile static AppRecorderImpl instance;

	public static AppRecorderImpl getInstance(RecorderContract.Recorder recorder,
															LocalRepository localRep, BackgroundQueue tasks,
															BackgroundQueue processingTasks, Prefs prefs) {
		if (instance == null) {
			synchronized (AppRecorderImpl.class) {
				if (instance == null) {
					instance = new AppRecorderImpl(recorder, localRep, tasks, processingTasks, prefs);
				}
			}
		}
		return instance;
	}

	private AppRecorderImpl(RecorderContract.Recorder recorder,
									LocalRepository localRep, BackgroundQueue tasks,
									final BackgroundQueue processingTasks, Prefs pr) {
		this.audioRecorder = recorder;
		this.localRepository = localRep;
		this.recordingsTasks = tasks;
		this.processingTasks = processingTasks;
		this.prefs = pr;
		this.appCallbacks = new ArrayList<>();
		this.recordingData = new ArrayList<>();

		recorderCallback = new RecorderContract.RecorderCallback() {
			@Override
			public void onPrepareRecord() {
				Timber.v("onPrepareRecord");
				audioRecorder.startRecording();
			}

			@Override
			public void onStartRecord() {
				Timber.v("onStartRecord");
				onRecordingStarted();
			}

			@Override
			public void onPauseRecord() {
				Timber.v("onPauseRecord");
				onRecordingPaused();
			}

			@Override
			public void onRecordProgress(final long mills, final int amplitude) {
				Timber.v("onRecordProgress time = %d, apm = %d, sampleCount = %d", mills, amplitude, recordingData.size());
				onRecordingProgress(mills, amplitude);
				recordingData.add(amplitude);
			}

			@Override
			public void onStopRecord(final File output) {
				Timber.v("onStopRecord file = %s", output.getAbsolutePath());
				onRecordProcessing();
				recordingsTasks.postRunnable(new Runnable() {
					long id = -1;

					@Override
					public void run() {
						try {
							if ((float)recordingData.size()/(float)ARApplication.getLongWaveformSampleCount() > 1) {
								long duration = AndroidUtils.readRecordDuration(output);
								int[] waveForm = convertRecordingData(recordingData, (int) (duration / 1000000f));
								id = localRepository.insertFile(output.getAbsolutePath(), duration, waveForm);
							} else {
								id = localRepository.insertFile(output.getAbsolutePath());
							}
							prefs.setActiveRecord(id);
						} catch (IOException e) {
							Timber.e(e);
						}
						AndroidUtils.runOnUIThread(new Runnable() {
							@Override
							public void run() {
								onRecordingStopped(id, output);
								if ((float)recordingData.size()/(float)ARApplication.getLongWaveformSampleCount() > 1) {
									processingTasks.postRunnable(new Runnable() {
										@Override
										public void run() {
											try {
												localRepository.updateWaveform((int) id);
												AndroidUtils.runOnUIThread(new Runnable() {
													@Override public void run() {
														onRecordFinishProcessing();
													}
												});
											} catch (IOException e) {
												Timber.e(e);
											}
										}
									});
								}
								recordingData.clear();
							}
						});
					}
				});
			}

			@Override
			public void onError(AppException e) {
				Timber.e(e);
				onRecordingError(e);
			}
		};
		audioRecorder.setRecorderCallback(recorderCallback);
	}

	private int[] convertRecordingData(List<Integer> list, int durationSec) {
		if (durationSec > AppConstants.LONG_RECORD_THRESHOLD_SECONDS) {
			int sampleCount = ARApplication.getLongWaveformSampleCount();
			int[] waveForm = new int[sampleCount];
			int scale = (int)((float)list.size()/(float) sampleCount);
			for (int i = 0; i < sampleCount; i+=scale) {
				int val = list.get(i);
//				int val = 0;
//				for (int j = 0; j < scale; j++) {
//					val = list.get(i + j);
//				}
//				val = val/scale;
				waveForm[i] = convertAmp(val);
			}
			Timber.v("WAVE: " + Arrays.toString(waveForm));
			return waveForm;
		} else {
			int[] waveForm = new int[recordingData.size()];
			for (int i = 0; i < list.size(); i++) {
				waveForm[i] = convertAmp(list.get(i));
			}
			return waveForm;
		}
	}

	/**
	 * Convert dB amp value to view amp.
	 */
	private int convertAmp(double amp) {
		return (int)(255*(amp/32767f));
	}

	@Override
	public void addRecordingCallback(AppRecorderCallback callback) {
		appCallbacks.add(callback);
	}

	@Override
	public void removeRecordingCallback(AppRecorderCallback callback) {
		appCallbacks.remove(callback);
	}

	@Override
	public void startRecording(String filePath) {
		if (!audioRecorder.isRecording()) {
			audioRecorder.prepare(filePath, prefs.getRecordChannelCount());
		}
	}

	@Override
	public void pauseRecording() {
		if (audioRecorder.isRecording()) {
			audioRecorder.pauseRecording();
		}
	}

	@Override
	public void stopRecording() {
		if (audioRecorder.isRecording()) {
			audioRecorder.stopRecording();
		}
	}

	@Override
	public List<Integer> getRecordingData() {
		return recordingData;
	}

	@Override
	public boolean isRecording() {
		return audioRecorder.isRecording();
	}

	@Override
	public void release() {
		recordingData.clear();
		audioRecorder.stopRecording();
		appCallbacks.clear();
	}

	private void onRecordingStarted() {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingStarted();
			}
		}
	}

	private void onRecordingPaused() {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingPaused();
			}
		}
	}

	private void onRecordProcessing() {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordProcessing();
			}
		}
	}

	private void onRecordFinishProcessing() {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordFinishProcessing();
			}
		}
	}

	private void onRecordingStopped(long id, File file) {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingStopped(id, file);
			}
		}
	}

	private void onRecordingProgress(long mills, int amp) {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onRecordingProgress(mills, amp);
			}
		}
	}

	private void onRecordingError(AppException e) {
		if (!appCallbacks.isEmpty()) {
			for (int i = 0; i < appCallbacks.size(); i++) {
				appCallbacks.get(i).onError(e);
			}
		}
	}
}
