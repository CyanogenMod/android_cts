/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Common code for the various LauncherApps tests.
 */
public class BaseLauncherAppsTest extends BaseDevicePolicyTest {

    protected static final String SIMPLE_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    protected static final String SIMPLE_APP_APK = "CtsSimpleApp.apk";
    protected static final String LAUNCHER_TESTS_PKG = "com.android.cts.launchertests";
    protected static final String LAUNCHER_TESTS_CLASS = LAUNCHER_TESTS_PKG + ".LauncherAppsTests";
    private static final String LAUNCHER_TESTS_APK = "CtsLauncherAppsTests.apk";
    private static final String LAUNCHER_TESTS_SUPPORT_PKG =
            "com.android.cts.launchertests.support";
    private static final String LAUNCHER_TESTS_SUPPORT_APK = "CtsLauncherAppsTestsSupport.apk";

    protected void installTestApps() throws Exception {
        uninstallTestApps();
        installApp(LAUNCHER_TESTS_APK);
        installApp(LAUNCHER_TESTS_SUPPORT_APK);
    }

    protected void uninstallTestApps() throws Exception {
        getDevice().uninstallPackage(LAUNCHER_TESTS_PKG);
        getDevice().uninstallPackage(LAUNCHER_TESTS_SUPPORT_PKG);
        getDevice().uninstallPackage(SIMPLE_APP_PKG);
    }

    protected void removeTestUsers() throws Exception {
        for (int userId : listUsers()) {
            if (userId != 0) {
                removeUser(userId);
            }
        }
    }

    protected void startCallbackService() throws Exception {
        String command = "am startservice --user 0 "
                + "-a " + LAUNCHER_TESTS_SUPPORT_PKG + ".REGISTER_CALLBACK "
                + LAUNCHER_TESTS_SUPPORT_PKG + "/.LauncherCallbackTestsService";
        CLog.logAndDisplay(LogLevel.INFO, "Output for command " + command + ": "
              + getDevice().executeShellCommand(command));

    }
}
