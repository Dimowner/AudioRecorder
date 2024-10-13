/*
 * Copyright 2019 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.audio.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.exception.InvalidOutputFile;
import com.dimowner.audiorecorder.exception.RecorderInitException;
import com.dimowner.audiorecorder.exception.RecordingException;
import com.dimowner.audiorecorder.util.AndroidUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import timber.log.Timber;
import static com.dimowner.audiorecorder.AppConstants.RECORDING_VISUALIZATION_INTERVAL;
import androidx.annotation.RequiresPermission;

public class WavRecorder implements RecorderContract.Recorder {

	private AudioRecord recorder = null;

	private static final int RECORDER_BPP = 16; //bits per sample

	private File recordFile = null;
	private int bufferSize = 0;
	private long updateTime = 0;
	private long durationMills = 0;

	private Thread recordingThread;

	private final AtomicBoolean isRecording = new AtomicBoolean(false);
	private final AtomicBoolean isPaused = new AtomicBoolean(false);
	private final Handler handler = new Handler();

	private int channelCount = 1;

	/** Value for recording used visualisation. */
	private int lastVal = 0;

	private int sampleRate = AppConstants.RECORD_SAMPLE_RATE_44100;

	private RecorderContract.RecorderCallback recorderCallback;

	private static class WavRecorderSingletonHolder {
		private static final WavRecorder singleton = new WavRecorder();

		public static WavRecorder getSingleton() {
			return WavRecorderSingletonHolder.singleton;
		}
	}

	public static WavRecorder getInstance() {
		return WavRecorderSingletonHolder.getSingleton();
	}

	private WavRecorder() { }

	@Override
	public void setRecorderCallback(RecorderContract.RecorderCallback callback) {
		recorderCallback = callback;
	}

	@Override
	@RequiresPermission(value = "android.permission.RECORD_AUDIO")
	public void startRecording(String outputFile, int channelCount, int sampleRate, int bitrate) {
		this.sampleRate = sampleRate;
//		this.framesPerVisInterval = (int)((VISUALIZATION_INTERVAL/1000f)/(1f/sampleRate));
		this.channelCount = channelCount;
		recordFile = new File(outputFile);
		if (recordFile.exists() && recordFile.isFile()) {
			int channel = channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
			try {
				bufferSize = AudioRecord.getMinBufferSize(sampleRate,
						channel,
						AudioFormat.ENCODING_PCM_16BIT);
				if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
					bufferSize = AudioRecord.getMinBufferSize(sampleRate,
							channel,
							AudioFormat.ENCODING_PCM_16BIT);
				}
				recorder = new AudioRecord(
						MediaRecorder.AudioSource.MIC,
						sampleRate,
						channel,
						AudioFormat.ENCODING_PCM_16BIT,
						bufferSize
				);
			} catch (IllegalArgumentException e) {
				Timber.e(e, "sampleRate = " + sampleRate + " channel = " + channel + " bufferSize = " + bufferSize);
				if (recorder != null) {
					recorder.release();
				}
			}
			if (recorder != null && recorder.getState() == AudioRecord.STATE_INITIALIZED) {
				recorder.startRecording();
				updateTime = System.currentTimeMillis();
				isRecording.set(true);
				recordingThread = new Thread(this::writeAudioDataToFile, "AudioRecorder Thread");

				recordingThread.start();
				scheduleRecordingTimeUpdate();
				if (recorderCallback != null) {
					recorderCallback.onStartRecord(recordFile);
				}
				isPaused.set(false);
			} else {
				Timber.e("prepare() failed");
				if (recorderCallback != null) {
					recorderCallback.onError(new RecorderInitException());
				}
			}
		} else {
			if (recorderCallback != null) {
				recorderCallback.onError(new InvalidOutputFile());
			}
		}
	}

	@Override
	public void resumeRecording() {
		if (recorder != null && recorder.getState() == AudioRecord.STATE_INITIALIZED) {
			if (isPaused.get()) {
				updateTime = System.currentTimeMillis();
				scheduleRecordingTimeUpdate();
				recorder.startRecording();
				if (recorderCallback != null) {
					recorderCallback.onResumeRecord();
				}
				isPaused.set(false);
			}
		}
	}

	@Override
	public void pauseRecording() {
		if (isRecording.get()) {
			recorder.stop();
			durationMills += System.currentTimeMillis() - updateTime;
			pauseRecordingTimer();

			isPaused.set(true);
			if (recorderCallback != null) {
				recorderCallback.onPauseRecord();
			}
		}
	}

	@Override
	public void stopRecording() {
		if (recorder != null) {
			isRecording.set(false);
			isPaused.set(false);
			stopRecordingTimer();
			if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
				try {
					recorder.stop();
				} catch (IllegalStateException e) {
					Timber.e(e, "stopRecording() problems");
				}
			}
			durationMills = 0;
			recorder.release();
			recordingThread.interrupt();
			if (recorderCallback != null) {
				recorderCallback.onStopRecord(recordFile);
			}
		}
	}

	@Override
	public boolean isRecording() {
		return isRecording.get();
	}

	@Override
	public boolean isPaused() {
		return isPaused.get();
	}

	private void writeAudioDataToFile() {
		byte[] data = new byte[bufferSize];
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(recordFile);
		} catch (FileNotFoundException e) {
			Timber.e(e);
			fos = null;
		}
		if (null != fos) {
			writeEmptyHeader(fos);
			int chunksCount = 0;
			ByteBuffer shortBuffer = ByteBuffer.allocate(2);
			shortBuffer.order(ByteOrder.LITTLE_ENDIAN);
			//TODO: Disable loop while pause.
			while (isRecording.get()) {
				if (!isPaused.get()) {
					chunksCount += recorder.read(data, 0, bufferSize);
					if (AudioRecord.ERROR_INVALID_OPERATION != chunksCount) {
						long sum = 0;
						for (int i = 0; i < bufferSize; i+=2) {
							//TODO: find a better way to covert bytes into shorts.
							shortBuffer.put(data[i]);
							shortBuffer.put(data[i+1]);
							sum += Math.abs(shortBuffer.getShort(0));
							shortBuffer.clear();
						}
						lastVal = (int)(sum/(bufferSize/16));
						try {
							fos.write(data);
						} catch (IOException e) {
							Timber.e(e);
							AndroidUtils.runOnUIThread(() -> {
								recorderCallback.onError(new RecordingException());
								stopRecording();
							});
						}
					}
				}
			}

			try {
				fos.flush();
				fos.close();
			} catch (IOException e) {
				Timber.e(e);
			}
			setWaveFileHeader(recordFile, channelCount);
		}
	}

	private void setWaveFileHeader(File file, int channels) {
		long fileSize = file.length() - 44;
		long totalSize = fileSize + 36;
		long byteRate = sampleRate * channels * (RECORDER_BPP/8); //2 byte per 1 sample for 1 channel.

		try {
			final RandomAccessFile wavFile = randomAccessFile(file);
			wavFile.seek(0); // to the beginning
			wavFile.write(generateHeader(fileSize, totalSize, sampleRate, channels, byteRate));
			wavFile.close();
		} catch (FileNotFoundException e) {
			Timber.e(e);
		} catch (IOException e) {
			Timber.e(e);
		}
	}

	private void writeEmptyHeader(FileOutputStream fos) {
		try {
			byte[] header = new byte[44];
			fos.write(header);
			fos.flush();
		} catch (IOException e) {
			Timber.e(e);
		}
	}

	private RandomAccessFile randomAccessFile(File file) {
		RandomAccessFile randomAccessFile;
		try {
			randomAccessFile = new RandomAccessFile(file, "rw");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		return randomAccessFile;
	}

	private byte[] generateHeader(
			long totalAudioLen, long totalDataLen, long longSampleRate, int channels,
			long byteRate) {

		byte[] header = new byte[44];

		header[0] = 'R'; // RIFF/WAVE header
		header[1] = 'I';
		header[2] = 'F';
		header[3] = 'F';
		header[4] = (byte) (totalDataLen & 0xff);
		header[5] = (byte) ((totalDataLen >> 8) & 0xff);
		header[6] = (byte) ((totalDataLen >> 16) & 0xff);
		header[7] = (byte) ((totalDataLen >> 24) & 0xff);
		header[8] = 'W';
		header[9] = 'A';
		header[10] = 'V';
		header[11] = 'E';
		header[12] = 'f'; // 'fmt ' chunk
		header[13] = 'm';
		header[14] = 't';
		header[15] = ' ';
		header[16] = 16; //16 for PCM. 4 bytes: size of 'fmt ' chunk
		header[17] = 0;
		header[18] = 0;
		header[19] = 0;
		header[20] = 1; // format = 1
		header[21] = 0;
		header[22] = (byte) channels;
		header[23] = 0;
		header[24] = (byte) (longSampleRate & 0xff);
		header[25] = (byte) ((longSampleRate >> 8) & 0xff);
		header[26] = (byte) ((longSampleRate >> 16) & 0xff);
		header[27] = (byte) ((longSampleRate >> 24) & 0xff);
		header[28] = (byte) (byteRate & 0xff);
		header[29] = (byte) ((byteRate >> 8) & 0xff);
		header[30] = (byte) ((byteRate >> 16) & 0xff);
		header[31] = (byte) ((byteRate >> 24) & 0xff);
		header[32] = (byte) (channels * (RECORDER_BPP/8)); // block align
		header[33] = 0;
		header[34] = RECORDER_BPP; // bits per sample
		header[35] = 0;
		header[36] = 'd';
		header[37] = 'a';
		header[38] = 't';
		header[39] = 'a';
		header[40] = (byte) (totalAudioLen & 0xff);
		header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
		header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
		header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
		return header;
	}

	private void scheduleRecordingTimeUpdate() {
		handler.postDelayed(() -> {
			if (recorderCallback != null && recorder != null) {
				long curTime = System.currentTimeMillis();
				durationMills += curTime - updateTime;
				updateTime = curTime;
				recorderCallback.onRecordProgress(durationMills, lastVal);
				scheduleRecordingTimeUpdate();
			}
		}, RECORDING_VISUALIZATION_INTERVAL);
	}

	private void stopRecordingTimer() {
		handler.removeCallbacksAndMessages(null);
		updateTime = 0;
	}

	private void pauseRecordingTimer() {
		handler.removeCallbacksAndMessages(null);
		updateTime = 0;
	}
}
