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
 * limitations under the License
 */

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

/**
 * A {@link ISensorVerification} which verifies if the collected sensor events have any obvious 
 * problems, such as no sample, wrong sensor type, etc.
 */
public class EventBasicVerification extends AbstractSensorVerification {

    public static final String PASSED_KEY = "event_basic_passed";
    private static final long ALLOWED_SENSOR_DELIVERING_DELAY_US =
            TimeUnit.MILLISECONDS.toMicros(1000);

    private final long mExpectedMinNumEvent;
    private final Object mSensor;
    private long  mNumEvent;
    private boolean mWrongSensorObserved;

    /**
     * Constructs an instance of {@link EventBasicVerification}.
     *
     * @param maximumSynchronizationErrorNs The valid threshold for timestamp synchronization.
     * @param reportLatencyNs The latency on which batching events are received
     */
    public EventBasicVerification(
            long expectedMinNumEvent,
            Sensor sensor) {
        mExpectedMinNumEvent = expectedMinNumEvent;
        mSensor = sensor;

        mNumEvent = 0;
        mWrongSensorObserved = false;
    }

    /**
     * Gets a default {@link EventBasicVerification}.
     *
     * @param environment The test environment
     * @return The verification or null if the verification is not supported in the given
     *         environment.
     */
    public static EventBasicVerification getDefault(
            TestSensorEnvironment environment,
            long testDurationUs) {

        long minTestDurationUs;
        long batchUs = environment.getMaxReportLatencyUs();
        long sampleUs = environment.getExpectedSamplingPeriodUs();
        if (batchUs > 0) {
            // test duration deduct allowed delivering latency and portion of time to deliver batch
            // (which will be 10% of the batching time)
            long effectiveTime = testDurationUs - ALLOWED_SENSOR_DELIVERING_DELAY_US - batchUs/10;

            // allow part of last batch to be partially delivered (>80%)
            minTestDurationUs = Math.max(
                    effectiveTime/batchUs * batchUs - batchUs/5,
                    environment.getExpectedSamplingPeriodUs());
        } else {
            minTestDurationUs =
                    Math.max(testDurationUs - ALLOWED_SENSOR_DELIVERING_DELAY_US,
                             environment.getExpectedSamplingPeriodUs());
        }

        long expectedMinNumEvent = minTestDurationUs / environment.getExpectedSamplingPeriodUs();
        return new EventBasicVerification(expectedMinNumEvent, environment.getSensor());
    }

    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        verify(stats);
    }

    /* visible to unit test */
    void verify(SensorStats stats) {

        stats.addValue(SensorStats.EVENT_COUNT_KEY, mNumEvent);
        stats.addValue(SensorStats.WRONG_SENSOR_KEY, mWrongSensorObserved);

        boolean enoughSample = mNumEvent >= mExpectedMinNumEvent;
        boolean noWrongSensor = !mWrongSensorObserved;

        boolean success = enoughSample && noWrongSensor;
        stats.addValue(PASSED_KEY, success);

        if (!success) {
            Assert.fail(String.format("Failed due to (%s%s)",
                        enoughSample?"":"insufficient events, ",
                        noWrongSensor?"":"wrong sensor observed, "));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventBasicVerification clone() {
        return new EventBasicVerification( mExpectedMinNumEvent, (Sensor)mSensor );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (event.sensor == mSensor) {
            ++mNumEvent;
        } else {
            mWrongSensorObserved = true;
        }
    }

}
