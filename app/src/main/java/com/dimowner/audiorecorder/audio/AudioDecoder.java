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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import timber.log.Timber;

/**
 * Created on 14.03.2020.
 * @author Dimowner
 */
public class AudioDecoder {

	private float dpPerSec = AppConstants.SHORT_RECORD_DP_PER_SECOND;

	private int sampleRate;
	private int channelCount;
	private int[] oneFrameAmps;
	private int frameIndex = 0;

	private long duration;
	private static final String[] SUPPORTED_EXT = new String[]{"mp3", "wav", "3gpp", "3gp", "amr", "aac", "m4a", "mp4", "ogg"};

	private List<Integer> gains;

	public interface DecodeListener {
		void onStartDecode(long duration, int channelsCount, int sampleRate);
		void onFinishDecode(List<Integer> data, long duration);
		void onError(MediaCodec.CodecException exception);
	}

	private AudioDecoder() {
	}

	public static void decode(String fileName, DecodeListener decodeListener)
			throws IOException, OutOfMemoryError, IllegalStateException, FileNotFoundException {
		// First check that the file exists and that its extension is supported.
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
		decoder.decodeFile(file, decodeListener);
	}

	private int calculateSamplesPerFrame() {
		return (int)(sampleRate / dpPerSec);
	}

	private void decodeFile(@NonNull final File mInputFile, @NonNull final DecodeListener decodeListener) throws IOException, OutOfMemoryError, IllegalStateException {
		gains = new ArrayList<>();
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

			@Override
			public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException exception) {
				Timber.e(exception);
				decodeListener.onError(exception);
			}

			@Override
			public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
			}

			@Override
			public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
				if (mOutputEOS) return;

				ByteBuffer inputBuffer;
				inputBuffer = codec.getInputBuffer(index);
				if (inputBuffer == null) return;
				long sampleTime;
				int result;
				result = extractor.readSampleData(inputBuffer, 0);
				if (result >= 0) {
					sampleTime = extractor.getSampleTime();
					codec.queueInputBuffer(index, 0, result, sampleTime, 0);
					extractor.advance();
				} else  {
					codec.queueInputBuffer(index, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
				}
			}

			@Override
			public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
				ByteBuffer outputBuffer = codec.getOutputBuffer(index);
				if (outputBuffer != null) {
					outputBuffer.rewind();
					outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
					//TODO: ShortBuffer get rid of usage. Just user source ByteBuffer.
					ShortBuffer decodedSamples = outputBuffer.asShortBuffer();
					while (decodedSamples.remaining() > 0) {
						for (int i = 0; i < decodedSamples.remaining(); i++) {
							if (decodedSamples.remaining() > 0) {
								oneFrameAmps[frameIndex] = decodedSamples.get();
								frameIndex++;
							}
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
				}

				mOutputEOS |= ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);
				codec.releaseOutputBuffer(index, false);

				if (mOutputEOS) {
					decodeListener.onFinishDecode(gains, duration);
					codec.stop();
					codec.release();
					extractor.release();
				}
			}
		});
		decoder.configure(format, null, null, 0);
		decoder.start();
	}
}
