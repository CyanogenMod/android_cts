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

package com.android.cts.devicepolicy;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Set of tests for LauncherApps attempting to access a non-profiles
 * apps.
 */
public class LauncherAppsMultiUserTest extends BaseLauncherAppsTest {
    private static final String FEATURE_LIVE_TV = "android.software.live_tv";

    private int mSecondaryUserId;
    private int mSecondaryUserSerialNumber;

    private boolean mMultiUserSupported;
    private boolean mHasLiveTvFeature;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // We need multi user to be supported in order to create a secondary user
        // and api level 21 to support LauncherApps
        mMultiUserSupported = getMaxNumberOfUsersSupported() > 1 && getDevice().getApiLevel() >= 21;
        mHasLiveTvFeature = hasDeviceFeature(FEATURE_LIVE_TV);

        if (mMultiUserSupported) {
            removeTestUsers();
            installTestApps();
            // Create a secondary user.
            mSecondaryUserId = createUser();
            mSecondaryUserSerialNumber = getUserSerialNumber(mSecondaryUserId);
            startUser(mSecondaryUserId);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mMultiUserSupported) {
            removeUser(mSecondaryUserId);
            uninstallTestApps();
        }
        super.tearDown();
    }

    public void testGetActivitiesForNonProfileFails() throws Exception {
        if (!mMultiUserSupported || mHasLiveTvFeature) {
            return;
        }
        installApp(SIMPLE_APP_APK);
        try {
            assertTrue(runDeviceTests(LAUNCHER_TESTS_PKG,
                    LAUNCHER_TESTS_CLASS,
                    "testGetActivitiesForUserFails",
                            0, "-e testUser " + mSecondaryUserSerialNumber));
        } finally {
            getDevice().uninstallPackage(SIMPLE_APP_PKG);
        }
    }

    public void testNoLauncherCallbackPackageAddedSecondaryUser() throws Exception {
        if (!mMultiUserSupported || mHasLiveTvFeature) {
            return;
        }
        startCallbackService();
        installApp(SIMPLE_APP_APK);
        try {
            assertTrue(runDeviceTests(LAUNCHER_TESTS_PKG,
                    LAUNCHER_TESTS_CLASS,
                            "testNoPackageAddedCallbackForUser",
                            0, "-e testUser " + mSecondaryUserSerialNumber));
        } finally {
            getDevice().uninstallPackage(SIMPLE_APP_PKG);
        }
    }
}
