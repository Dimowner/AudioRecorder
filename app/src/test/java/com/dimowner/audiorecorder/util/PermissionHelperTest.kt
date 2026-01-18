/*
 * Copyright 2026 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.mockk.MockKAnnotations
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PermissionHelperTest {

    @MockK(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Build.VERSION::class)
        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        clearStaticMockk(Build.VERSION::class)
        clearStaticMockk(ContextCompat::class)
    }

    @Test
    fun testPermissionConstants() {
        assertEquals(Manifest.permission.RECORD_AUDIO, PermissionHelper.RECORD_AUDIO)
        assertEquals(Manifest.permission.MODIFY_AUDIO_SETTINGS, PermissionHelper.MODIFY_AUDIO_SETTINGS)
        assertEquals("android.permission.BLUETOOTH_CONNECT", PermissionHelper.BLUETOOTH_CONNECT)
    }

    @Test
    fun testPermissionBluetoothConnect_Api31Plus() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        
        assertNotNull(PermissionHelper.PERMISSION_BLUETOOTH_CONNECT)
        assertEquals("android.permission.BLUETOOTH_CONNECT", PermissionHelper.PERMISSION_BLUETOOTH_CONNECT)
    }

    @Test
    fun testPermissionBluetoothConnect_BelowApi31() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.R
        
        assertNull(PermissionHelper.PERMISSION_BLUETOOTH_CONNECT)
    }

    @Test
    fun testIsBluetoothPermissionRequired_Api31Plus() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        
        assertTrue(PermissionHelper.isBluetoothPermissionRequired())
    }

    @Test
    fun testIsBluetoothPermissionRequired_BelowApi31() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.R
        
        assertFalse(PermissionHelper.isBluetoothPermissionRequired())
    }

    @Test
    fun testHasBluetoothConnectPermission_BelowApi31_ReturnsTrue() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.R
        
        // Below API 31, this should always return true
        assertTrue(PermissionHelper.hasBluetoothConnectPermission(context))
    }

    @Test
    fun testHasBluetoothConnectPermission_Api31Plus_PermissionGranted() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        assertTrue(PermissionHelper.hasBluetoothConnectPermission(context))
    }

    @Test
    fun testHasBluetoothConnectPermission_Api31Plus_PermissionDenied() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_DENIED
        
        assertFalse(PermissionHelper.hasBluetoothConnectPermission(context))
    }

    @Test
    fun testGetBluetoothRecordingPermissionsArray_Api31Plus() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        
        val permissions = PermissionHelper.getBluetoothRecordingPermissionsArray()
        assertEquals(3, permissions.size)
        assertTrue(permissions.contains(PermissionHelper.RECORD_AUDIO))
        assertTrue(permissions.contains(PermissionHelper.MODIFY_AUDIO_SETTINGS))
        assertTrue(permissions.contains(PermissionHelper.BLUETOOTH_CONNECT))
    }

    @Test
    fun testGetBluetoothRecordingPermissionsArray_BelowApi31() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.R
        
        val permissions = PermissionHelper.getBluetoothRecordingPermissionsArray()
        assertEquals(2, permissions.size)
        assertTrue(permissions.contains(PermissionHelper.RECORD_AUDIO))
        assertTrue(permissions.contains(PermissionHelper.MODIFY_AUDIO_SETTINGS))
        assertFalse(permissions.contains(PermissionHelper.BLUETOOTH_CONNECT))
    }

    @Test
    fun testPermissionCheckResult() {
        val result = PermissionHelper.PermissionCheckResult(
            allGranted = true,
            missingPermissions = emptyList()
        )
        assertTrue(result.allGranted)
        assertTrue(result.missingPermissions.isEmpty())

        val result2 = PermissionHelper.PermissionCheckResult(
            allGranted = false,
            missingPermissions = listOf(PermissionHelper.RECORD_AUDIO)
        )
        assertFalse(result2.allGranted)
        assertEquals(1, result2.missingPermissions.size)
        assertEquals(PermissionHelper.RECORD_AUDIO, result2.missingPermissions[0])
    }

    @Test
    fun testHasRecordAudioPermission_Granted() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        assertTrue(PermissionHelper.hasRecordAudioPermission(context))
    }

    @Test
    fun testHasRecordAudioPermission_Denied() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_DENIED
        
        assertFalse(PermissionHelper.hasRecordAudioPermission(context))
    }

    @Test
    fun testHasModifyAudioSettingsPermission_Granted() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        assertTrue(PermissionHelper.hasModifyAudioSettingsPermission(context))
    }

    @Test
    fun testHasModifyAudioSettingsPermission_Denied() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_DENIED
        
        assertFalse(PermissionHelper.hasModifyAudioSettingsPermission(context))
    }

    @Test
    fun testGetMissingBasicRecordingPermissions_AllGranted() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val missing = PermissionHelper.getMissingBasicRecordingPermissions(context)
        assertTrue(missing.isEmpty())
    }

    @Test
    fun testGetMissingBasicRecordingPermissions_RecordAudioMissing() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_DENIED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val missing = PermissionHelper.getMissingBasicRecordingPermissions(context)
        assertEquals(1, missing.size)
        assertTrue(missing.contains(PermissionHelper.RECORD_AUDIO))
    }

    @Test
    fun testGetMissingBasicRecordingPermissions_BothMissing() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_DENIED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_DENIED
        
        val missing = PermissionHelper.getMissingBasicRecordingPermissions(context)
        assertEquals(2, missing.size)
        assertTrue(missing.contains(PermissionHelper.RECORD_AUDIO))
        assertTrue(missing.contains(PermissionHelper.MODIFY_AUDIO_SETTINGS))
    }

    @Test
    fun testGetMissingBluetoothRecordingPermissions_Api31Plus_AllGranted() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val missing = PermissionHelper.getMissingBluetoothRecordingPermissions(context)
        assertTrue(missing.isEmpty())
    }

    @Test
    fun testGetMissingBluetoothRecordingPermissions_Api31Plus_BluetoothMissing() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_DENIED
        
        val missing = PermissionHelper.getMissingBluetoothRecordingPermissions(context)
        assertEquals(1, missing.size)
        assertTrue(missing.contains(PermissionHelper.BLUETOOTH_CONNECT))
    }

    @Test
    fun testGetMissingBluetoothRecordingPermissions_BelowApi31() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.R
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        // Below API 31, BLUETOOTH_CONNECT should not be in the missing permissions
        val missing = PermissionHelper.getMissingBluetoothRecordingPermissions(context)
        assertFalse(missing.contains(PermissionHelper.BLUETOOTH_CONNECT))
    }

    @Test
    fun testCheckBluetoothRecordingPermissions_AllGranted() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val result = PermissionHelper.checkBluetoothRecordingPermissions(context)
        assertNotNull(result)
        assertTrue(result.allGranted)
        assertTrue(result.missingPermissions.isEmpty())
    }

    @Test
    fun testCheckBluetoothRecordingPermissions_SomeMissing() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_DENIED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        val result = PermissionHelper.checkBluetoothRecordingPermissions(context)
        assertNotNull(result)
        assertFalse(result.allGranted)
        assertEquals(1, result.missingPermissions.size)
        assertTrue(result.missingPermissions.contains(PermissionHelper.RECORD_AUDIO))
    }

    @Test
    fun testHasBasicRecordingPermissions_AllGranted() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        assertTrue(PermissionHelper.hasBasicRecordingPermissions(context))
    }

    @Test
    fun testHasBasicRecordingPermissions_OneMissing() {
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_DENIED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        assertFalse(PermissionHelper.hasBasicRecordingPermissions(context))
    }

    @Test
    fun testHasBluetoothRecordingPermissions_AllGranted() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        
        assertTrue(PermissionHelper.hasBluetoothRecordingPermissions(context))
    }

    @Test
    fun testHasBluetoothRecordingPermissions_BluetoothMissing() {
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.S
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.RECORD_AUDIO
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.MODIFY_AUDIO_SETTINGS
            ) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(
                context,
                PermissionHelper.BLUETOOTH_CONNECT
            ) 
        } returns PackageManager.PERMISSION_DENIED
        
        assertFalse(PermissionHelper.hasBluetoothRecordingPermissions(context))
    }
}
