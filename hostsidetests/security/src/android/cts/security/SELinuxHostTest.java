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

package android.cts.security;

import com.android.cts.tradefed.build.CtsBuildHelper;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.String;
import java.net.URL;
import java.util.Scanner;

/**
 * Host-side SELinux tests.
 *
 * These tests analyze the policy file in use on the subject device directly or
 * run as the shell user to evaluate aspects of the state of SELinux on the test
 * device which otherwise would not be available to a normal apk.
 */
public class SELinuxHostTest extends DeviceTestCase {

    private File sepolicyAnalyze;
    private File devicePolicyFile;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    private File copyResourceToTempFile(String resName) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(resName);
        File tempFile = File.createTempFile("SELinuxHostTest", ".tmp");
        FileOutputStream os = new FileOutputStream(tempFile);
        int rByte = 0;
        while ((rByte = is.read()) != -1) {
            os.write(rByte);
        }
        os.flush();
        os.close();
        tempFile.deleteOnExit();
        return tempFile;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();

        /* retrieve the sepolicy-analyze executable from jar */
        sepolicyAnalyze = copyResourceToTempFile("/sepolicy-analyze");
        sepolicyAnalyze.setExecutable(true);

        /* obtain sepolicy file from running device */
        devicePolicyFile = File.createTempFile("sepolicy", ".tmp");
        devicePolicyFile.deleteOnExit();
        mDevice.executeAdbCommand("pull", "/sys/fs/selinux/policy",
                devicePolicyFile.getAbsolutePath());
    }

    /**
     * Tests that all domains in the running policy file are in enforcing mode
     *
     * @throws Exception
     */
    public void testAllEnforcing() throws Exception {

        /* run sepolicy-analyze permissive check on policy file */
        ProcessBuilder pb = new ProcessBuilder(sepolicyAnalyze.getAbsolutePath(),
                devicePolicyFile.getAbsolutePath(), "permissive");
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
        BufferedReader result = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        StringBuilder errorString = new StringBuilder();
        while ((line = result.readLine()) != null) {
            errorString.append(line);
            errorString.append("\n");
        }
        assertTrue("The following SELinux domains were found to be in permissive mode:\n"
                   + errorString, errorString.length() == 0);
    }
}
