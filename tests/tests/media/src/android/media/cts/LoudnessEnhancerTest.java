/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.cts.media.R;

import android.content.Context;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Visualizer;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Visualizer.MeasurementPeakRms;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.util.Log;

public class LoudnessEnhancerTest extends PostProcTestBase {

    private String TAG = "LoudnessEnhancerTest";
    private LoudnessEnhancer mLE = null;
    private Visualizer mVisualizer = null;

    //-----------------------------------------------------------------
    // LOUDNESS ENHANCER TESTS:
    //----------------------------------

    //-----------------------------------------------------------------
    // 0 - constructor
    //----------------------------------

    //Test case 0.0: test constructor and release
    public void test0_0ConstructorAndRelease() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        assertNotNull("null AudioManager", am);
        getLoudnessEnhancer(0);
        releaseLoudnessEnhancer();

        int session = am.generateAudioSessionId();
        assertTrue("cannot generate new session", session != AudioManager.ERROR);
        getLoudnessEnhancer(session);
        releaseLoudnessEnhancer();
    }

    //-----------------------------------------------------------------
    // 1 - get/set parameters
    //----------------------------------

    //Test case 1.0: test set/get target gain
    public void test1_0TargetGain() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        getLoudnessEnhancer(0);
        try {
            mLE.setTargetGain(0);
            assertEquals("target gain differs from value set", 0.0f, mLE.getTargetGain());
            mLE.setTargetGain(800);
            assertEquals("target gain differs from value set", 800.0f, mLE.getTargetGain());
        } catch (IllegalArgumentException e) {
            fail("target gain illegal argument");
        } catch (UnsupportedOperationException e) {
            fail("target gain unsupported operation");
        } catch (IllegalStateException e) {
            fail("target gain operation called in wrong state");
        } finally {
            releaseLoudnessEnhancer();
        }
    }

    //-----------------------------------------------------------------
    // 2 - Effect enable/disable
    //----------------------------------

    //Test case 2.0: test setEnabled() and getEnabled() in valid state
    public void test2_0SetEnabledGetEnabled() throws Exception {
        if (!isLoudnessEnhancerAvailable()) {
            return;
        }
        getLoudnessEnhancer(getSessionId());
        try {
            mLE.setEnabled(true);
            assertTrue("invalid state from getEnabled", mLE.getEnabled());
            mLE.setEnabled(false);
            assertFalse("invalid state to getEnabled", mLE.getEnabled());
            // test passed
        } catch (IllegalStateException e) {
            fail("setEnabled() in wrong state");
        } finally {
            releaseLoudnessEnhancer();
        }
    }

    //Test case 2.1: test setEnabled() throws exception after release
    public void test2_1SetEnabledAfterRelease() throws Exception {
        if (!isLoudnessEnhancerAvailable()) {
            return;
        }
        getLoudnessEnhancer(getSessionId());
        mLE.release();
        try {
            mLE.setEnabled(true);
            fail("setEnabled() processed after release()");
        } catch (IllegalStateException e) {
            // test passed
        } finally {
            releaseLoudnessEnhancer();
        }
    }

  //-----------------------------------------------------------------
    // 3 - check effect using visualizer effect
    //----------------------------------

    //Test case 3.0: test enhancement 6 db
    public void test3_0Measure6dbEnhancement() throws Exception {
        MediaPlayer mp = null;
        AudioManager am = null;
        int originalVolume = 0;
        try {
            mp = MediaPlayer.create(getContext(), R.raw.sine1khzs40dblong);
            final int GAIN_MB =  600;
            final int MAX_GAIN_ERROR_MB = 100;
            assertNotNull("null MediaPlayer", mp);

            am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            assertNotNull("null AudioManager", am);
            originalVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

            int sessionId = mp.getAudioSessionId();
            getLoudnessEnhancer(sessionId);
            getVisualizer(sessionId);
            mp.start();

            mVisualizer.setEnabled(true);
            assertTrue("visualizer not enabled", mVisualizer.getEnabled());
            Thread.sleep(100);
            int status = mVisualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS);
            // make sure we're playing long enough so the measurement is valid
            Thread.sleep(500);
            assertEquals("setMeasurementMode() for PEAK_RMS doesn't report success",
                    Visualizer.SUCCESS, status);
            MeasurementPeakRms measurement = new MeasurementPeakRms();
            status = mVisualizer.getMeasurementPeakRms(measurement);
            assertEquals("getMeasurementPeakRms() reports failure",
                    Visualizer.SUCCESS, status);
            Log.i(TAG, "peak="+measurement.mPeak+"  rms="+measurement.mRms);

            //enable loudness enhancement.
            //start new measurement
            mLE.setTargetGain(GAIN_MB);
            mLE.setEnabled(true);
            assertTrue("LoudnessEnhancer not enabled", mLE.getEnabled());
            Thread.sleep(500);
            MeasurementPeakRms measurement2 = new MeasurementPeakRms();
            status = mVisualizer.getMeasurementPeakRms(measurement2);

            assertEquals("getMeasurementPeakRms()2 reports failure",
                    Visualizer.SUCCESS, status);
            Log.i(TAG, "peak2="+measurement2.mPeak+"  rms2="+measurement2.mRms);
            int diffExpectedPeak = Math.abs(measurement2.mPeak - (measurement.mPeak + GAIN_MB));
            int diffExpectedRms =  Math.abs(measurement2.mRms - (measurement.mRms + GAIN_MB));

            assertTrue("Gain peak deviation in mB=" + diffExpectedPeak,
                    diffExpectedPeak < MAX_GAIN_ERROR_MB);
            assertTrue("Gain RMS deviation in mB=" + diffExpectedRms,
                    diffExpectedRms < MAX_GAIN_ERROR_MB);
        } catch (IllegalStateException e) {
            fail("method called in wrong state");
        } catch (InterruptedException e) {
            fail("sleep() interrupted");
        } finally {
            releaseLoudnessEnhancer();
            releaseVisualizer();
            if (mp != null) {
                mp.stop();
                mp.release();
            }
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
            }

        }
    }

    //-----------------------------------------------------------------
    // private methods
    //----------------------------------
    private void getLoudnessEnhancer(int session) {
        releaseLoudnessEnhancer();
        try {
            mLE = new LoudnessEnhancer(session);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "getLoudnessEnhancer() LoudnessEnhancer not found exception: ", e);
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "getLoudnessEnhancer() Effect library not loaded exception: ", e);
        }
        assertNotNull("could not create LoudnessEnhancer", mLE);
    }

    private void releaseLoudnessEnhancer() {
        if (mLE != null) {
            mLE.release();
            mLE = null;
        }
    }

    private void getVisualizer(int session) {
        if (mVisualizer == null || session != mSession) {
            if (session != mSession && mVisualizer != null) {
                mVisualizer.release();
                mVisualizer = null;
            }
            try {
               mVisualizer = new Visualizer(session);
               mSession = session;
           } catch (IllegalArgumentException e) {
               Log.e(TAG, "getVisualizer() Visualizer not found exception: "+e);
           } catch (UnsupportedOperationException e) {
               Log.e(TAG, "getVisualizer() Effect library not loaded exception: "+e);
           }
        }
        assertNotNull("could not create mVisualizer", mVisualizer);
   }

   private void releaseVisualizer() {
       if (mVisualizer != null) {
           mVisualizer.release();
           mVisualizer = null;
       }
   }
}
