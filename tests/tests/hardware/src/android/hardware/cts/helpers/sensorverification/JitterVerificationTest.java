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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.TestCase;

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link JitterVerification}.
 */
public class JitterVerificationTest extends TestCase {

    public void testVerify() {
        final int SAMPLE_SIZE = 100;
        // for unit testing the verification, only the parameter 'sensorMightHaveMoreListeners' is
        // required
        TestSensorEnvironment environment = new TestSensorEnvironment(
                null /* context */,
                null /* sensor */,
                false /* sensorMightHaveMoreListeners */,
                0 /*samplingPeriodUs */,
                0 /* maxReportLatencyUs */);

        // 100 samples at 1000Hz
        long[] timestamps = new long[SAMPLE_SIZE];
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            timestamps[i] = i * 100000;
        }
        SensorStats stats = new SensorStats();
        ISensorVerification verification = getVerification(1, timestamps);
        verification.verify(environment, stats);
        verifyStats(stats, true, 0.0);

        // 90 samples at 1000Hz, 10 samples at 2000Hz
        long timestamp = 0;
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            timestamps[i] = timestamp;
            timestamp += (i % 10 == 0) ? 500000 : 1000000;
        }
        stats = new SensorStats();
        verification = getVerification(1, timestamps);
        try {
            verification.verify(environment, stats);
            fail("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, 50); // 500 us range divide by 1ms requested sample time x 100%
    }

    public void testCalculateDelta() {
        long[] timestamps = new long[]{0, 1, 2, 3, 4};
        JitterVerification verification = getVerification(1, timestamps);
        List<Long> deltaValues = verification.getDeltaValues();
        assertEquals(4, deltaValues.size());
        assertEquals(1, deltaValues.get(0).doubleValue());
        assertEquals(1, deltaValues.get(1).doubleValue());
        assertEquals(1, deltaValues.get(2).doubleValue());
        assertEquals(1, deltaValues.get(3).doubleValue());

        timestamps = new long[]{0, 0, 2, 4, 4};
        verification = getVerification(1, timestamps);
        deltaValues = verification.getDeltaValues();
        assertEquals(4, deltaValues.size());
        assertEquals(0, deltaValues.get(0).doubleValue());
        assertEquals(2, deltaValues.get(1).doubleValue());
        assertEquals(2, deltaValues.get(2).doubleValue());
        assertEquals(0, deltaValues.get(3).doubleValue());

        timestamps = new long[]{0, 1, 4, 9, 16};
        verification = getVerification(1, timestamps);
        deltaValues = verification.getDeltaValues();
        assertEquals(4, deltaValues.size());
        assertEquals(1, deltaValues.get(0).doubleValue());
        assertEquals(3, deltaValues.get(1).doubleValue());
        assertEquals(5, deltaValues.get(2).doubleValue());
        assertEquals(7, deltaValues.get(3).doubleValue());
    }

    private static JitterVerification getVerification(int threshold, long ... timestamps) {
        Collection<TestSensorEvent> events = new ArrayList<>(timestamps.length);
        for (long timestamp : timestamps) {
            events.add(new TestSensorEvent(null, timestamp, 0, null));
        }
        long samplePeriodNs = 1000*1000; //1000Hz
        long jitterThresholdNs = 20*1000; // 2%

        JitterVerification verification =
                new JitterVerification(threshold, jitterThresholdNs, samplePeriodNs);
        verification.addSensorEvents(events);
        return verification;
    }

    private void verifyStats(SensorStats stats, boolean passed, double normalizedRange) {
        assertEquals(passed, stats.getValue(JitterVerification.PASSED_KEY));
        assertEquals(
                normalizedRange,
                (Double) stats.getValue(SensorStats.JITTER_95_PERCENTILE_PERCENT_KEY),
                0.01);
    }
}
