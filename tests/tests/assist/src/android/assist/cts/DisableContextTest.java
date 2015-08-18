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

package android.assist.cts;

import android.assist.TestStartActivity;
import android.assist.common.Utils;

import android.app.Activity;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.cts.util.SystemUtil;
import android.os.Bundle;
import android.provider.Settings;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import java.lang.Override;
import java.util.concurrent.CountDownLatch;

/** Test we receive proper assist data when context is disabled or enabled */

public class DisableContextTest extends AssistTestBase {
    static final String TAG = "DisableContextTest";

    private static final String TEST_CASE_TYPE = Utils.DISABLE_CONTEXT;

    public DisableContextTest() {
        super();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        SystemUtil.runShellCommand(getInstrumentation(),
                "settings put secure assist_structure_enabled 0");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 0");
        logContextAndScreenshotSetting();
        startTestActivity(TEST_CASE_TYPE);
        waitForBroadcast();
    }

    @Override
    public void tearDown() throws Exception {
        SystemUtil.runShellCommand(getInstrumentation(),
                "settings put secure assist_structure_enabled 1");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 1");
        logContextAndScreenshotSetting();
        super.tearDown();
    }

    public void testContextAndScreenshotOff() throws Exception {
        // Both settings off
        verifyAssistDataNullness(true, true, true, true);

        // Screenshot off, context on
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_structure_enabled 1");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 0");
        waitForBroadcast();

        logContextAndScreenshotSetting();

        verifyAssistDataNullness(false, false, false, true);

        // Context off, screenshot on
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_structure_enabled 0");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 1");
        waitForBroadcast();

        logContextAndScreenshotSetting();

        verifyAssistDataNullness(true, true, true, false);
    }
}