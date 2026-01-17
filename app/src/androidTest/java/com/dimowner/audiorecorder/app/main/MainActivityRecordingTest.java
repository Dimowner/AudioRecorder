package com.dimowner.audiorecorder.app.main;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.core.app.ActivityScenario;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.RecordingService;
import com.dimowner.audiorecorder.data.Prefs;

import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityRecordingTest {

	@Rule
	public final GrantPermissionRule permissionRule =
			GrantPermissionRule.grant(
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.WRITE_EXTERNAL_STORAGE,
					Manifest.permission.READ_EXTERNAL_STORAGE);

	private ActivityScenario<MainActivity> scenario;

	@Before
	public void setUp() {
		Context context = ApplicationProvider.getApplicationContext();
		Prefs prefs = ARApplication.getInjector().providePrefs(context);
		prefs.firstRunExecuted();
		prefs.setStoreDirPublic(false);
		scenario = ActivityScenario.launch(MainActivity.class);
	}

	@After
	public void tearDown() {
		if (scenario != null) {
			scenario.close();
		}
		Context context = ApplicationProvider.getApplicationContext();
		context.stopService(new Intent(context, RecordingService.class));
	}

	@Test
	public void recordButtonDoesNotAllocateNewFileWhenPausing() {
		final long initialCounter = readRecordCounter();

		onView(withId(R.id.btn_record)).perform(click());
		waitForIdle();
		assertEquals(initialCounter + 1, readRecordCounter());

		onView(withId(R.id.btn_record)).perform(click()); // Pause
		waitForIdle();
		assertEquals("Second tap should not allocate a new record file",
				initialCounter + 1, readRecordCounter());

		onView(withId(R.id.btn_record_stop)).perform(click());
		waitForIdle();

		onView(withId(R.id.btn_record)).perform(click()); // Start a fresh session
		waitForIdle();
		assertEquals(initialCounter + 2, readRecordCounter());
	}

	private void waitForIdle() {
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
	}

	private long readRecordCounter() {
		Context context = ApplicationProvider.getApplicationContext();
		Prefs prefs = ARApplication.getInjector().providePrefs(context);
		return prefs.getRecordCounter();
	}
}
