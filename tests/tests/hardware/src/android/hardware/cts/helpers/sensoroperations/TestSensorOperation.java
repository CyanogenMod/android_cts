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

package android.hardware.cts.helpers.sensoroperations;

import junit.framework.Assert;

import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.SensorTestPlatformException;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.hardware.cts.helpers.TestSensorEventListener;
import android.hardware.cts.helpers.TestSensorManager;
import android.hardware.cts.helpers.reporting.ISensorTestNode;
import android.hardware.cts.helpers.sensorverification.EventGapVerification;
import android.hardware.cts.helpers.sensorverification.EventOrderingVerification;
import android.hardware.cts.helpers.sensorverification.EventTimestampSynchronizationVerification;
import android.hardware.cts.helpers.sensorverification.FrequencyVerification;
import android.hardware.cts.helpers.sensorverification.ISensorVerification;
import android.hardware.cts.helpers.sensorverification.JitterVerification;
import android.hardware.cts.helpers.sensorverification.MagnitudeVerification;
import android.hardware.cts.helpers.sensorverification.MeanVerification;
import android.hardware.cts.helpers.sensorverification.StandardDeviationVerification;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SensorOperation} used to verify that sensor events and sensor values are correct.
 * <p>
 * Provides methods to set test expectations as well as providing a set of default expectations
 * depending on sensor type.  When {{@link #execute(ISensorTestNode)} is called, the sensor will
 * collect the events and then run all the tests.
 * </p>
 */
public class TestSensorOperation extends SensorOperation {
    private static final String TAG = "TestSensorOperation";

    private final HashSet<ISensorVerification> mVerifications = new HashSet<>();

    private final TestSensorManager mSensorManager;
    private final TestSensorEnvironment mEnvironment;
    private final Executor mExecutor;
    private final Handler mHandler;

    /**
     * An interface that defines an abstraction for operations to be performed by the
     * {@link TestSensorOperation}.
     */
    public interface Executor {
        void execute(TestSensorManager sensorManager, TestSensorEventListener listener)
                throws InterruptedException;
    }

    /**
     * Create a {@link TestSensorOperation}.
     */
    public TestSensorOperation(TestSensorEnvironment environment, Executor executor) {
        this(environment, executor, null /* handler */);
    }

    /**
     * Create a {@link TestSensorOperation}.
     */
    public TestSensorOperation(
            TestSensorEnvironment environment,
            Executor executor,
            Handler handler) {
        mEnvironment = environment;
        mExecutor = executor;
        mHandler = handler;
        mSensorManager = new TestSensorManager(mEnvironment);
    }

    /**
     * Set all of the default test expectations.
     */
    public void addDefaultVerifications() {
        addVerification(EventGapVerification.getDefault(mEnvironment));
        addVerification(EventOrderingVerification.getDefault(mEnvironment));
        addVerification(FrequencyVerification.getDefault(mEnvironment));
        addVerification(JitterVerification.getDefault(mEnvironment));
        addVerification(MagnitudeVerification.getDefault(mEnvironment));
        addVerification(MeanVerification.getDefault(mEnvironment));
        addVerification(StandardDeviationVerification.getDefault(mEnvironment));
        addVerification(EventTimestampSynchronizationVerification.getDefault(mEnvironment));
    }

    public void addVerification(ISensorVerification verification) {
        if (verification != null) {
            mVerifications.add(verification);
        }
    }

    /**
     * Collect the specified number of events from the sensor and run all enabled verifications.
     */
    @Override
    public void execute(ISensorTestNode parent) throws InterruptedException {
        getStats().addValue("sensor_name", mEnvironment.getSensor().getName());
        TestSensorEventListener listener = new TestSensorEventListener(mEnvironment, mHandler);
        mExecutor.execute(mSensorManager, listener);

        boolean failed = false;
        StringBuilder sb = new StringBuilder();
        List<TestSensorEvent> collectedEvents = listener.getCollectedEvents();
        for (ISensorVerification verification : mVerifications) {
            failed |= evaluateResults(collectedEvents, verification, sb);
        }

        if (failed) {
            trySaveCollectedEvents(parent, listener);

            String msg = SensorCtsHelper
                    .formatAssertionMessage("VerifySensorOperation", mEnvironment, sb.toString());
            getStats().addValue(SensorStats.ERROR, msg);
            Assert.fail(msg);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSensorOperation clone() {
        TestSensorOperation operation = new TestSensorOperation(mEnvironment, mExecutor);
        for (ISensorVerification verification : mVerifications) {
            operation.addVerification(verification.clone());
        }
        return operation;
    }

    /**
     * Evaluate the results of a test, aggregate the stats, and build the error message.
     */
    private boolean evaluateResults(
            List<TestSensorEvent> events,
            ISensorVerification verification,
            StringBuilder sb) {
        try {
            // this is an intermediate state in refactoring, at some point verifications might
            // become stateless
            verification.addSensorEvents(events);
            verification.verify(mEnvironment, getStats());
        } catch (AssertionError e) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getMessage());
            return true;
        }
        return false;
    }

    /**
     * Tries to save collected {@link TestSensorEvent}s to a file.
     *
     * NOTE: it is more important to handle verifications and its results, than failing if the file
     * cannot be created. So we silently fail if necessary.
     */
    private void trySaveCollectedEvents(ISensorTestNode parent, TestSensorEventListener listener) {
        String sanitizedFileName;
        try {
            String fileName = asTestNode(parent).getName();
            sanitizedFileName = String.format(
                    "%s-%s-%s_%dus.txt",
                    SensorCtsHelper.sanitizeStringForFileName(fileName),
                    SensorStats.getSanitizedSensorName(mEnvironment.getSensor()),
                    mEnvironment.getFrequencyString(),
                    mEnvironment.getMaxReportLatencyUs());
        } catch (SensorTestPlatformException e) {
            Log.w(TAG, "Unable to generate file name to save collected events", e);
            return;
        }

        try {
            listener.logCollectedEventsToFile(sanitizedFileName);
        } catch (IOException e) {
            Log.w(TAG, "Unable to save collected events to file: " + sanitizedFileName, e);
        }
    }

    /**
     * Creates an operation that will wait for a given amount of events to arrive.
     *
     * @param environment The test environment.
     * @param eventCount The number of events to wait for.
     */
    public static TestSensorOperation createOperation(
            TestSensorEnvironment environment,
            final int eventCount) {
        Executor executor = new Executor() {
            @Override
            public void execute(TestSensorManager sensorManager, TestSensorEventListener listener)
                    throws InterruptedException {
                try {
                    CountDownLatch latch = sensorManager.registerListener(listener, eventCount);
                    listener.waitForEvents(latch, eventCount);
                } finally {
                    sensorManager.unregisterListener();
                }
            }
        };
        return new TestSensorOperation(environment, executor);
    }

    /**
     * Creates an operation that will wait for a given amount of time to collect events.
     *
     * @param environment The test environment.
     * @param duration The duration to wait for events.
     * @param timeUnit The time unit for {@code duration}.
     */
    public static TestSensorOperation createOperation(
            TestSensorEnvironment environment,
            final long duration,
            final TimeUnit timeUnit) {
        Executor executor = new Executor() {
            @Override
            public void execute(TestSensorManager sensorManager, TestSensorEventListener listener)
                    throws InterruptedException {
                try {
                    sensorManager.registerListener(listener);
                    listener.waitForEvents(duration, timeUnit);
                } finally {
                    sensorManager.unregisterListener();
                }
            }
        };
        return new TestSensorOperation(environment, executor);
    }

    /**
     * Creates an operation that will wait for a given amount of time before calling
     * {@link TestSensorManager#requestFlush()}.
     *
     * @param environment The test environment.
     * @param duration The duration to wait before calling {@link TestSensorManager#requestFlush()}.
     * @param timeUnit The time unit for {@code duration}.
     */
    public static TestSensorOperation createFlushOperation(
            TestSensorEnvironment environment,
            final long duration,
            final TimeUnit timeUnit) {
        Executor executor = new Executor() {
            @Override
            public void execute(TestSensorManager sensorManager, TestSensorEventListener listener)
                    throws InterruptedException {
                try {
                    sensorManager.registerListener(listener);
                    SensorCtsHelper.sleep(duration, timeUnit);
                    CountDownLatch latch = sensorManager.requestFlush();
                    listener.waitForFlushComplete(latch);
                } finally {
                    sensorManager.unregisterListener();
                }
            }
        };
        return new TestSensorOperation(environment, executor);
    }
}
