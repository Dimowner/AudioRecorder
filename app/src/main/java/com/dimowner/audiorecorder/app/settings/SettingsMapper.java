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

package com.dimowner.audiorecorder.app.settings;

import com.dimowner.audiorecorder.AppConstants;

/**
 * Created on 26.04.2020.
 * @author Dimowner
 */
public class SettingsMapper {

	public final static String SAMPLE_RATE_8000 = "8000";
	public final static String SAMPLE_RATE_16000 = "16000";
	public final static String SAMPLE_RATE_22050 = "22050";
	public final static String SAMPLE_RATE_32000 = "32000";
	public final static String SAMPLE_RATE_44100 = "44100";
	public final static String SAMPLE_RATE_48000 = "48000";

//	public final static String BITRATE_24000 = "24000";
	public final static String BITRATE_48000 = "48000";
	public final static String BITRATE_96000 = "96000";
	public final static String BITRATE_128000 = "128000";
	public final static String BITRATE_192000 = "192000";
	public final static String BITRATE_256000 = "256000";

	public final static String CHANNEL_COUNT_STEREO = "stereo";
	public final static String CHANNEL_COUNT_MONO = "mono";

	public static String positionToColorKey(int position) {
		switch (position) {
			case 0:
				return AppConstants.THEME_BLUE_GREY;
			case 1:
				return AppConstants.THEME_BLACK;
			case 2:
				return AppConstants.THEME_TEAL;
			case 3:
				return AppConstants.THEME_BLUE;
			case 4:
				return AppConstants.THEME_PURPLE;
			case 5:
				return AppConstants.THEME_PINK;
			case 6:
				return AppConstants.THEME_ORANGE;
			case 7:
				return AppConstants.THEME_RED;
			case 8:
				return AppConstants.THEME_BROWN;
			default:
				return AppConstants.DEFAULT_THEME_COLOR;
		}
	}

	public static int colorKeyToPosition(String colorKey) {
		switch (colorKey) {
			default:
			case AppConstants.THEME_BLUE_GREY:
				return 0;
			case AppConstants.THEME_BLACK:
				return 1;
			case AppConstants.THEME_TEAL:
				return 2;
			case AppConstants.THEME_BLUE:
				return 3;
			case AppConstants.THEME_PURPLE:
				return 4;
			case AppConstants.THEME_PINK:
				return 5;
			case AppConstants.THEME_ORANGE:
				return 6;
			case AppConstants.THEME_RED:
				return 7;
			case AppConstants.THEME_BROWN:
				return 8;
		}
	}

	public static int namingFormatToPosition(String namingFormat) {
		switch (namingFormat) {
			case AppConstants.NAME_FORMAT_TIMESTAMP:
				return 2;
			case AppConstants.NAME_FORMAT_DATE:
			case AppConstants.NAME_FORMAT_DATE_SIMPLE:
				return 1;
//			case AppConstants.NAME_FORMAT_DATE:
//				return 1;
			case AppConstants.NAME_FORMAT_RECORD:
			default:
				return 0;
		}
	}

	public static String positionToNamingFormat(int position) {
		switch (position) {
			case 0:
				return AppConstants.NAME_FORMAT_RECORD;
//			case 1:
//				return AppConstants.NAME_FORMAT_DATE;
			case 1:
				return AppConstants.NAME_FORMAT_DATE_SIMPLE;
			case 2:
				return AppConstants.NAME_FORMAT_TIMESTAMP;
			default:
				return AppConstants.DEFAULT_NAME_FORMAT;
		}
	}

	public static int keyToSampleRate(String sampleRateKey) {
		switch (sampleRateKey) {
			case SAMPLE_RATE_8000:
				return AppConstants.RECORD_SAMPLE_RATE_8000;
			case SAMPLE_RATE_16000:
				return AppConstants.RECORD_SAMPLE_RATE_16000;
			case SAMPLE_RATE_22050:
				return AppConstants.RECORD_SAMPLE_RATE_22050;
			case SAMPLE_RATE_32000:
				return AppConstants.RECORD_SAMPLE_RATE_32000;
			case SAMPLE_RATE_44100:
				return AppConstants.RECORD_SAMPLE_RATE_44100;
			case SAMPLE_RATE_48000:
				return AppConstants.RECORD_SAMPLE_RATE_48000;
			default:
				return AppConstants.DEFAULT_RECORD_SAMPLE_RATE;
		}
	}

