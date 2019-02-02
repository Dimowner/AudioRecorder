package com.dimowner.audiorecorder.audio.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.dimowner.audiorecorder.exception.InvalidOutputFile;
import com.dimowner.audiorecorder.exception.RecorderInitException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import timber.log.Timber;

import static com.dimowner.audiorecorder.AppConstants.VISUALIZATION_INTERVAL;

public class WavRecorder implements RecorderContract.Recorder {

	private AudioRecord recorder = null;

	private static final int RECORDER_BPP = 16; //bits per sample
	private static final int RECORDER_SAMPLE_RATE = 44100;

	private File recordFile = null;
	private int bufferSize = 0;
	private Thread recordingThread;

	private boolean isRecording = false;

	private int channelCount = 1;

	private int counter = 0;
	private int chunksCount = 0;
	private int lastVal = 0;

	private static final int FRAMES_PER_VIS_INTERVAL = (int)((VISUALIZATION_INTERVAL/1000f)/(1f/RECORDER_SAMPLE_RATE));

	private RecorderContract.RecorderCallback recorderCallback;

	private static class WavRecorderSingletonHolder {
		private static WavRecorder singleton = new WavRecorder();

		public static WavRecorder getSingleton() {
			return WavRecorderSingletonHolder.singleton;
		}
	}

	public static WavRecorder getInstance() {
		return WavRecorderSingletonHolder.getSingleton();
	}

	@Override
	public void setRecorderCallback(RecorderContract.RecorderCallback callback) {
		recorderCallback = callback;
	}

	@Override
	public void prepare(String outputFile, int channelCount) {
		Timber.v("prepare file: %s", outputFile + " channelCount = " + channelCount);
		this.channelCount = channelCount;
		recordFile = new File(outputFile);
		if (recordFile.exists() && recordFile.isFile()) {
			int channel = channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
			bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE,
					channel,
					AudioFormat.ENCODING_PCM_16BIT);

			recorder = new AudioRecord(
					MediaRecorder.AudioSource.MIC,
					RECORDER_SAMPLE_RATE,
					channel,
					AudioFormat.ENCODING_PCM_16BIT,
					bufferSize
				);

			if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
				if (recorderCallback != null) {
					recorderCallback.onPrepareRecord();
				}
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
	public void startRecording() {
		if (recorder != null && recorder.getState() == AudioRecord.STATE_INITIALIZED) {
			recorder.startRecording();
			isRecording = true;
			recordingThread = new Thread(new Runnable() {
				@Override
				public void run() {
					writeAudioDataToFile();
				}
			}, "AudioRecorder Thread");

			recordingThread.start();
			if (recorderCallback != null) {
				recorderCallback.onStartRecord();
			}
		}
	}

	@Override
	public void pauseRecording() {
		stopRecording();
	}

	@Override
	public void stopRecording() {
		if (null != recorder) {
			isRecording = false;
			if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
				recorder.stop();
			}
			recorder.release();
//			recorder = null;
			recordingThread.interrupt();
			if (recorderCallback != null) {
				recorderCallback.onStopRecord(recordFile);
			}
		}
	}

	@Override
	public boolean isRecording() {
		return isRecording;
	}

	private void writeAudioDataToFile() {
		byte data[] = new byte[bufferSize];

		FileOutputStream fos;
		try {
			fos = new FileOutputStream(recordFile);
		} catch (FileNotFoundException e) {
			Timber.e(e);
			fos = null;
		}
		if (null != fos) {
			recorder.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
				@Override
				public void onMarkerReached(AudioRecord recorder) {
					int cur = (int)(chunksCount/(2*channelCount*RECORDER_SAMPLE_RATE)*1000f);
					counter++;
					recorder.setNotificationMarkerPosition(counter*FRAMES_PER_VIS_INTERVAL);
					Timber.v("onMarkerReached curr = " + cur + " counter = " + counter + " frames = " + counter*FRAMES_PER_VIS_INTERVAL);

					recorderCallback.onRecordProgress(cur, lastVal);
				}

				@Override
				public void onPeriodicNotification(AudioRecord recorder) {
					Timber.v("onPeriodicNotification");
				}
			});
			recorder.setNotificationMarkerPosition(FRAMES_PER_VIS_INTERVAL);
			counter++;
			while (isRecording) {
				chunksCount += recorder.read(data, 0, bufferSize);
				if (AudioRecord.ERROR_INVALID_OPERATION != chunksCount) {
					lastVal = (Math.abs((data[0]<<8)+data[1])
							+ Math.abs((data[2]<<8)+data[3]))/2;
//					Timber.v("Vale = " + lastVal + " 0 = " + data[0] + " 1 = " + data[1] + " 2 = " + data[2] + " 3 = " + data[3]
//							+ " 0+ = " + (data[0]<<8) + " 2+ = " + (data[2]<<2));
					try {
						fos.write(data);
					} catch (IOException e) {
						Timber.e(e);
					}
				}
			}

			try {
				fos.close();
			} catch (IOException e) {
				Timber.e(e);
			}
			setWaveFileHeader(recordFile);
			chunksCount = 0;
			counter = 0;
		}
	}

	private void setWaveFileHeader(File file) {
		long fileSize = file.length();
		long totalSize = fileSize + 36;
		int channels = 2;
		long byteRate = RECORDER_SAMPLE_RATE * channels * 2; //2 byte per 1 sample for 1 channel.

		Timber.v("File size: " + totalSize + " duration mills = " + (file.length()/(4*44.1)) + " chunksTime = " + (chunksCount/(4*44.1)));
		try {
			final RandomAccessFile wavFile = randomAccessFile(file);
			wavFile.seek(0); // to the beginning
			wavFile.write(generateHeader(fileSize, totalSize, RECORDER_SAMPLE_RATE, channels, byteRate));
			wavFile.close();
		} catch (FileNotFoundException e) {
			Timber.e(e);
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
		header[16] = 16; // 4 bytes: size of 'fmt ' chunk
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
		header[32] = (byte) (2 * 16 / 8); // block align
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
}
