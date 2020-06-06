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

package com.dimowner.audiorecorder.audio;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.IntArrayList;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import androidx.annotation.NonNull;
import timber.log.Timber;

import static com.dimowner.audiorecorder.AppConstants.SUPPORTED_EXT;

/**
 * Created on 14.03.2020.
 * @author Dimowner
 */
public class AudioDecoder {
	private static final int QUEUE_INPUT_BUFFER_EFFECTIVE = 1; // Most effective and fastest
	private static final int QUEUE_INPUT_BUFFER_SIMPLE = 2;	// Less effective and slower

	private float dpPerSec = AppConstants.SHORT_RECORD_DP_PER_SECOND;

	private int sampleRate;
	private int channelCount;
	private int[] oneFrameAmps;
	private int frameIndex = 0;

	private long duration;
	private static final String TRASH_EXT = "del";

	private IntArrayList gains;

	public interface DecodeListener {
		void onStartDecode(long duration, int channelsCount, int sampleRate);
		void onFinishDecode(int[] data, long duration);
		void onError(Exception exception);
	}

	private AudioDecoder() {
	}

	public static void decode(@NonNull String fileName, @NonNull DecodeListener decodeListener) {
		try {
			File file = new File(fileName);
			if (!file.exists()) {
				throw new java.io.FileNotFoundException(fileName);
			}
			String name = file.getName().toLowerCase();
			String[] components = name.split("\\.");
			if (components.length < 2) {
				throw new IOException();
			}
			if (!Arrays.asList(SUPPORTED_EXT).contains(components[components.length - 1])) {
				throw new IOException();
			}
			AudioDecoder decoder = new AudioDecoder();
			decoder.decodeFile(file, decodeListener, QUEUE_INPUT_BUFFER_EFFECTIVE);
		} catch (Exception e) {
			decodeListener.onError(e);
		}
	}

	private int calculateSamplesPerFrame() {
		return (int)(sampleRate / dpPerSec);
	}

	private void decodeFile(@NonNull final File mInputFile, @NonNull final DecodeListener decodeListener, final int queueType)
			throws IOException, OutOfMemoryError, IllegalStateException {
		gains = new IntArrayList();
		final MediaExtractor extractor = new MediaExtractor();
		MediaFormat format = null;
		int i;

		extractor.setDataSource(mInputFile.getPath());
		int numTracks = extractor.getTrackCount();
		// find and select the first audio track present in the file.
		for (i = 0; i < numTracks; i++) {
			format = extractor.getTrackFormat(i);
			if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
				extractor.selectTrack(i);
				break;
			}
		}

		if (i == numTracks || format == null) {
			throw new IOException("No audio track found in " + mInputFile.toString());
		}
		channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
		sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

		duration = format.getLong(MediaFormat.KEY_DURATION);

		//TODO: Make waveform independent from dpPerSec!!!
		dpPerSec = ARApplication.getDpPerSecond((float) duration/1000000f);
		oneFrameAmps = new int[calculateSamplesPerFrame() * channelCount];

		String mimeType = format.getString(MediaFormat.KEY_MIME);
		//Start decoding
		MediaCodec decoder = MediaCodec.createDecoderByType(mimeType);

		decodeListener.onStartDecode(duration, channelCount, sampleRate);
		decoder.setCallback(new MediaCodec.Callback() {

			private boolean mOutputEOS = false;
			private boolean mInputEOS = false;

			@Override
			public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException exception) {
				Timber.e(exception);
				if (queueType == QUEUE_INPUT_BUFFER_EFFECTIVE) {
					try {
						AudioDecoder decoder = new AudioDecoder();
						decoder.decodeFile(mInputFile, decodeListener, QUEUE_INPUT_BUFFER_SIMPLE);
					} catch (IllegalStateException | IOException | OutOfMemoryError e) {
						decodeListener.onError(exception);
					}
				} else {
					decodeListener.onError(exception);
				}
			}

			@Override
			public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
			}

