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

package com.dimowner.audiorecorder.ui.records;

import android.os.Parcel;
import android.os.Parcelable;

public class ListItem implements Parcelable {

	public final static int ITEM_TYPE_NORMAL = 1;
	public final static int ITEM_TYPE_HEADER = 2;

	private final long id;
	private final int type;
	private final String name;
	private final String description;
	private final String avatar_url;


	public ListItem(long id, int type, String name, String description) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.description = description;
		this.avatar_url = "";
	}

	public static ListItem createHeaderItem() {
		return new ListItem(-1, ListItem.ITEM_TYPE_HEADER, "HEADER", "");
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

	public String getImageUrl() {
		return avatar_url;
	}

	public int getType() {
		return type;
	}

	//----- START Parcelable implementation ----------
	private ListItem(Parcel in) {
		type = in.readInt();
		id = in.readLong();
		String[] data = new String[3];
		in.readStringArray(data);
		name = data[0];
		description = data[1];
		avatar_url = data[2];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(type);
		out.writeLong(id);
		out.writeStringArray(new String[] {name, description, avatar_url});
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
				", name='" + name + '\'' +
				", description='" + description + '\'' +
				", avatar_url='" + avatar_url + '\'' +
				'}';
	}
}
