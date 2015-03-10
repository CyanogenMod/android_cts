/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.admin.cts;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.List;

import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;

/**
 * Test that exercises {@link DevicePolicyManager}. The test requires that the
 * CtsDeviceAdminReceiver be installed via the CtsDeviceAdmin.apk and be
 * activated via "Settings > Location & security > Select device administrators".
 */
public class DevicePolicyManagerTest extends AndroidTestCase {

    private static final String TAG = DevicePolicyManagerTest.class.getSimpleName();

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mComponent;
    private ComponentName mSecondComponent;
    private boolean mDeviceAdmin;

    private static final String TEST_CA_STRING1 =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICVzCCAgGgAwIBAgIJAMvnLHnnfO/IMA0GCSqGSIb3DQEBBQUAMIGGMQswCQYD\n" +
            "VQQGEwJJTjELMAkGA1UECAwCQVAxDDAKBgNVBAcMA0hZRDEVMBMGA1UECgwMSU1G\n" +
            "TCBQVlQgTFREMRAwDgYDVQQLDAdJTUZMIE9VMRIwEAYDVQQDDAlJTUZMLklORk8x\n" +
            "HzAdBgkqhkiG9w0BCQEWEHJhbWVzaEBpbWZsLmluZm8wHhcNMTMwODI4MDk0NDA5\n" +
            "WhcNMjMwODI2MDk0NDA5WjCBhjELMAkGA1UEBhMCSU4xCzAJBgNVBAgMAkFQMQww\n" +
            "CgYDVQQHDANIWUQxFTATBgNVBAoMDElNRkwgUFZUIExURDEQMA4GA1UECwwHSU1G\n" +
            "TCBPVTESMBAGA1UEAwwJSU1GTC5JTkZPMR8wHQYJKoZIhvcNAQkBFhByYW1lc2hA\n" +
            "aW1mbC5pbmZvMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJ738cbTQlNIO7O6nV/f\n" +
            "DJTMvWbPkyHYX8CQ7yXiAzEiZ5bzKJjDJmpRAkUrVinljKns2l6C4++l/5A7pFOO\n" +
            "33kCAwEAAaNQME4wHQYDVR0OBBYEFOdbZP7LaMbgeZYPuds2CeSonmYxMB8GA1Ud\n" +
            "IwQYMBaAFOdbZP7LaMbgeZYPuds2CeSonmYxMAwGA1UdEwQFMAMBAf8wDQYJKoZI\n" +
            "hvcNAQEFBQADQQBdrk6J9koyylMtl/zRfiMAc2zgeC825fgP6421NTxs1rjLs1HG\n" +
            "VcUyQ1/e7WQgOaBHi9TefUJi+4PSVSluOXon\n" +
            "-----END CERTIFICATE-----";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mComponent = DeviceAdminInfoTest.getReceiverComponent();
        mSecondComponent = DeviceAdminInfoTest.getSecondReceiverComponent();
        mDeviceAdmin =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        setBlankPassword();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        setBlankPassword();
    }

    private void setBlankPassword() {
        if (!mDeviceAdmin) {
            return;
        }
        // Reset the password to nothing for future tests...
        mDevicePolicyManager.setPasswordQuality(mComponent,
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 0);
        assertTrue(mDevicePolicyManager.resetPassword("", 0));
    }

    public void testGetActiveAdmins() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetActiveAdmins");
            return;
        }
        List<ComponentName> activeAdmins = mDevicePolicyManager.getActiveAdmins();
        assertFalse(activeAdmins.isEmpty());
        assertTrue(activeAdmins.contains(mComponent));
        assertTrue(mDevicePolicyManager.isAdminActive(mComponent));
    }

    public void testGetMaximumFailedPasswordsForWipe() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetMaximumFailedPasswordsForWipe");
            return;
        }
        mDevicePolicyManager.setMaximumFailedPasswordsForWipe(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getMaximumFailedPasswordsForWipe(mComponent));

        mDevicePolicyManager.setMaximumFailedPasswordsForWipe(mComponent, 5);
        assertEquals(5, mDevicePolicyManager.getMaximumFailedPasswordsForWipe(mComponent));
    }

    public void testPasswordQuality_something() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_something");
            return;
        }
        mDevicePolicyManager.setPasswordQuality(mComponent,
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING,
                mDevicePolicyManager.getPasswordQuality(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertTrue(mDevicePolicyManager.resetPassword("123", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 10);
        assertEquals(10, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd123", 0));

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        assertTrue(mDevicePolicyManager.resetPassword("123", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
    }

    public void testPasswordQuality_numeric() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_numeric");
            return;
        }
        mDevicePolicyManager.setPasswordQuality(mComponent,
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_NUMERIC,
                mDevicePolicyManager.getPasswordQuality(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertTrue(mDevicePolicyManager.resetPassword("123", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 10);
        assertEquals(10, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd123", 0));

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        assertTrue(mDevicePolicyManager.resetPassword("123", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
    }

    public void testPasswordQuality_alphabetic() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_alphabetic");
            return;
        }
        mDevicePolicyManager.setPasswordQuality(mComponent,
                DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC,
                mDevicePolicyManager.getPasswordQuality(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 10);
        assertEquals(10, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd123", 0));

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
    }

    public void testPasswordQuality_alphanumeric() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_alphanumeric");
            return;
        }
        mDevicePolicyManager.setPasswordQuality(mComponent,
                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC,
                mDevicePolicyManager.getPasswordQuality(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 10);
        assertEquals(10, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertFalse(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd123", 0));

        mDevicePolicyManager.setPasswordMinimumLength(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumLength(mComponent));
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());

        assertFalse(mDevicePolicyManager.resetPassword("123", 0));
        assertFalse(mDevicePolicyManager.resetPassword("abcd", 0));
        assertTrue(mDevicePolicyManager.resetPassword("abcd123", 0));
    }

    public void testPasswordQuality_complexUpperCase() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_complexUpperCase");
            return;
        }

        mDevicePolicyManager.setPasswordQuality(mComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, mDevicePolicyManager.getPasswordQuality(mComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum UpperCase=0";
        assertPasswordSucceeds("abc", caseDescription);
        assertPasswordSucceeds("aBc", caseDescription);
        assertPasswordSucceeds("ABC", caseDescription);
        assertPasswordSucceeds("ABCD", caseDescription);

        mDevicePolicyManager.setPasswordMinimumUpperCase(mComponent, 1);
        assertEquals(1, mDevicePolicyManager.getPasswordMinimumUpperCase(mComponent));
        caseDescription = "minimum UpperCase=1";
        assertPasswordFails("abc", caseDescription);
        assertPasswordSucceeds("aBc", caseDescription);
        assertPasswordSucceeds("ABC", caseDescription);
        assertPasswordSucceeds("ABCD", caseDescription);

        mDevicePolicyManager.setPasswordMinimumUpperCase(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumUpperCase(mComponent));
        caseDescription = "minimum UpperCase=3";
        assertPasswordFails("abc", caseDescription);
        assertPasswordFails("aBC", caseDescription);
        assertPasswordSucceeds("ABC", caseDescription);
        assertPasswordSucceeds("ABCD", caseDescription);
    }

    public void testPasswordQuality_complexLowerCase() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_complexLowerCase");
            return;
        }

        mDevicePolicyManager.setPasswordQuality(mComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, mDevicePolicyManager.getPasswordQuality(mComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum LowerCase=0";
        assertPasswordSucceeds("ABCD", caseDescription);
        assertPasswordSucceeds("aBC", caseDescription);
        assertPasswordSucceeds("abc", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);

        mDevicePolicyManager.setPasswordMinimumLowerCase(mComponent, 1);
        assertEquals(1, mDevicePolicyManager.getPasswordMinimumLowerCase(mComponent));
        caseDescription = "minimum LowerCase=1";
        assertPasswordFails("ABCD", caseDescription);
        assertPasswordSucceeds("aBC", caseDescription);
        assertPasswordSucceeds("abc", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);

        mDevicePolicyManager.setPasswordMinimumLowerCase(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumLowerCase(mComponent));
        caseDescription = "minimum LowerCase=3";
        assertPasswordFails("ABCD", caseDescription);
        assertPasswordFails("aBC", caseDescription);
        assertPasswordSucceeds("abc", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
    }

    public void testPasswordQuality_complexLetters() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_complexLetters");
            return;
        }

        mDevicePolicyManager.setPasswordQuality(mComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, mDevicePolicyManager.getPasswordQuality(mComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum Letters=0";
        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordSucceeds("a23", caseDescription);
        assertPasswordSucceeds("abc", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);

        mDevicePolicyManager.setPasswordMinimumLetters(mComponent, 1);
        assertEquals(1, mDevicePolicyManager.getPasswordMinimumLetters(mComponent));
        caseDescription = "minimum Letters=1";
        assertPasswordFails("1234", caseDescription);
        assertPasswordSucceeds("a23", caseDescription);
        assertPasswordSucceeds("abc", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);

        mDevicePolicyManager.setPasswordMinimumLetters(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumLetters(mComponent));
        caseDescription = "minimum Letters=3";
        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("a23", caseDescription);
        assertPasswordSucceeds("abc", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
    }

    public void testPasswordQuality_complexNumeric() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_complexNumeric");
            return;
        }

        mDevicePolicyManager.setPasswordQuality(mComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, mDevicePolicyManager.getPasswordQuality(mComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum Numeric=0";
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("1bc", caseDescription);
        assertPasswordSucceeds("123", caseDescription);
        assertPasswordSucceeds("1234", caseDescription);

        mDevicePolicyManager.setPasswordMinimumNumeric(mComponent, 1);
        assertEquals(1, mDevicePolicyManager.getPasswordMinimumNumeric(mComponent));
        caseDescription = "minimum Numeric=1";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordSucceeds("1bc", caseDescription);
        assertPasswordSucceeds("123", caseDescription);
        assertPasswordSucceeds("1234", caseDescription);

        mDevicePolicyManager.setPasswordMinimumNumeric(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumNumeric(mComponent));
        caseDescription = "minimum Numeric=3";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("1bc", caseDescription);
        assertPasswordSucceeds("123", caseDescription);
        assertPasswordSucceeds("1234", caseDescription);
    }

    public void testPasswordQuality_complexSymbols() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_complexSymbols");
            return;
        }

        mDevicePolicyManager.setPasswordQuality(mComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, mDevicePolicyManager.getPasswordQuality(mComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum Symbols=0";
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("_bc", caseDescription);
        assertPasswordSucceeds("@#!", caseDescription);
        assertPasswordSucceeds("_@#!", caseDescription);

        mDevicePolicyManager.setPasswordMinimumSymbols(mComponent, 1);
        assertEquals(1, mDevicePolicyManager.getPasswordMinimumSymbols(mComponent));
        caseDescription = "minimum Symbols=1";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordSucceeds("_bc", caseDescription);
        assertPasswordSucceeds("@#!", caseDescription);
        assertPasswordSucceeds("_@#!", caseDescription);

        mDevicePolicyManager.setPasswordMinimumSymbols(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumSymbols(mComponent));
        caseDescription = "minimum Symbols=3";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("_bc", caseDescription);
        assertPasswordSucceeds("@#!", caseDescription);
        assertPasswordSucceeds("_@#!", caseDescription);
    }

    public void testPasswordQuality_complexNonLetter() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordQuality_complexNonLetter");
            return;
        }

        mDevicePolicyManager.setPasswordQuality(mComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, mDevicePolicyManager.getPasswordQuality(mComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum NonLetter=0";
        assertPasswordSucceeds("Abcd", caseDescription);
        assertPasswordSucceeds("_bcd", caseDescription);
        assertPasswordSucceeds("3bcd", caseDescription);
        assertPasswordSucceeds("_@3c", caseDescription);
        assertPasswordSucceeds("_25!", caseDescription);

        mDevicePolicyManager.setPasswordMinimumNonLetter(mComponent, 1);
        assertEquals(1, mDevicePolicyManager.getPasswordMinimumNonLetter(mComponent));
        caseDescription = "minimum NonLetter=1";
        assertPasswordFails("Abcd", caseDescription);
        assertPasswordSucceeds("_bcd", caseDescription);
        assertPasswordSucceeds("3bcd", caseDescription);
        assertPasswordSucceeds("_@3c", caseDescription);
        assertPasswordSucceeds("_25!", caseDescription);

        mDevicePolicyManager.setPasswordMinimumNonLetter(mComponent, 3);
        assertEquals(3, mDevicePolicyManager.getPasswordMinimumNonLetter(mComponent));
        caseDescription = "minimum NonLetter=3";
        assertPasswordFails("Abcd", caseDescription);
        assertPasswordFails("_bcd", caseDescription);
        assertPasswordFails("3bcd", caseDescription);
        assertPasswordSucceeds("c_@3c", caseDescription);
        assertPasswordSucceeds("_25!", caseDescription);
    }

    public void testPasswordHistoryLength() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordHistoryLength");
            return;
        }
        // Password history length restriction is only imposed if password quality is at least
        // numeric.
        mDevicePolicyManager.setPasswordQuality(mComponent,
                DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);
        int originalValue = mDevicePolicyManager.getPasswordHistoryLength(mComponent);
        try {
            mDevicePolicyManager.setPasswordHistoryLength(mComponent, 3);
            assertEquals(3, mDevicePolicyManager.getPasswordHistoryLength(mComponent));
            // Although it would make sense we cannot test if password history restrictions
            // are enforced as DevicePolicyManagerService.resetPassword fails to do so at the
            // moment. See b/17707820
        } finally {
            mDevicePolicyManager.setPasswordHistoryLength(mComponent, originalValue);
        }
    }

    public void testPasswordExpirationTimeout() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testPasswordExpirationTimeout");
            return;
        }
        long originalValue = mDevicePolicyManager.getPasswordExpirationTimeout(mComponent);
        try {
            for (long testLength : new long[] {
                    0L, 864000000L /* ten days */, 8640000000L /* 100 days */}) {
                mDevicePolicyManager.setPasswordExpirationTimeout(mComponent, testLength);
                assertEquals(testLength,
                        mDevicePolicyManager.getPasswordExpirationTimeout(mComponent));
            }
        } finally {
            mDevicePolicyManager.setPasswordExpirationTimeout(mComponent, originalValue);
        }
    }

    public void testCreateUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testCreateUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.createUser(mComponent, "user name");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testRemoveUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRemoveUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.removeUser(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetApplicationHidden_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetApplicationHidden_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setApplicationHidden(mComponent, "com.google.anything", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsApplicationHidden_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsApplicationHidden_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isApplicationHidden(mComponent, "com.google.anything");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetGlobalSetting_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetGlobalSetting_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setGlobalSetting(mComponent,
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, "1");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetSecureSetting_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSecureSetting_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setSecureSetting(mComponent,
                    Settings.Secure.INSTALL_NON_MARKET_APPS, "1");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setMasterVolumeMuted(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsMasterVolumeMuted_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isMasterVolumeMuted(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetRecommendedGlobalProxy_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetRecommendedGlobalProxy_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setRecommendedGlobalProxy(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetLockTaskPackages_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetLockTaskPackages_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setLockTaskPackages(mComponent, new String[] {"package"});
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
        }
    }

    public void testClearDeviceOwnerApp_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testClearDeviceOwnerApp_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearDeviceOwnerApp("android.deviceadmin.cts");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSwitchUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSwitchUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.switchUser(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testCreateAndInitializeUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testCreateAndInitializeUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.createAndInitializeUser(mComponent, "name", "admin name",
                        mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testInstallCaCert_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testInstallCaCert_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.installCaCert(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testUninstallCaCert_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallCaCert_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.uninstallCaCert(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testGetInstalledCaCerts_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetInstalledCaCerts_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.getInstalledCaCerts(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testHasCaCertInstalled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testHasCaCertInstalled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.hasCaCertInstalled(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testUninstallAllUserCaCerts_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallAllUserCaCerts_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.uninstallAllUserCaCerts(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetScreenCaptureDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetScreenCaptureDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setScreenCaptureDisabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetAutoTimeRequired_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetAutoTimeRequired_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setAutoTimeRequired(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testAddPersistentPreferredActivity_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testAddPersistentPreferredActivity_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.addPersistentPreferredActivity(mComponent,
                    new IntentFilter(Intent.ACTION_MAIN),
                    new ComponentName("android.admin.cts", "dummy"));
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testClearPackagePersistentPreferredActivities_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testClearPackagePersistentPreferredActivities_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(mComponent,
                    "android.admin.cts");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetApplicationRestrictions_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetApplicationRestrictions_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setApplicationRestrictions(mComponent,
                    "android.admin.cts", null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testAddUserRestriction_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testAddUserRestriction_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.addUserRestriction(mComponent,
                    UserManager.DISALLOW_SMS);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetAccountManagementDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetAccountManagementDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setAccountManagementDisabled(mComponent,
                    "dummy", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetRestrictionsProvider_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetRestrictionsProvider_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setRestrictionsProvider(mComponent,
                    new ComponentName("android.admin.cts", "dummy"));
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetUninstallBlocked_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetUninstallBlocked_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setUninstallBlocked(mComponent,
                    "android.admin.cts", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetPermittedAccessibilityServices_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetPermittedAccessibilityServices_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setPermittedAccessibilityServices(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetPermittedInputMethods_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetPermittedInputMethods_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setPermittedInputMethods(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    private void assertDeviceOwnerMessage(String message) {
        assertTrue("message is: "+ message, message.contains("does not own the device")
                || message.contains("can only be called by the device owner"));
    }

    private void assertProfileOwnerMessage(String message) {
        assertTrue("message is: "+ message,
                message.contains("does not own the profile"));
    }

    private void resetComplexPasswordRestrictions() {
        /**
         * Not enough to reset only mComponent as
         * {@link DevicePolicyManager#resetPassword(String, int)} checks restrictions across all
         * admin components.
         */
        for (ComponentName adminComponent : new ComponentName[] {mComponent, mSecondComponent}) {
            mDevicePolicyManager.setPasswordMinimumLength(adminComponent, 0);
            mDevicePolicyManager.setPasswordMinimumUpperCase(adminComponent, 0);
            mDevicePolicyManager.setPasswordMinimumLowerCase(adminComponent, 0);
            mDevicePolicyManager.setPasswordMinimumLetters(adminComponent, 0);
            mDevicePolicyManager.setPasswordMinimumNumeric(adminComponent, 0);
            mDevicePolicyManager.setPasswordMinimumSymbols(adminComponent, 0);
            mDevicePolicyManager.setPasswordMinimumNonLetter(adminComponent, 0);
        }
    }

    private void assertPasswordFails(String password, String restriction) {
        boolean passwordResetResult = mDevicePolicyManager.resetPassword(password, /* flags= */0);
        assertFalse("Password '" + password + "' should have failed on " + restriction,
                passwordResetResult);
    }

    private void assertPasswordSucceeds(String password, String restriction) {
        boolean passwordResetResult = mDevicePolicyManager.resetPassword(password, /* flags= */0);
        assertTrue("Password '" + password + "' failed on " + restriction, passwordResetResult);
        assertTrue(mDevicePolicyManager.isActivePasswordSufficient());
    }
}
