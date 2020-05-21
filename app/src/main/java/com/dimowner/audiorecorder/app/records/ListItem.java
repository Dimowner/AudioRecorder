/*
 * Copyright 2018 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.records;

import android.os.Parcel;
import android.os.Parcelable;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.Arrays;

public class ListItem implements Parcelable {

	//TODO: make base item with type
	public final static int ITEM_TYPE_NORMAL = 1;
	public final static int ITEM_TYPE_HEADER = 2;
	public final static int ITEM_TYPE_DATE   = 3;
	public final static int ITEM_TYPE_FOOTER = 4;

	private final long id;
	private final int type;
	private final String name;
	private final String format;
	private final String path;
	private final String description;
	private final String durationStr;
	private final long duration;
	private final long size;
	private final int sampleRate;
	private final int channelCount;
	private final int bitrate;
	private final long created;
	private final long added;
	private final String addedTime;
	private final String createTime;
	private boolean bookmarked;
	private final String avatar_url;
	private int[] amps;

	public ListItem(long id, int type, String name, String format, String description, long duration,
						 long size, long created, long added, String path, int sampleRate, int channelCount, int bitrate,
						 boolean bookmarked, int[] amps) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.format = format;
		this.description = description;
		this.created = created;
		this.createTime = convertTimeToStr(created);
		this.added = added;
		this.addedTime = convertTimeToStr(added);
		this.duration = duration;
		this.size = size;
		this.durationStr = convertDurationToStr(duration/1000);
		this.path = path;
		this.sampleRate = sampleRate;
		this.channelCount = channelCount;
		this.bitrate = bitrate;
		this.bookmarked = bookmarked;
		this.avatar_url = "";
		this.amps = amps;
	}

	public static ListItem createHeaderItem() {
		return new ListItem(-1, ListItem.ITEM_TYPE_HEADER, "HEADER", "", "", 0, 0, 0, 0, "", 0, 0, 0, false, null);
	}

	public static ListItem createFooterItem() {
		return new ListItem(-1, ListItem.ITEM_TYPE_FOOTER, "FOOTER", "", "", 0, 0, 0, 0, "", 0, 0, 0, false, null);
	}

	public static ListItem createDateItem(long date) {
		return new ListItem(-1, ListItem.ITEM_TYPE_DATE, "DATE", "", "", 0, 0, 0, date, "", 0, 0, 0, false, null);
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getNameWithExtension() {
		return name + AppConstants.EXTENSION_SEPARATOR + format;
	}

	public String getFormat() {
		return format;
	}

	public String getDescription() {
		return description;
	}

	public long getCreated() {
		return created;
	}

	public String getCreateTimeStr() {
		return createTime;
	}

	public long getAdded() {
		return added;
	}

	public String getAddedTimeStr() {
		return addedTime;
	}

	public String getDurationStr() {
		return durationStr;
	}

	public long getDuration() {
		return duration;
	}

	public long getSize() {
		return size;
	}

	public String getPath() {
		return path;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public int getChannelCount() {
		return channelCount;
	}

	public int getBitrate() {
		return bitrate;
	}

	public String getImageUrl() {
		return avatar_url;
	}

	public int getType() {
		return type;
	}

	public boolean isBookmarked() {
		return bookmarked;
	}

	public void setBookmarked(boolean bookmarked) {
		this.bookmarked = bookmarked;
	}

	public int[] getAmps() {
		return amps;
	}

	private String convertTimeToStr(long time) {
		return TimeUtils.formatDateForNameVariant(time);
//		return TimeUtils.formatTime(time);
	}

	private String convertDurationToStr(long dur) {
		return TimeUtils.formatTimeIntervalHourMinSec2(dur);
	}

	//----- START Parcelable implementation ----------
	private ListItem(Parcel in) {
		int[] ints = new int[4];
		in.readIntArray(ints);
		type = ints[0];
		sampleRate = ints[1];
		channelCount = ints[2];
		bitrate = ints[3];
		long[] longs = new long[5];
		in.readLongArray(longs);
		id = longs[0];
		duration = longs[1];
		size = longs[2];
		created = longs[3];
		added = longs[4];
		String[] data = new String[8];
		in.readStringArray(data);
		name = data[0];
		format = data[1];
		path = data[2];
		description = data[3];
		durationStr = data[4];
		addedTime = data[5];
		createTime = data[6];
		avatar_url = data[7];
		in.readIntArray(amps);
		boolean[] bools = new boolean[1];
		in.readBooleanArray(bools);
		bookmarked = bools[0];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeIntArray(new int[] {type, sampleRate, channelCount, bitrate});
		out.writeLongArray(new long[] {id, duration, size, created, added});
		out.writeStringArray(new String[] {name, format, path, description, durationStr, addedTime, createTime, avatar_url});
		out.writeIntArray(amps);
		out.writeBooleanArray(new boolean[] {bookmarked});
	}

	public static final Parcelable.Creator<ListItem> CREATOR
			= new Parcelable.Creator<ListItem>() {
		public ListItem createFromParcel(Parcel in) {
			return new ListItem(in);
		}

		public ListItem[] newArray(int size) {
			return new ListItem[size];
		}
	};
	//----- END Parcelable implementation ----------

	@Override
	public String toString() {
		return "ListItem{" +
				"id=" + id +
				", type=" + type +
				", name='" + name + '\'' +
				", format='" + format + '\'' +
				", path='" + path + '\'' +
				", description='" + description + '\'' +
				", durationStr='" + durationStr + '\'' +
				", duration=" + duration +
				", size=" + size +
				", sampleRate=" + sampleRate +
				", channelCount=" + channelCount +
				", bitrate=" + bitrate +
				", created=" + created +
				", added=" + added +
				", addedTime='" + addedTime + '\'' +
				", createTime='" + createTime + '\'' +
				", bookmarked=" + bookmarked +
				", avatar_url='" + avatar_url + '\'' +
				", amps=" + Arrays.toString(amps) +
				'}';
	}
}
