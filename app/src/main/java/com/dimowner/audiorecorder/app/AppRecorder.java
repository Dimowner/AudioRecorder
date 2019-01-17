package com.dimowner.audiorecorder.app;

import java.util.List;

public interface AppRecorder {

	void addRecordingCallback(AppRecorderCallback recorderCallback);
	void removeRecordingCallback(AppRecorderCallback recorderCallback);
	void startRecording(String filePath);
	void pauseRecording();
	void stopRecording();
	List<Integer> getRecordingData();
	boolean isRecording();
	boolean isProcessing();
	void release();
}
