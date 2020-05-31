package com.dimowner.audiorecorder;

import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.app.lostrecords.RecordItem;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.app.records.ListItem;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class Mapper {
	private Mapper() {}

	public static ListItem recordToListItem(Record record) {
		if (record == null) return null;
		return new ListItem(
				record.getId(),
				ListItem.ITEM_TYPE_NORMAL,
				record.getName(),
				record.getFormat(),
				TimeUtils.formatTimeIntervalHourMinSec2(record.getDuration()/1000),
				record.getDuration(),
				record.getSize(),
				record.getCreated(),
				record.getAdded(),
				record.getPath(),
				record.getSampleRate(),
				record.getChannelCount(),
				record.getBitrate(),
				record.isBookmarked(),
				record.getAmps());
	}

	public static List<ListItem> recordsToListItems(List<Record> records) {
		List<ListItem> items = new ArrayList<>(records.size());
		for (int i = 0; i < records.size(); i++) {
			items.add(recordToListItem(records.get(i)));
		}
		return items;
	}

	public static RecordInfo toRecordInfo(ListItem record) {
		if (record == null) return null;
		return new RecordInfo(
				record.getName(),
				record.getFormat(),
				record.getDuration(),
				record.getSize(),
				record.getPath(),
				record.getCreated(),
				record.getSampleRate(),
				record.getChannelCount(),
				record.getBitrate(),
				false
		);
	}

	public static RecordInfo toRecordInfo(RecordItem record) {
		if (record == null) return null;
		return new RecordInfo(
				record.getName(),
				record.getFormat(),
				record.getDuration(),
				record.getSize(),
				record.getPath(),
				record.getCreated(),
				record.getSampleRate(),
				record.getChannelCount(),
				record.getBitrate(),
				false
		);
	}

	public static RecordInfo toRecordInfoInTrash(RecordItem record) {
		if (record == null) return null;
		return new RecordInfo(
				record.getName(),
				record.getFormat(),
				record.getDuration(),
				record.getSize(),
				record.getPath(),
				record.getCreated(),
				record.getSampleRate(),
				record.getChannelCount(),
				record.getBitrate(),
				true
		);
	}

	public static RecordInfo toRecordInfo(Record record) {
		if (record == null) return null;
		return new RecordInfo(
				record.getName(),
				record.getFormat(),
				record.getDuration(),
				record.getSize(),
				record.getPath(),
				record.getCreated(),
				record.getSampleRate(),
				record.getChannelCount(),
				record.getBitrate(),
				false
		);
	}

	public static RecordItem toRecordItem(Record r) {
		if (r == null) return null;
		return new RecordItem(
				r.getId(),
				r.getName(),
				r.getSize(),
				r.getFormat(),
				r.getDuration(),
				r.getPath(),
				r.getCreated(),
				r.getSampleRate(),
				r.getChannelCount(),
				r.getBitrate());
	}

	public static List<RecordItem> toRecordItemList(List<Record> records) {
		ArrayList<RecordItem> list = new ArrayList<>();
		for (Record r : records) {
			RecordItem recordItem = Mapper.toRecordItem(r);
			if (recordItem != null) {
				list.add(recordItem);
			}
		}
		return list;
	}
}
