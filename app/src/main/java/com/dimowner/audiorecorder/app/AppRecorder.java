package com.dimowner.audiorecorder.app;

import com.dimowner.audiorecorder.audio.recorder.RecorderContract;

import java.util.List;

public interface AppRecorder {

	void addRecordingCallback(AppRecorderCallback recorderCallback);
	void removeRecordingCallback(AppRecorderCallback recorderCallback);
	void setRecorder(RecorderContract.Recorder recorder);
	void startRecording(String filePath);
	void pauseRecording();
	void stopRecording();
	List<Integer> getRecordingData();
	boolean isRecording();
	boolean isProcessing();
	void release();
}