			@Override
			public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
				if (mOutputEOS | mInputEOS) return;
				try {
					ByteBuffer inputBuffer;
					inputBuffer = codec.getInputBuffer(index);
					if (inputBuffer == null) return;
					long sampleTime = 0;
					int result;
					if (queueType == QUEUE_INPUT_BUFFER_EFFECTIVE) {
						int total = 0;
						boolean advanced = false;
						int maxresult = 0;
						do {
							result = extractor.readSampleData(inputBuffer, total);
							if (result >= 0) {
								total += result;
								sampleTime = extractor.getSampleTime();
								advanced = extractor.advance();
								maxresult = Math.max(maxresult, result);
							}
						} while (result >= 0 && total < maxresult * 5 && advanced && inputBuffer.capacity() - inputBuffer.limit() > maxresult*3);//3 it is just for insurance. When remove it crash happens. it is ok if replace it by 2 number.
						if (advanced) {
							codec.queueInputBuffer(index, 0, total, sampleTime, 0);
						} else {
							codec.queueInputBuffer(index, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							mInputEOS = true;
						}
					} else {
						//If QUEUE_INPUT_BUFFER_EFFECTIVE failed then trying this way.
						result = extractor.readSampleData(inputBuffer, 0);
						if (result >= 0) {
							sampleTime = extractor.getSampleTime();
							codec.queueInputBuffer(index, 0, result, sampleTime, 0);
							extractor.advance();
						} else {
							codec.queueInputBuffer(index, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							mInputEOS = true;
						}
					}
				} catch (IllegalStateException | IllegalArgumentException e) {
					Timber.e(e);
				}
			}

			@Override
			public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
				try {
					ByteBuffer outputBuffer = codec.getOutputBuffer(index);
					if (outputBuffer != null) {
						outputBuffer.rewind();
						outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
						while (outputBuffer.remaining() > 0) {
							oneFrameAmps[frameIndex] = outputBuffer.getShort();
							frameIndex++;
							if (frameIndex >= oneFrameAmps.length - 1) {
								int j;
								int gain, value;
								gain = -1;
								for (j = 0; j < oneFrameAmps.length; j += channelCount) {
									value = 0;
									for (int k = 0; k < channelCount; k++) {
										value += oneFrameAmps[j + k];
									}
									value /= channelCount;
									if (gain < value) {
										gain = value;
									}
								}
								gains.add((int) Math.sqrt(gain));
								frameIndex = 0;
							}
						}
					}

					mOutputEOS |= ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);
					codec.releaseOutputBuffer(index, false);

					if (mOutputEOS) {
						decodeListener.onFinishDecode(gains.getData(), duration);
						codec.stop();
						codec.release();
						extractor.release();
					}
				} catch (IllegalStateException e) {
					Timber.e(e);
				}
			}
		});
		decoder.configure(format, null, null, 0);
		decoder.start();
	}

	public static RecordInfo readRecordInfo(@NonNull final File inputFile)
			throws OutOfMemoryError, IllegalStateException {

		boolean isInTrash = false;
		try {
			if (!inputFile.exists()) {
				throw new java.io.FileNotFoundException(inputFile.getAbsolutePath());
			}
			String name = inputFile.getName().toLowerCase();
			String[] components = name.split("\\.");
			if (components.length < 2) {
				throw new IOException();
			}
			isInTrash = TRASH_EXT.equalsIgnoreCase(components[components.length - 1]);
			if (!isInTrash && !FileUtil.isSupportedExtension(components[components.length - 1])) {
				throw new IOException();
			}

			final MediaExtractor extractor = new MediaExtractor();
			MediaFormat format = null;
			int i;

			extractor.setDataSource(inputFile.getPath());
			int numTracks = extractor.getTrackCount();
			// find and select the first audio track present in the file.
			for (i = 0; i < numTracks; i++) {
				format = extractor.getTrackFormat(i);
				try {
					if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
						extractor.selectTrack(i);
						break;
					}
				} catch (Exception e) {
					Timber.e(e);
				}
			}

			if (i == numTracks || format == null) {
				throw new IOException("No audio track found in " + inputFile.toString());
			}
			int channelCount;
			try {
				channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
			} catch (Exception e) {
				Timber.e(e);
				channelCount = 0;
			}
			int sampleRate;
			try {
				sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
			} catch (Exception e) {
				Timber.e(e);
				sampleRate = 0;
			}
			int bitrate;
			try {
				bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
			} catch (Exception e) {
				Timber.e(e);
				bitrate = 0;
			}

			long duration;
			try {
				duration = format.getLong(MediaFormat.KEY_DURATION);
			} catch (Exception e) {
				Timber.e(e);
				duration = 0;
			}

			String mimeType;
			try {
				mimeType= format.getString(MediaFormat.KEY_MIME);
			} catch (Exception e) {
				Timber.e(e);
				mimeType = "";
			}

			return new RecordInfo(
					FileUtil.removeFileExtension(inputFile.getName()),
					readFileFormat(inputFile, mimeType),
					duration,
					inputFile.length(),
					inputFile.getAbsolutePath(),
					inputFile.lastModified(),
					sampleRate,
					channelCount,
					bitrate,
					isInTrash
			);
		} catch (Exception e) {
			Timber.e(e);
			return new RecordInfo(
					FileUtil.removeFileExtension(inputFile.getName()), "", 0, inputFile.length(),
					inputFile.getAbsolutePath(), inputFile.lastModified(), 0, 0, 0, isInTrash
			);
		}
	}

	private static String readFileFormat(File file, String mime) {
		String name = file.getName().toLowerCase();
		if (name.contains(AppConstants.FORMAT_M4A) || (mime != null && mime.contains("audio") && mime.contains("mp4a"))) {
			return AppConstants.FORMAT_M4A;
		} else if (name.contains(AppConstants.FORMAT_WAV) || (mime != null && mime.contains("audio") && mime.contains("raw"))) {
			return AppConstants.FORMAT_WAV;
		} else if (name.contains(AppConstants.FORMAT_3GP) || (mime != null && mime.contains("audio") && mime.contains("3gpp"))) {
			return AppConstants.FORMAT_3GP;
		} else if (name.contains(AppConstants.FORMAT_3GPP)) {
			return AppConstants.FORMAT_3GPP;
		} else if (name.contains(AppConstants.FORMAT_MP3) || (mime != null && mime.contains("audio") && mime.contains("mpeg"))) {
			return AppConstants.FORMAT_MP3;
		} else if (name.contains(AppConstants.FORMAT_AMR)) {
			return AppConstants.FORMAT_AMR;
		} else if (name.contains(AppConstants.FORMAT_AAC)) {
			return AppConstants.FORMAT_AAC;
		} else if (name.contains(AppConstants.FORMAT_MP4)) {
			return AppConstants.FORMAT_MP4;
		} else if (name.contains(AppConstants.FORMAT_OGG)) {
			return AppConstants.FORMAT_OGG;
		} else if (name.contains(AppConstants.FORMAT_FLAC) || (mime != null && mime.contains("audio") && mime.contains(AppConstants.FORMAT_FLAC))) {
			return AppConstants.FORMAT_FLAC;
		}
		return "";

	}
}
