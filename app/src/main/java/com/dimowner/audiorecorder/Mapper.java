package com.dimowner.audiorecorder;

import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.ui.records.ListItem;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class Mapper {
	private Mapper() {}

	public static ListItem recordToListItem(Record record) {
		return new ListItem(
				record.getId(),
				ListItem.ITEM_TYPE_NORMAL,
				record.getName(),
				"Dur: " + TimeUtils.formatTimeIntervalMinSecMills(record.getDuration()/1000) + " Created: " + TimeUtils.formatTime(record.getCreated()),
				record.getPath());
	}

	public static List<ListItem> recordsToListItems(List<Record> records) {
		List<ListItem> items = new ArrayList<>(records.size());
		for (int i = 0; i < records.size(); i++) {
			items.add(recordToListItem(records.get(i)));
		}
		return items;
	}

}
