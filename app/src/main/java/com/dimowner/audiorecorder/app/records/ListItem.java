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

import com.dimowner.audiorecorder.util.TimeUtils;

public class ListItem implements Parcelable {

	//TODO: make base item with type
	public final static int ITEM_TYPE_NORMAL = 1;
	public final static int ITEM_TYPE_HEADER = 2;
	public final static int ITEM_TYPE_DATE   = 3;
	public final static int ITEM_TYPE_FOOTER = 4;

	private final long id;
	private final int type;
	private final String name;
	private final String path;
	private final String description;
	private final String durationStr;
	private final long duration;
	private final long created;
	private final long added;
	private final String addedTime;
	private final String createTime;
	private boolean bookmarked;
	private final String avatar_url;
	private int[] amps;


	public ListItem(long id, int type, String name, String description, long duration,
						 long created, long added, String path, boolean bookmarked, int[] amps) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.description = description;
		this.created = created;
		this.createTime = convertTimeToStr(created);
		this.added = added;
		this.addedTime = convertTimeToStr(added);
		this.duration = duration;
		this.durationStr = convertDurationToStr(duration);
		this.path = path;
		this.bookmarked = bookmarked;
		this.avatar_url = "";
		this.amps = amps;
	}

	public static ListItem createHeaderItem() {
		return new ListItem(-1, ListItem.ITEM_TYPE_HEADER, "HEADER", "", 0, 0, 0, "", false, null);
	}

	public static ListItem createFooterItem() {
		return new ListItem(-1, ListItem.ITEM_TYPE_FOOTER, "FOOTER", "", 0, 0, 0, "", false, null);
	}

	public static ListItem createDateItem(long date) {
		return new ListItem(-1, ListItem.ITEM_TYPE_DATE, "DATE", "", 0, 0, date, "", false, null);
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
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

	public String getPath() {
		return path;
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
		return TimeUtils.formatTime(time);
	}

	private String convertDurationToStr(long dur) {
		return TimeUtils.formatTimeIntervalHourMinSec2(dur);
	}

	//----- START Parcelable implementation ----------
	private ListItem(Parcel in) {
		type = in.readInt();
		long[] longs = new long[4];
		in.readLongArray(longs);
		id = longs[0];
		created = longs[1];
		duration = longs[2];
		added = longs[3];
		createTime = convertTimeToStr(created);
		durationStr = convertDurationToStr(duration);
		addedTime = convertTimeToStr(added);
		String[] data = new String[4];
		in.readStringArray(data);
		name = data[0];
		description = data[1];
		path = data[2];
		avatar_url = data[3];
		in.readIntArray(amps);
		boolean[] bools = new boolean[1];
		in.readBooleanArray(bools);
		bookmarked = bools[0];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(type);
		out.writeLongArray(new long[] {id, created, duration, added});
		out.writeStringArray(new String[] {name, description, path, avatar_url});
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
				", path='" + path + '\'' +
				", description='" + description + '\'' +
				", durationStr='" + durationStr + '\'' +
				", duration=" + duration +
				", created=" + created +
				", createTime='" + createTime + '\'' +
				", added=" + added +
				", addedTime='" + addedTime + '\'' +
				", bookmarked='" + bookmarked + '\'' +
				", avatar_url='" + avatar_url + '\'' +
				", amps length='" + amps.length+ '\'' +
				'}';
	}
}
