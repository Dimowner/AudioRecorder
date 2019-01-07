/*
 * Copyright 2018 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder;

/**
 * AppConstants that may be used in multiple classes.
 */
public class AppConstants {

	public static final String APPLICATION_NAME = "AudioRecorder";
	public static final String RECORDS_DIR = "records";
	public static final String RECORD_FILE_EXTENSION = "m4a";
	public static final String EXTENSION_SEPARATOR = ".";
	public static final String BASE_RECORD_NAME = "Record-";
	public static final int MAX_RECORD_NAME_LENGTH = 20;


	/**
	 * Density pixel count per one second of time.
	 */
	public static final int PIXELS_PER_SECOND = 25;


	public static final int TIME_FORMAT_24H = 11;
	public static final int TIME_FORMAT_12H = 12;

	// recording and playback
	public final static int PLAYBACK_SAMPLE_RATE = 44100;
	public final static int RECORD_SAMPLE_RATE = 44100;
	public final static int RECORD_ENCODING_BITRATE = 48000;
	public final static int RECORD_AUDIO_CHANNELS_COUNT = 2;
	public final static int RECORD_MAX_DURATION = 600000; // 10 min
	public final static int VISUALIZATION_INTERVAL = 40;

}