	public static String sampleRateToKey(int sampleRate) {
		switch (sampleRate) {
			case AppConstants.RECORD_SAMPLE_RATE_8000:
				return SAMPLE_RATE_8000;
			case AppConstants.RECORD_SAMPLE_RATE_16000:
				return SAMPLE_RATE_16000;
			case AppConstants.RECORD_SAMPLE_RATE_22050:
				return SAMPLE_RATE_22050;
			case AppConstants.RECORD_SAMPLE_RATE_32000:
				return SAMPLE_RATE_32000;
			case AppConstants.RECORD_SAMPLE_RATE_44100:
			default:
				return SAMPLE_RATE_44100;
			case AppConstants.RECORD_SAMPLE_RATE_48000:
				return SAMPLE_RATE_48000;
		}
	}

	public static int keyToBitrate(String bitrateKey) {
		switch (bitrateKey) {
//			case BITRATE_24000:
//				return AppConstants.RECORD_ENCODING_BITRATE_24000;
			case BITRATE_48000:
				return AppConstants.RECORD_ENCODING_BITRATE_48000;
			case BITRATE_96000:
				return AppConstants.RECORD_ENCODING_BITRATE_96000;
			case BITRATE_128000:
				return AppConstants.RECORD_ENCODING_BITRATE_128000;
			case BITRATE_192000:
				return AppConstants.RECORD_ENCODING_BITRATE_192000;
			case BITRATE_256000:
				return AppConstants.RECORD_ENCODING_BITRATE_256000;
			default:
				return AppConstants.DEFAULT_RECORD_ENCODING_BITRATE;
		}
	}

	public static String bitrateToKey(int bitrate) {
		switch (bitrate) {
//			case AppConstants.RECORD_ENCODING_BITRATE_24000:
//				return BITRATE_24000;
			case AppConstants.RECORD_ENCODING_BITRATE_48000:
				return BITRATE_48000;
			case AppConstants.RECORD_ENCODING_BITRATE_96000:
				return BITRATE_96000;
			case AppConstants.RECORD_ENCODING_BITRATE_128000:
			default:
				return BITRATE_128000;
			case AppConstants.RECORD_ENCODING_BITRATE_192000:
				return BITRATE_192000;
			case AppConstants.RECORD_ENCODING_BITRATE_256000:
				return BITRATE_256000;
		}
	}

	public static int keyToChannelCount(String key) {
		switch (key) {
			case CHANNEL_COUNT_MONO:
				return AppConstants.RECORD_AUDIO_MONO;
			case CHANNEL_COUNT_STEREO:
				return AppConstants.RECORD_AUDIO_STEREO;
			default:
				return AppConstants.DEFAULT_CHANNEL_COUNT;
		}
	}

	public static String channelCountToKey(int count) {
		switch (count) {
			case AppConstants.RECORD_AUDIO_MONO:
				return CHANNEL_COUNT_MONO;
			case AppConstants.RECORD_AUDIO_STEREO:
			default:
				return CHANNEL_COUNT_STEREO;
		}
	}

	public static String convertSampleRateToString(String[] sampleRates, String[] sampleRateKeys, int sampleRate) {
		String key = sampleRateToKey(sampleRate);
		for (int i = 0; i < sampleRateKeys.length; i++) {
			if (key.equals(sampleRateKeys[i])) {
				return sampleRates[i];
			}
		}
		return "";
	}

	public static String convertBitratesToString(String[] bitrates, String[] bitrateKeys, int bitrate) {
		String key = bitrateToKey(bitrate);
		for (int i = 0; i < bitrateKeys.length; i++) {
			if (key.equals(bitrateKeys[i])) {
				return bitrates[i];
			}
		}
		return "";
	}

	public static String convertChannelsToString(String[] channels, String[] channelKeys, int count) {
		String key = channelCountToKey(count);
		for (int i = 0; i < channelKeys.length; i++) {
			if (key.equals(channelKeys[i])) {
				return channels[i];
			}
		}
		return "";
	}

	public static String convertFormatsToString(String[] formats, String[] formatKeys, String formatKey) {
		for (int i = 0; i < formatKeys.length; i++) {
			if (formatKey.equals(formatKeys[i])) {
				return formats[i];
			}
		}
		return "";
	}
}
