/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.cts;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_SAME;
import static android.media.AudioManager.FLAG_ALLOW_RINGER_MODES;
import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_IN_COMMUNICATION;
import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.USE_DEFAULT_STREAM_TYPE;
import static android.media.AudioManager.VIBRATE_SETTING_OFF;
import static android.media.AudioManager.VIBRATE_SETTING_ON;
import static android.media.AudioManager.VIBRATE_SETTING_ONLY_SILENT;
import static android.media.AudioManager.VIBRATE_TYPE_NOTIFICATION;
import static android.media.AudioManager.VIBRATE_TYPE_RINGER;
import static android.provider.Settings.System.SOUND_EFFECTS_ENABLED;

import com.android.cts.media.R;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.view.SoundEffectConstants;

import java.util.TreeMap;

public class AudioManagerTest extends AndroidTestCase {

    private final static int MP3_TO_PLAY = R.raw.testmp3;
    private final static long TIME_TO_PLAY = 2000;
    private AudioManager mAudioManager;
    private boolean mHasVibrator;
    private boolean mUseMasterVolume;
    private boolean mUseFixedVolume;
    private int[] mMasterVolumeRamp;
    private TreeMap<Integer, Integer> mMasterVolumeMap = new TreeMap<Integer, Integer>();
    private boolean mIsTelevision;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = (vibrator != null) && vibrator.hasVibrator();
        mUseMasterVolume = mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_useMasterVolume", "bool", "android"));
        mUseFixedVolume = mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_useFixedVolume", "bool", "android"));
        mMasterVolumeRamp = mContext.getResources().getIntArray(
                Resources.getSystem().getIdentifier("config_masterVolumeRamp", "array", "android"));
        assertTrue((mMasterVolumeRamp.length > 0) && (mMasterVolumeRamp.length % 2 == 0));
        for (int i = 0; i < mMasterVolumeRamp.length; i+=2) {
            mMasterVolumeMap.put(mMasterVolumeRamp[i], mMasterVolumeRamp[i+1]);
        }
        PackageManager packageManager = mContext.getPackageManager();
        mIsTelevision = packageManager != null
                && (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION));
    }

    public void testMicrophoneMute() throws Exception {
        mAudioManager.setMicrophoneMute(true);
        assertTrue(mAudioManager.isMicrophoneMute());
        mAudioManager.setMicrophoneMute(false);
        assertFalse(mAudioManager.isMicrophoneMute());
    }

    public void testSoundEffects() throws Exception {
        // set relative setting
        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        Settings.System.putInt(mContext.getContentResolver(), SOUND_EFFECTS_ENABLED, 1);

        // should hear sound after loadSoundEffects() called.
        mAudioManager.loadSoundEffects();
        Thread.sleep(TIME_TO_PLAY);
        float volume = 13;
        mAudioManager.playSoundEffect(SoundEffectConstants.CLICK);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT);

        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, volume);

        // won't hear sound after unloadSoundEffects() called();
        mAudioManager.unloadSoundEffects();
        mAudioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT);

        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT, volume);
        mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, volume);
    }

    public void testMusicActive() throws Exception {
        MediaPlayer mp = MediaPlayer.create(mContext, MP3_TO_PLAY);
        assertNotNull(mp);
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.start();
        Thread.sleep(TIME_TO_PLAY);
        assertTrue(mAudioManager.isMusicActive());
        Thread.sleep(TIME_TO_PLAY);
        mp.stop();
        mp.release();
        Thread.sleep(TIME_TO_PLAY);
        assertFalse(mAudioManager.isMusicActive());
    }

    public void testAccessMode() throws Exception {
        mAudioManager.setMode(MODE_RINGTONE);
        assertEquals(MODE_RINGTONE, mAudioManager.getMode());
        mAudioManager.setMode(MODE_IN_COMMUNICATION);
        assertEquals(MODE_IN_COMMUNICATION, mAudioManager.getMode());
        mAudioManager.setMode(MODE_NORMAL);
        assertEquals(MODE_NORMAL, mAudioManager.getMode());
    }

    @SuppressWarnings("deprecation")
    public void testRouting() throws Exception {
        // setBluetoothA2dpOn is a no-op, and getRouting should always return -1
        // AudioManager.MODE_CURRENT
        boolean oldA2DP = mAudioManager.isBluetoothA2dpOn();
        mAudioManager.setBluetoothA2dpOn(true);
        assertEquals(oldA2DP , mAudioManager.isBluetoothA2dpOn());
        mAudioManager.setBluetoothA2dpOn(false);
        assertEquals(oldA2DP , mAudioManager.isBluetoothA2dpOn());

        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_RINGTONE));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_NORMAL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_CALL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_COMMUNICATION));

        mAudioManager.setBluetoothScoOn(true);
        assertTrue(mAudioManager.isBluetoothScoOn());
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_RINGTONE));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_NORMAL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_CALL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_COMMUNICATION));

        mAudioManager.setBluetoothScoOn(false);
        assertFalse(mAudioManager.isBluetoothScoOn());
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_RINGTONE));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_NORMAL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_CALL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_COMMUNICATION));

        mAudioManager.setSpeakerphoneOn(true);
        assertTrue(mAudioManager.isSpeakerphoneOn());
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_CALL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_COMMUNICATION));
        mAudioManager.setSpeakerphoneOn(false);
        assertFalse(mAudioManager.isSpeakerphoneOn());
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_CALL));
        assertEquals(AudioManager.MODE_CURRENT, mAudioManager.getRouting(MODE_IN_COMMUNICATION));
    }

    public void testVibrateNotification() throws Exception {
        if (mUseFixedVolume || !mHasVibrator) {
            return;
        }
        // VIBRATE_SETTING_ON
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        // VIBRATE_SETTING_OFF
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        // VIBRATE_SETTING_ONLY_SILENT
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_NOTIFICATION));

        // VIBRATE_TYPE_NOTIFICATION
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF, mAudioManager
                .getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_NOTIFICATION, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_NOTIFICATION));
    }

    public void testVibrateRinger() throws Exception {
        if (mUseFixedVolume || !mHasVibrator) {
            return;
        }
        // VIBRATE_TYPE_RINGER
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        // VIBRATE_SETTING_OFF
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF, mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        // Note: as of Froyo, if VIBRATE_TYPE_RINGER is set to OFF, it will
        // not vibrate, even in RINGER_MODE_VIBRATE. This allows users to
        // disable the vibration for incoming calls only.
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        // VIBRATE_SETTING_ONLY_SILENT
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        assertFalse(mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                mAudioManager.getRingerMode());
        assertEquals(mHasVibrator, mAudioManager.shouldVibrate(VIBRATE_TYPE_RINGER));

        // VIBRATE_TYPE_NOTIFICATION
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ON);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ON : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_OFF);
        assertEquals(VIBRATE_SETTING_OFF, mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
        mAudioManager.setVibrateSetting(VIBRATE_TYPE_RINGER, VIBRATE_SETTING_ONLY_SILENT);
        assertEquals(mHasVibrator ? VIBRATE_SETTING_ONLY_SILENT : VIBRATE_SETTING_OFF,
                mAudioManager.getVibrateSetting(VIBRATE_TYPE_RINGER));
    }

    public void testAccessRingMode() throws Exception {
        mAudioManager.setRingerMode(RINGER_MODE_NORMAL);
        assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());

        mAudioManager.setRingerMode(RINGER_MODE_SILENT);
        // AudioService#setRingerMode() has:
        // if (isTelevision) return;
        if (mUseFixedVolume || mIsTelevision) {
            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
        } else {
            assertEquals(RINGER_MODE_SILENT, mAudioManager.getRingerMode());
        }

        mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
        if (mUseFixedVolume || mIsTelevision) {
            assertEquals(RINGER_MODE_NORMAL, mAudioManager.getRingerMode());
        } else {
            assertEquals(mHasVibrator ? RINGER_MODE_VIBRATE : RINGER_MODE_SILENT,
                    mAudioManager.getRingerMode());
        }
    }

    public void testVolume() throws Exception {
        int volume, volumeDelta;
        int[] streams = { AudioManager.STREAM_ALARM,
                          AudioManager.STREAM_MUSIC,
                          AudioManager.STREAM_VOICE_CALL,
                          AudioManager.STREAM_RING };

        mAudioManager.adjustVolume(ADJUST_RAISE, 0);
        mAudioManager.adjustSuggestedStreamVolume(
                ADJUST_LOWER, USE_DEFAULT_STREAM_TYPE, 0);
        int maxMusicVolume = mAudioManager.getStreamMaxVolume(STREAM_MUSIC);

        for (int i = 0; i < streams.length; i++) {
            // set ringer mode to back normal to not interfere with volume tests
            mAudioManager.setRingerMode(RINGER_MODE_NORMAL);

            int maxVolume = mAudioManager.getStreamMaxVolume(streams[i]);

            mAudioManager.setStreamVolume(streams[i], 1, 0);
            if (mUseFixedVolume) {
                assertEquals(maxVolume, mAudioManager.getStreamVolume(streams[i]));
                continue;
            }
            assertEquals(1, mAudioManager.getStreamVolume(streams[i]));

            if (streams[i] == AudioManager.STREAM_MUSIC && mAudioManager.isWiredHeadsetOn()) {
                // due to new regulations, music sent over a wired headset may be volume limited
                // until the user explicitly increases the limit, so we can't rely on being able
                // to set the volume to getStreamMaxVolume(). Instead, determine the current limit
                // by increasing the volume until it won't go any higher, then use that volume as
                // the maximum for the purposes of this test
                int curvol = 0;
                int prevvol = 0;
                do {
                    prevvol = curvol;
                    mAudioManager.adjustStreamVolume(streams[i], ADJUST_RAISE, 0);
                    curvol = mAudioManager.getStreamVolume(streams[i]);
                } while (curvol != prevvol);
                maxVolume = maxMusicVolume = curvol;
            }
            mAudioManager.setStreamVolume(streams[i], maxVolume, 0);
            mAudioManager.adjustStreamVolume(streams[i], ADJUST_RAISE, 0);
            assertEquals(maxVolume, mAudioManager.getStreamVolume(streams[i]));

            volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(streams[i]));
            mAudioManager.adjustSuggestedStreamVolume(ADJUST_LOWER, streams[i], 0);
            assertEquals(maxVolume - volumeDelta, mAudioManager.getStreamVolume(streams[i]));

            // volume lower
            mAudioManager.setStreamVolume(streams[i], maxVolume, 0);
            volume = mAudioManager.getStreamVolume(streams[i]);
            while (volume > 0) {
                volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(streams[i]));
                mAudioManager.adjustStreamVolume(streams[i], ADJUST_LOWER, 0);
                assertEquals(Math.max(0, volume - volumeDelta),
                             mAudioManager.getStreamVolume(streams[i]));
                volume = mAudioManager.getStreamVolume(streams[i]);
            }

            mAudioManager.adjustStreamVolume(streams[i], ADJUST_SAME, 0);

            // volume raise
            mAudioManager.setStreamVolume(streams[i], 1, 0);
            volume = mAudioManager.getStreamVolume(streams[i]);
            while (volume < maxVolume) {
                volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(streams[i]));
                mAudioManager.adjustStreamVolume(streams[i], ADJUST_RAISE, 0);
                assertEquals(Math.min(volume + volumeDelta, maxVolume),
                             mAudioManager.getStreamVolume(streams[i]));
                volume = mAudioManager.getStreamVolume(streams[i]);
            }

            // volume same
            mAudioManager.setStreamVolume(streams[i], maxVolume, 0);
            for (int k = 0; k < maxVolume; k++) {
                mAudioManager.adjustStreamVolume(streams[i], ADJUST_SAME, 0);
                assertEquals(maxVolume, mAudioManager.getStreamVolume(streams[i]));
            }

            mAudioManager.setStreamVolume(streams[i], maxVolume, 0);
        }

        if (mUseFixedVolume) {
            return;
        }

        // adjust volume
        mAudioManager.adjustVolume(ADJUST_RAISE, 0);

        MediaPlayer mp = MediaPlayer.create(mContext, MP3_TO_PLAY);
        assertNotNull(mp);
        mp.setAudioStreamType(STREAM_MUSIC);
        mp.setLooping(true);
        mp.start();
        Thread.sleep(TIME_TO_PLAY);
        assertTrue(mAudioManager.isMusicActive());

        // adjust volume as ADJUST_SAME
        for (int k = 0; k < maxMusicVolume; k++) {
            mAudioManager.adjustVolume(ADJUST_SAME, 0);
            assertEquals(maxMusicVolume, mAudioManager.getStreamVolume(STREAM_MUSIC));
        }

        // adjust volume as ADJUST_RAISE
        mAudioManager.setStreamVolume(STREAM_MUSIC, 0, 0);
        volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(STREAM_MUSIC));
        mAudioManager.adjustVolume(ADJUST_RAISE, 0);
        assertEquals(Math.min(volumeDelta, maxMusicVolume),
                     mAudioManager.getStreamVolume(STREAM_MUSIC));

        // adjust volume as ADJUST_LOWER
        mAudioManager.setStreamVolume(STREAM_MUSIC, maxMusicVolume, 0);
        maxMusicVolume = mAudioManager.getStreamVolume(STREAM_MUSIC);
        volumeDelta = getVolumeDelta(mAudioManager.getStreamVolume(STREAM_MUSIC));
        mAudioManager.adjustVolume(ADJUST_LOWER, 0);
        assertEquals(Math.max(0, maxMusicVolume - volumeDelta),
                     mAudioManager.getStreamVolume(STREAM_MUSIC));

        mp.stop();
        mp.release();
        Thread.sleep(TIME_TO_PLAY);
        assertFalse(mAudioManager.isMusicActive());
    }

    public void testSetInvalidRingerMode() {
        int ringerMode = mAudioManager.getRingerMode();
        mAudioManager.setRingerMode(-1337);
        assertEquals(ringerMode, mAudioManager.getRingerMode());

        mAudioManager.setRingerMode(-3007);
        assertEquals(ringerMode, mAudioManager.getRingerMode());
    }

    private int getVolumeDelta(int volume) {
        if (!mUseMasterVolume) {
            return 1;
        }
        int volumeDelta = mMasterVolumeMap.floorEntry(volume).getValue();
        assertTrue(volumeDelta > 0);
        return volumeDelta;
    }
}
