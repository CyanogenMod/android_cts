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

package android.dumpsys.cts;

import com.android.ddmlib.Log;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Test to check the format of the dumps of various services (currently only procstats is tested).
 */
public class DumpsysHostTest extends DeviceTestCase {
    private static final String TAG = "DumpsysHostTest";

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }

    /**
     * Tests the output of "dumpsys procstats -c". This is a proxy for testing "dumpsys procstats
     * --checkin", since the latter is not idempotent.
     *
     * @throws Exception
     */
    public void testProcstatsOutput() throws Exception {
        if (mDevice.getApiLevel() < 19) {
            Log.i(TAG, "No Procstats output before KitKat, skipping test.");
            return;
        }

        String procstats = mDevice.executeShellCommand("dumpsys procstats -c");
        assertNotNull(procstats);
        assertTrue(procstats.length() > 0);

        Set<String> seenTags = new HashSet<>();
        int version = -1;

        try (BufferedReader reader = new BufferedReader(
                new StringReader(procstats))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                // extra space to make sure last column shows up.
                if (line.endsWith(",")) {
                  line = line + " ";
                }
                String[] parts = line.split(",");
                seenTags.add(parts[0]);

                switch (parts[0]) {
                    case "vers":
                        assertEquals(2, parts.length);
                        version = Integer.parseInt(parts[1]);
                        break;
                    case "period":
                        checkPeriod(parts);
                        break;
                    case "pkgproc":
                        checkPkgProc(parts, version);
                        break;
                    case "pkgpss":
                        checkPkgPss(parts, version);
                        break;
                    case "pkgsvc-bound":
                    case "pkgsvc-exec":
                    case "pkgsvc-run":
                    case "pkgsvc-start":
                        checkPkgSvc(parts, version);
                        break;
                    case "pkgkills":
                        checkPkgKills(parts, version);
                        break;
                    case "proc":
                        checkProc(parts);
                        break;
                    case "pss":
                        checkPss(parts);
                        break;
                    case "kills":
                        checkKills(parts);
                        break;
                    case "total":
                        checkTotal(parts);
                        break;
                    default:
                        break;
                }
            }
        }

        // spot check a few tags
        assertSeenTag(seenTags, "pkgproc");
        assertSeenTag(seenTags, "proc");
        assertSeenTag(seenTags, "pss");
        assertSeenTag(seenTags, "total");
    }

    private void checkPeriod(String[] parts) {
        assertEquals(5, parts.length);
        assertNotNull(parts[1]); // date
        assertInteger(parts[2]); // start time (msec)
        assertInteger(parts[3]); // end time (msec)
        assertNotNull(parts[4]); // status
    }

    private void checkPkgProc(String[] parts, int version) {
        int statesStartIndex;

        if (version < 4) {
            assertTrue(parts.length >= 4);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertNotNull(parts[3]); // process
            statesStartIndex = 4;
        } else {
            assertTrue(parts.length >= 5);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertInteger(parts[3]); // app version
            assertNotNull(parts[4]); // process
            statesStartIndex = 5;
        }

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], true); // tag
            assertInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkTag(String tag, boolean hasProcess) {
        assertEquals(hasProcess ? 3 : 2, tag.length());

        // screen: 0 = off, 1 = on
        char s = tag.charAt(0);
        if (s != '0' && s != '1') {
            fail("malformed tag: " + tag);
        }

        // memory: n = normal, m = moderate, l = low, c = critical
        char m = tag.charAt(1);
        if (m != 'n' && m != 'm' && m != 'l' && m != 'c') {
            fail("malformed tag: " + tag);
        }

        if (hasProcess) {
            char p = tag.charAt(2);
            assertTrue("malformed tag: " + tag, p >= 'a' && p <= 'z');
        }
    }

    private void checkPkgPss(String[] parts, int version) {
        int statesStartIndex;

        if (version < 4) {
            assertTrue(parts.length >= 4);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertNotNull(parts[3]); // process
            statesStartIndex = 4;
        } else {
            assertTrue(parts.length >= 5);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertInteger(parts[3]); // app version
            assertNotNull(parts[4]); // process
            statesStartIndex = 5;
        }

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(8, subparts.length);
            checkTag(subparts[0], true); // tag
            assertInteger(subparts[1]); // sample size
            assertInteger(subparts[2]); // pss min
            assertInteger(subparts[3]); // pss avg
            assertInteger(subparts[4]); // pss max
            assertInteger(subparts[5]); // uss min
            assertInteger(subparts[6]); // uss avg
            assertInteger(subparts[7]); // uss max
        }
    }

    private void checkPkgSvc(String[] parts, int version) {
        int statesStartIndex;

        if (version < 4) {
            assertTrue(parts.length >= 5);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertNotNull(parts[3]); // service name
            assertInteger(parts[4]); // count
            statesStartIndex = 5;
        } else {
            assertTrue(parts.length >= 6);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertInteger(parts[3]); // app version
            assertNotNull(parts[4]); // service name
            assertInteger(parts[5]); // count
            statesStartIndex = 6;
        }

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], false); // tag
            assertInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkPkgKills(String[] parts, int version) {
        String pssStr;

        if (version < 4) {
            assertEquals(8, parts.length);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertNotNull(parts[3]); // process
            assertInteger(parts[4]); // wakes
            assertInteger(parts[5]); // cpu
            assertInteger(parts[6]); // cached
            pssStr = parts[7];
        } else {
            assertEquals(9, parts.length);
            assertNotNull(parts[1]); // package name
            assertInteger(parts[2]); // uid
            assertInteger(parts[3]); // app version
            assertNotNull(parts[4]); // process
            assertInteger(parts[5]); // wakes
            assertInteger(parts[6]); // cpu
            assertInteger(parts[7]); // cached
            pssStr = parts[8];
        }

        String[] subparts = pssStr.split(":");
        assertEquals(3, subparts.length);
        assertInteger(subparts[0]); // pss min
        assertInteger(subparts[1]); // pss avg
        assertInteger(subparts[2]); // pss max
    }

    private void checkProc(String[] parts) {
        assertTrue(parts.length >= 3);
        assertNotNull(parts[1]); // package name
        assertInteger(parts[2]); // uid

        for (int i = 3; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], true); // tag
            assertInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkPss(String[] parts) {
        assertTrue(parts.length >= 3);
        assertNotNull(parts[1]); // package name
        assertInteger(parts[2]); // uid

        for (int i = 3; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(8, subparts.length);
            checkTag(subparts[0], true); // tag
            assertInteger(subparts[1]); // sample size
            assertInteger(subparts[2]); // pss min
            assertInteger(subparts[3]); // pss avg
            assertInteger(subparts[4]); // pss max
            assertInteger(subparts[5]); // uss min
            assertInteger(subparts[6]); // uss avg
            assertInteger(subparts[7]); // uss max
        }
    }

    private void checkKills(String[] parts) {
        assertEquals(7, parts.length);
        assertNotNull(parts[1]); // package name
        assertInteger(parts[2]); // uid
        assertInteger(parts[3]); // wakes
        assertInteger(parts[4]); // cpu
        assertInteger(parts[5]); // cached
        String pssStr = parts[6];

        String[] subparts = pssStr.split(":");
        assertEquals(3, subparts.length);
        assertInteger(subparts[0]); // pss min
        assertInteger(subparts[1]); // pss avg
        assertInteger(subparts[2]); // pss max
    }

    private void checkTotal(String[] parts) {
        assertTrue(parts.length >= 2);
        for (int i = 1; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            checkTag(subparts[0], false); // tag

            if (subparts[1].contains("sysmemusage")) {
                break; // see b/18340771
            }
            assertInteger(subparts[1]); // duration (msec)
        }
    }

    /**
     * Tests the output of "dumpsys batterystats --checkin".
     *
     * @throws Exception
     */
    public void testBatterystatsOutput() throws Exception {
        if (mDevice.getApiLevel() < 21) {
            Log.i(TAG, "Batterystats output before Lollipop, skipping test.");
            return;
        }

        String batterystats = mDevice.executeShellCommand("dumpsys batterystats --checkin");
        assertNotNull(batterystats);
        assertTrue(batterystats.length() > 0);

        Set<String> seenTags = new HashSet<>();
        int version = -1;

        try (BufferedReader reader = new BufferedReader(
                new StringReader(batterystats))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",");
                assertInteger(parts[0]); // old version
                assertInteger(parts[1]); // UID
                switch (parts[2]) { // aggregation type
                    case "i":
                    case "l":
                    case "c":
                    case "u":
                        break;
                    default:
                        fail("malformed stat: " + parts[2]);
                }
                assertNotNull(parts[3]);
                seenTags.add(parts[3]);

                // Note the time fields are measured in milliseconds by default.
                switch (parts[3]) {
                    case "vers":
                        checkVersion(parts);
                        break;
                    case "uid":
                        checkUid(parts);
                        break;
                    case "apk":
                        checkApk(parts);
                        break;
                    case "pr":
                        checkProcess(parts);
                        break;
                    case "sr":
                        checkSensor(parts);
                        break;
                    case "vib":
                        checkVibrator(parts);
                        break;
                    case "fg":
                        checkForeground(parts);
                        break;
                    case "st":
                        checkStateTime(parts);
                        break;
                    case "wl":
                        checkWakelock(parts);
                        break;
                    case "sy":
                        checkSync(parts);
                        break;
                    case "jb":
                        checkJob(parts);
                        break;
                    case "kwl":
                        checkKernelWakelock(parts);
                        break;
                    case "wr":
                        checkWakeupReason(parts);
                        break;
                    case "nt":
                        checkNetwork(parts);
                        break;
                    case "ua":
                        checkUserActivity(parts);
                        break;
                    case "bt":
                        checkBattery(parts);
                        break;
                    case "dc":
                        checkBatteryDischarge(parts);
                        break;
                    case "lv":
                        checkBatteryLevel(parts);
                        break;
                    case "wfl":
                        checkWifi(parts);
                        break;
                    case "m":
                        checkMisc(parts);
                        break;
                    case "gn":
                        checkGlobalNetwork(parts);
                        break;
                    case "br":
                        checkScreenBrightness(parts);
                        break;
                    case "sgt":
                    case "sgc":
                        checkSignalStrength(parts);
                        break;
                    case "sst":
                        checkSignalScanningTime(parts);
                        break;
                    case "dct":
                    case "dcc":
                        checkDataConnection(parts);
                        break;
                    case "wst":
                    case "wsc":
                        checkWifiState(parts);
                        break;
                    case "wsst":
                    case "wssc":
                        checkWifiSupplState(parts);
                        break;
                    case "wsgt":
                    case "wsgc":
                        checkWifiSignalStrength(parts);
                        break;
                    case "bst":
                    case "bsc":
                        checkBluetoothState(parts);
                        break;
                    case "pws":
                        checkPowerUseSummary(parts);
                        break;
                    case "pwi":
                        checkPowerUseItem(parts);
                        break;
                    case "dsd":
                    case "csd":
                        checkChargeDischargeStep(parts);
                        break;
                    case "dtr":
                        checkDischargeTimeRemain(parts);
                        break;
                    case "ctr":
                        checkChargeTimeRemain(parts);
                        break;
                    default:
                        break;
                }
            }
        }

        // spot check a few tags
        assertSeenTag(seenTags, "vers");
        assertSeenTag(seenTags, "bt");
        assertSeenTag(seenTags, "dc");
        assertSeenTag(seenTags, "m");
    }

    private void checkVersion(String[] parts) {
        assertEquals(8, parts.length);
        assertInteger(parts[4]); // checkinVersion
        assertInteger(parts[5]); // parcelVersion
        assertNotNull(parts[6]); // startPlatformVersion
        assertNotNull(parts[7]); // endPlatformVersion
    }

    private void checkUid(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // uid
        assertNotNull(parts[5]); // pkgName
    }

    private void checkApk(String[] parts) {
        assertEquals(10, parts.length);
        assertInteger(parts[4]); // wakeups
        assertNotNull(parts[5]); // apk
        assertNotNull(parts[6]); // service
        assertInteger(parts[7]); // startTime
        assertInteger(parts[8]); // starts
        assertInteger(parts[9]); // launches
    }

    private void checkProcess(String[] parts) {
        assertTrue(parts.length >= 9);
        assertNotNull(parts[4]); // process
        assertInteger(parts[5]); // userMillis
        assertInteger(parts[6]); // systemMillis
        assertInteger(parts[7]); // foregroundMillis
        assertInteger(parts[8]); // starts
    }

    private void checkSensor(String[] parts) {
        assertEquals(7, parts.length);
        assertInteger(parts[4]); // sensorNumber
        assertInteger(parts[5]); // totalTime
        assertInteger(parts[6]); // count
    }

    private void checkVibrator(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // totalTime
        assertInteger(parts[5]); // count
    }

    private void checkForeground(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // totalTime
        assertInteger(parts[5]); // count
    }

    private void checkStateTime(String[] parts) {
        assertEquals(7, parts.length);
        assertInteger(parts[4]); // foreground
        assertInteger(parts[5]); // active
        assertInteger(parts[6]); // running
    }

    private void checkWakelock(String[] parts) {
        assertEquals(14, parts.length);
        assertNotNull(parts[4]);      // wakelock
        assertInteger(parts[5]);      // full totalTime
        assertEquals("f", parts[6]);  // full
        assertInteger(parts[7]);      // full count
        assertInteger(parts[8]);      // partial totalTime
        assertEquals("p", parts[9]);  // partial
        assertInteger(parts[10]);     // partial count
        assertInteger(parts[11]);     // window totalTime
        assertEquals("w", parts[12]); // window
        assertInteger(parts[13]);     // window count
    }

    private void checkSync(String[] parts) {
        assertEquals(7, parts.length);
        assertNotNull(parts[4]); // sync
        assertInteger(parts[5]); // totalTime
        assertInteger(parts[6]); // count
    }

    private void checkJob(String[] parts) {
        assertEquals(7, parts.length);
        assertNotNull(parts[4]); // job
        assertInteger(parts[5]); // totalTime
        assertInteger(parts[6]); // count
    }

    private void checkKernelWakelock(String[] parts) {
        assertEquals(7, parts.length);
        assertNotNull(parts[4]); // kernel wakelock
        assertInteger(parts[5]); // totalTime
        assertInteger(parts[6]); // count
    }

    private void checkWakeupReason(String[] parts) {
        assertTrue(parts.length >= 7);
        for (int i = 4; i < parts.length-2; i++) {
            assertNotNull(parts[i]); // part of wakeup
        }
        assertInteger(parts[parts.length-2]); // totalTime
        assertInteger(parts[parts.length-1]); // count
    }

    private void checkNetwork(String[] parts) {
        assertEquals(14, parts.length);
        assertInteger(parts[4]);  // mobileBytesRx
        assertInteger(parts[5]);  // mobileBytesTx
        assertInteger(parts[6]);  // wifiBytesRx
        assertInteger(parts[7]);  // wifiBytesTx
        assertInteger(parts[8]);  // mobilePacketsRx
        assertInteger(parts[9]);  // mobilePacketsTx
        assertInteger(parts[10]); // wifiPacketsRx
        assertInteger(parts[11]); // wifiPacketsTx
        assertInteger(parts[12]); // mobileActiveTime (usec)
        assertInteger(parts[13]); // mobileActiveCount
    }

    private void checkUserActivity(String[] parts) {
        assertEquals(7, parts.length);
        assertInteger(parts[4]); // other
        assertInteger(parts[5]); // button
        assertInteger(parts[6]); // touch
    }

    private void checkBattery(String[] parts) {
        assertEquals(12, parts.length);
        if (!parts[4].equals("N/A")) {
            assertInteger(parts[4]);  // startCount
        }
        assertInteger(parts[5]);  // batteryRealtime
        assertInteger(parts[6]);  // batteryUptime
        assertInteger(parts[7]);  // totalRealtime
        assertInteger(parts[8]);  // totalUptime
        assertInteger(parts[9]);  // startClockTime
        assertInteger(parts[10]); // batteryScreenOffRealtime
        assertInteger(parts[11]); // batteryScreenOffUptime
    }

    private void checkBatteryDischarge(String[] parts) {
        assertEquals(8, parts.length);
        assertInteger(parts[4]); // low
        assertInteger(parts[5]); // high
        assertInteger(parts[6]); // screenOn
        assertInteger(parts[7]); // screenOff
    }

    private void checkBatteryLevel(String[] parts) {
        assertEquals(6, parts.length);
        assertInteger(parts[4]); // startLevel
        assertInteger(parts[5]); // currentLevel
    }

    private void checkWifi(String[] parts) {
        assertEquals(7, parts.length);
        assertInteger(parts[4]); // fullWifiLockOnTime (usec)
        assertInteger(parts[5]); // wifiScanTime (usec)
        assertInteger(parts[6]); // uidWifiRunningTime (usec)
    }

    private void checkMisc(String[] parts) {
        assertTrue(parts.length >= 20);
        assertInteger(parts[4]);      // screenOnTime
        assertInteger(parts[5]);      // phoneOnTime
        assertInteger(parts[6]);      // wifiOnTime
        assertInteger(parts[7]);      // wifiRunningTime
        assertInteger(parts[8]);      // bluetoothOnTime
        assertInteger(parts[9]);      // mobileRxTotalBytes
        assertInteger(parts[10]);     // mobileTxTotalBytes
        assertInteger(parts[11]);     // wifiRxTotalBytes
        assertInteger(parts[12]);     // wifiTxTotalBytes
        assertInteger(parts[13]);     // fullWakeLockTimeTotal
        assertInteger(parts[14]);     // partialWakeLockTimeTotal
        assertEquals("0", parts[15]); // legacy input event count
        assertInteger(parts[16]);     // mobileRadioActiveTime
        assertInteger(parts[17]);     // mobileRadioActiveAdjustedTime
        assertInteger(parts[18]);     // interactiveTime
        assertInteger(parts[19]);     // lowPowerModeEnabledTime
    }

    private void checkGlobalNetwork(String[] parts) {
        assertEquals(12, parts.length);
        assertInteger(parts[4]);  // mobileRxTotalBytes
        assertInteger(parts[5]);  // mobileTxTotalBytes
        assertInteger(parts[6]);  // wifiRxTotalBytes
        assertInteger(parts[7]);  // wifiTxTotalBytes
        assertInteger(parts[8]);  // mobileRxTotalPackets
        assertInteger(parts[9]);  // mobileTxTotalPackets
        assertInteger(parts[10]); // wifiRxTotalPackets
        assertInteger(parts[11]); // wifiTxTotalPackets
    }

    private void checkScreenBrightness(String[] parts) {
        assertEquals(9, parts.length);
        assertInteger(parts[4]); // dark
        assertInteger(parts[5]); // dim
        assertInteger(parts[6]); // medium
        assertInteger(parts[7]); // light
        assertInteger(parts[8]); // bright
    }

    private void checkSignalStrength(String[] parts) {
        assertEquals(9, parts.length);
        assertInteger(parts[4]); // none
        assertInteger(parts[5]); // poor
        assertInteger(parts[6]); // moderate
        assertInteger(parts[7]); // good
        assertInteger(parts[8]); // great
    }

    private void checkSignalScanningTime(String[] parts) {
        assertEquals(5, parts.length);
        assertInteger(parts[4]); // signalScanningTime
    }

    private void checkDataConnection(String[] parts) {
        assertEquals(21, parts.length);
        assertInteger(parts[4]);  // none
        assertInteger(parts[5]);  // gprs
        assertInteger(parts[6]);  // edge
        assertInteger(parts[7]);  // umts
        assertInteger(parts[8]);  // cdma
        assertInteger(parts[9]);  // evdo_0
        assertInteger(parts[10]); // evdo_A
        assertInteger(parts[11]); // 1xrtt
        assertInteger(parts[12]); // hsdpa
        assertInteger(parts[13]); // hsupa
        assertInteger(parts[14]); // hspa
        assertInteger(parts[15]); // iden
        assertInteger(parts[16]); // evdo_b
        assertInteger(parts[17]); // lte
        assertInteger(parts[18]); // ehrpd
        assertInteger(parts[19]); // hspap
        assertInteger(parts[20]); // other
    }

    private void checkWifiState(String[] parts) {
        assertEquals(12, parts.length);
        assertInteger(parts[4]);  // off
        assertInteger(parts[5]);  // scanning
        assertInteger(parts[6]);  // no_net
        assertInteger(parts[7]);  // disconn
        assertInteger(parts[8]);  // sta
        assertInteger(parts[9]);  // p2p
        assertInteger(parts[10]); // sta_p2p
        assertInteger(parts[11]); // soft_ap
    }

    private void checkWifiSupplState(String[] parts) {
        assertEquals(17, parts.length);
        assertInteger(parts[4]);  // inv
        assertInteger(parts[5]);  // dsc
        assertInteger(parts[6]);  // dis
        assertInteger(parts[7]);  // inact
        assertInteger(parts[8]);  // scan
        assertInteger(parts[9]);  // auth
        assertInteger(parts[10]); // ascing
        assertInteger(parts[11]); // asced
        assertInteger(parts[12]); // 4-way
        assertInteger(parts[13]); // group
        assertInteger(parts[14]); // compl
        assertInteger(parts[15]); // dorm
        assertInteger(parts[16]); // uninit
    }

    private void checkWifiSignalStrength(String[] parts) {
        assertEquals(9, parts.length);
        assertInteger(parts[4]); // none
        assertInteger(parts[5]); // poor
        assertInteger(parts[6]); // moderate
        assertInteger(parts[7]); // good
        assertInteger(parts[8]); // great
    }

    private void checkBluetoothState(String[] parts) {
        assertEquals(8, parts.length);
        assertInteger(parts[4]); // inactive
        assertInteger(parts[5]); // low
        assertInteger(parts[6]); // med
        assertInteger(parts[7]); // high
    }

    private void checkPowerUseSummary(String[] parts) {
        assertEquals(8, parts.length);
        assertDouble(parts[4]); // batteryCapacity
        assertDouble(parts[5]); // computedPower
        assertDouble(parts[6]); // minDrainedPower
        assertDouble(parts[7]); // maxDrainedPower
    }

    private void checkPowerUseItem(String[] parts) {
        assertEquals(6, parts.length);
        assertNotNull(parts[4]); // label
        assertDouble(parts[5]);  // mAh
    }

    private void checkChargeDischargeStep(String[] parts) {
        assertEquals(8, parts.length);
        assertInteger(parts[4]); // duration
        if (!parts[5].equals("?")) {
            assertInteger(parts[5]); // level
        }
        assertNotNull(parts[6]); // screen
        assertNotNull(parts[7]); // power-save
    }

    private void checkDischargeTimeRemain(String[] parts) {
        assertEquals(5, parts.length);
        assertInteger(parts[4]); // batteryTimeRemaining
    }

    private void checkChargeTimeRemain(String[] parts) {
        assertEquals(5, parts.length);
        assertInteger(parts[4]); // chargeTimeRemaining
    }

    private static void assertInteger(String input) {
        try {
            Long.parseLong(input);
        } catch (NumberFormatException e) {
            fail("Expected an integer but found \"" + input + "\"");
        }
    }

    private static void assertDouble(String input) {
        try {
            Double.parseDouble(input);
        } catch (NumberFormatException e) {
            fail("Expected a double but found \"" + input + "\"");
        }
    }

    private static void assertSeenTag(Set<String> seenTags, String tag) {
        assertTrue("No line starting with \"" + tag + ",\"", seenTags.contains(tag));
    }
}
