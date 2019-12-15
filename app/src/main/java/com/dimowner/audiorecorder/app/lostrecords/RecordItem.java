package com.dimowner.audiorecorder.app.lostrecords;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created on 14.12.2019.
 * @author Dimowner
 */
public class RecordItem implements Parcelable {
	private int id;
	private String name;
	private long duration;
	private String path;

	public RecordItem(int id, String name, long duration, String path) {
		this.id = id;
		this.name = name;
		this.duration = duration;
		this.path = path;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getDuration() {
		return duration;
	}

	public String getPath() {
		return path;
	}

	//----- START Parcelable implementation ----------
	private RecordItem(Parcel in) {
		id = in.readInt();
		duration = in.readLong();
		String[] data = new String[2];
		in.readStringArray(data);
		name = data[0];
		path = data[1];
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(id);
		out.writeLong(duration);
		out.writeStringArray(new String[] {name, path});
	}

	public static final Parcelable.Creator<RecordItem> CREATOR
			= new Parcelable.Creator<RecordItem>() {
		public RecordItem createFromParcel(Parcel in) {
			return new RecordItem(in);
		}

		public RecordItem[] newArray(int size) {
			return new RecordItem[size];
		}
	};
	//----- END Parcelable implementation ----------
}
