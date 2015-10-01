/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.permission2.cts;

import android.content.Intent;
import android.test.AndroidTestCase;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Verify that applications can not send protected broadcasts.
 */
public class ProtectedBroadcastsTest extends AndroidTestCase {
    private static final String BROADCASTS[] = new String[] {
        "android.app.action.DEVICE_OWNER_CHANGED",
        "android.app.action.ENTER_CAR_MODE",
        "android.app.action.ENTER_DESK_MODE",
        "android.app.action.EXIT_CAR_MODE",
        "android.app.action.EXIT_DESK_MODE",
        "android.app.action.NEXT_ALARM_CLOCK_CHANGED",
        "android.app.action.SYSTEM_UPDATE_POLICY_CHANGED",
        "android.appwidget.action.APPWIDGET_DELETED",
        "android.appwidget.action.APPWIDGET_DISABLED",
        "android.appwidget.action.APPWIDGET_ENABLED",
        "android.appwidget.action.APPWIDGET_HOST_RESTORED",
        "android.appwidget.action.APPWIDGET_RESTORED",
        "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS",
        "android.backup.intent.CLEAR",
        "android.backup.intent.INIT",
        "android.backup.intent.RUN",
        "android.bluetooth.a2dp-sink.profile.action.AUDIO_CONFIG_CHANGED",
        "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED",
        "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED",
        "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.adapter.action.DISCOVERY_FINISHED",
        "android.bluetooth.adapter.action.DISCOVERY_STARTED",
        "android.bluetooth.adapter.action.LOCAL_NAME_CHANGED",
        "android.bluetooth.adapter.action.SCAN_MODE_CHANGED",
        "android.bluetooth.adapter.action.STATE_CHANGED",
        "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.device.action.ACL_CONNECTED",
        "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED",
        "android.bluetooth.device.action.ACL_DISCONNECTED",
        "android.bluetooth.device.action.ALIAS_CHANGED",
        "android.bluetooth.device.action.BOND_STATE_CHANGED",
        "android.bluetooth.device.action.CLASS_CHANGED",
        "android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL",
        "android.bluetooth.device.action.CONNECTION_ACCESS_REPLY",
        "android.bluetooth.device.action.CONNECTION_ACCESS_REQUEST",
        "android.bluetooth.device.action.DISAPPEARED",
        "android.bluetooth.device.action.FOUND",
        "android.bluetooth.device.action.MAS_INSTANCE",
        "android.bluetooth.device.action.NAME_CHANGED",
        "android.bluetooth.device.action.NAME_FAILED",
        "android.bluetooth.device.action.PAIRING_CANCEL",
        "android.bluetooth.device.action.PAIRING_REQUEST",
        "android.bluetooth.device.action.UUID",
        "android.bluetooth.devicepicker.action.DEVICE_SELECTED",
        "android.bluetooth.devicepicker.action.LAUNCH",
        "android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT",
        "android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED",
        "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED",
        "android.bluetooth.headsetclient.profile.action.AG_EVENT",
        "android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED",
        "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.headsetclient.profile.action.LAST_VTAG",
        "android.bluetooth.headsetclient.profile.action.RESULT",
        "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.input.profile.action.PROTOCOL_MODE_CHANGED",
        "android.bluetooth.input.profile.action.VIRTUAL_UNPLUG_STATUS",
        "android.bluetooth.pan.profile.action.CONNECTION_STATE_CHANGED",
        "android.bluetooth.pbap.intent.action.PBAP_STATE_CHANGED",
        "android.btopp.intent.action.CONFIRM",
        "android.btopp.intent.action.HIDE_COMPLETE",
        "android.btopp.intent.action.HIDE",
        "android.btopp.intent.action.INCOMING_FILE_NOTIFICATION",
        "android.btopp.intent.action.LIST",
        "android.btopp.intent.action.OPEN_INBOUND",
        "android.btopp.intent.action.OPEN_OUTBOUND",
        "android.btopp.intent.action.OPEN",
        "android.btopp.intent.action.RETRY",
        "android.btopp.intent.action.USER_CONFIRMATION_TIMEOUT",
        "android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED",
        "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
        "android.hardware.usb.action.USB_DEVICE_ATTACHED",
        "android.hardware.usb.action.USB_PORT_CHANGED",
        "android.hardware.usb.action.USB_STATE",
        "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED",
        "android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED",
        "android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED",
        "android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED",
        "android.intent.action.ACTION_IDLE_MAINTENANCE_END",
        "android.intent.action.ACTION_IDLE_MAINTENANCE_START",
        "android.intent.action.ACTION_POWER_CONNECTED",
        "android.intent.action.ACTION_POWER_DISCONNECTED",
        "android.intent.action.ACTION_SET_RADIO_CAPABILITY_DONE",
        "android.intent.action.ACTION_SET_RADIO_CAPABILITY_FAILED",
        "android.intent.action.ACTION_SHUTDOWN",
        "android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE",
        "android.intent.action.ACTION_SUBINFO_RECORD_UPDATED",
        "android.intent.action.ADVANCED_SETTINGS",
        "android.intent.action.AIRPLANE_MODE",
        "android.intent.action.ANY_DATA_STATE",
        "android.intent.action.APPLICATION_RESTRICTIONS_CHANGED",
        "android.intent.action.BATTERY_CHANGED",
        "android.intent.action.BATTERY_LOW",
        "android.intent.action.BATTERY_OKAY",
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.BUGREPORT_FINISHED",
        "android.intent.action.CHARGING",
        "android.intent.action.CLEAR_DNS_CACHE",
        "android.intent.action.CONFIGURATION_CHANGED",
        "android.intent.action.DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN",
        "android.intent.action.DATE_CHANGED",
        "android.intent.action.DEVICE_STORAGE_FULL",
        "android.intent.action.DEVICE_STORAGE_LOW",
        "android.intent.action.DEVICE_STORAGE_NOT_FULL",
        "android.intent.action.DEVICE_STORAGE_OK",
        "android.intent.action.DISCHARGING",
        "android.intent.action.DOCK_EVENT",
        "android.intent.action.DREAMING_STARTED",
        "android.intent.action.DREAMING_STOPPED",
        "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE",
        "android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE",
        "android.intent.action.HDMI_PLUGGED",
        "android.intent.action.HEADSET_PLUG",
        "android.intent.action.INTENT_FILTER_NEEDS_VERIFICATION",
        "android.intent.action.LOCALE_CHANGED",
        "android.intent.action.MASTER_CLEAR_NOTIFICATION",
        "android.intent.action.MEDIA_BAD_REMOVAL",
        "android.intent.action.MEDIA_CHECKING",
        "android.intent.action.MEDIA_EJECT",
        "android.intent.action.MEDIA_MOUNTED",
        "android.intent.action.MEDIA_NOFS",
        "android.intent.action.MEDIA_REMOVED",
        "android.intent.action.MEDIA_SHARED",
        "android.intent.action.MEDIA_UNMOUNTABLE",
        "android.intent.action.MEDIA_UNMOUNTED",
        "android.intent.action.MEDIA_UNSHARED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.NEW_OUTGOING_CALL",
        "android.intent.action.PACKAGE_ADDED",
        "android.intent.action.PACKAGE_CHANGED",
        "android.intent.action.PACKAGE_DATA_CLEARED",
        "android.intent.action.PACKAGE_FIRST_LAUNCH",
        "android.intent.action.PACKAGE_FULLY_REMOVED",
        "android.intent.action.PACKAGE_INSTALL",
        "android.intent.action.PACKAGE_NEEDS_VERIFICATION",
        "android.intent.action.PACKAGE_REMOVED",
        "android.intent.action.PACKAGE_REPLACED",
        "android.intent.action.PACKAGE_RESTARTED",
        "android.intent.action.PACKAGE_VERIFIED",
        "android.intent.action.PERMISSION_RESPONSE_RECEIVED",
        "android.intent.action.PHONE_STATE",
        "android.intent.action.PROXY_CHANGE",
        "android.intent.action.QUERY_PACKAGE_RESTART",
        "android.intent.action.REBOOT",
        "android.intent.action.REQUEST_PERMISSION",
        "android.intent.action.SCREEN_OFF",
        "android.intent.action.SCREEN_ON",
        "android.intent.action.SUB_DEFAULT_CHANGED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIME_TICK",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.UID_REMOVED",
        "android.intent.action.USER_ADDED",
        "android.intent.action.USER_BACKGROUND",
        "android.intent.action.USER_FOREGROUND",
        "android.intent.action.USER_PRESENT",
        "android.intent.action.USER_REMOVED",
        "android.intent.action.USER_STARTED",
        "android.intent.action.USER_STARTING",
        "android.intent.action.USER_STOPPED",
        "android.intent.action.USER_STOPPING",
        "android.intent.action.USER_SWITCHED",
        "android.internal.policy.action.BURN_IN_PROTECTION",
        "android.location.GPS_ENABLED_CHANGE",
        "android.location.GPS_FIX_CHANGE",
        "android.location.MODE_CHANGED",
        "android.location.PROVIDERS_CHANGED",
        "android.media.ACTION_SCO_AUDIO_STATE_UPDATED",
        "android.media.action.HDMI_AUDIO_PLUG",
        "android.media.AUDIO_BECOMING_NOISY",
        "android.media.MASTER_MUTE_CHANGED_ACTION",
        "android.media.MASTER_VOLUME_CHANGED_ACTION",
        "android.media.RINGER_MODE_CHANGED",
        "android.media.SCO_AUDIO_STATE_CHANGED",
        "android.media.VIBRATE_SETTING_CHANGED",
        "android.media.VOLUME_CHANGED_ACTION",
        "android.net.conn.BACKGROUND_DATA_SETTING_CHANGED",
        "android.net.conn.CAPTIVE_PORTAL_TEST_COMPLETED",
        "android.net.conn.CAPTIVE_PORTAL",
        "android.net.conn.CONNECTIVITY_CHANGE_IMMEDIATE",
        "android.net.conn.CONNECTIVITY_CHANGE",
        "android.net.conn.DATA_ACTIVITY_CHANGE",
        "android.net.conn.INET_CONDITION_ACTION",
        "android.net.conn.NETWORK_CONDITIONS_MEASURED",
        "android.net.conn.TETHER_STATE_CHANGED",
        "android.net.ConnectivityService.action.PKT_CNT_SAMPLE_INTERVAL_ELAPSED",
        "android.net.nsd.STATE_CHANGED",
        "android.net.proxy.PAC_REFRESH",
        "android.net.scoring.SCORE_NETWORKS",
        "android.net.scoring.SCORER_CHANGED",
        "android.net.wifi.CONFIGURED_NETWORKS_CHANGE",
        "android.net.wifi.LINK_CONFIGURATION_CHANGED",
        "android.net.wifi.p2p.CONNECTION_STATE_CHANGE",
        "android.net.wifi.p2p.DISCOVERY_STATE_CHANGE",
        "android.net.wifi.p2p.PEERS_CHANGED",
        "android.net.wifi.p2p.PERSISTENT_GROUPS_CHANGED",
        "android.net.wifi.p2p.STATE_CHANGED",
        "android.net.wifi.p2p.THIS_DEVICE_CHANGED",
        "android.net.wifi.RSSI_CHANGED",
        "android.net.wifi.SCAN_RESULTS",
        "android.net.wifi.STATE_CHANGE",
        "android.net.wifi.supplicant.CONNECTION_CHANGE",
        "android.net.wifi.supplicant.STATE_CHANGE",
        "android.net.wifi.WIFI_AP_STATE_CHANGED",
        "android.net.wifi.WIFI_CREDENTIAL_CHANGED",
        "android.net.wifi.WIFI_SCAN_AVAILABLE",
        "android.net.wifi.WIFI_STATE_CHANGED",
        "android.nfc.action.LLCP_LINK_STATE_CHANGED",
        "android.nfc.action.TRANSACTION_DETECTED",
        "android.nfc.handover.intent.action.HANDOVER_STARTED",
        "android.nfc.handover.intent.action.TRANSFER_DONE",
        "android.nfc.handover.intent.action.TRANSFER_PROGRESS",
        "android.os.action.DEVICE_IDLE_MODE_CHANGED",
        "android.os.action.POWER_SAVE_MODE_CHANGED",
        "android.os.action.POWER_SAVE_MODE_CHANGING",
        "android.os.action.POWER_SAVE_TEMP_WHITELIST_CHANGED",
        "android.os.action.POWER_SAVE_WHITELIST_CHANGED",
        "android.os.action.SCREEN_BRIGHTNESS_BOOST_CHANGED",
        "android.os.action.SETTING_RESTORED",
        "android.os.UpdateLock.UPDATE_LOCK_CHANGED",
        "android.telecom.action.DEFAULT_DIALER_CHANGED",
        "com.android.bluetooth.pbap.authcancelled",
        "com.android.bluetooth.pbap.authchall",
        "com.android.bluetooth.pbap.authresponse",
        "com.android.bluetooth.pbap.userconfirmtimeout",
        "com.android.nfc_extras.action.AID_SELECTED",
        "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED",
        "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED",
        "com.android.server.connectivityservice.CONNECTED_TO_PROVISIONING_NETWORK_ACTION",
        "com.android.server.WifiManager.action.DELAYED_DRIVER_STOP",
        "com.android.server.WifiManager.action.START_PNO",
        "com.android.server.WifiManager.action.START_SCAN"
    };
    private static final String PHONE_BROADCASTS[] = new String[] {
        "android.intent.action.ACTION_MDN_STATE_CHANGED",
        "android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS",
        "android.intent.action.ANY_DATA_STATE",
        "android.intent.action.DATA_CONNECTION_FAILED",
        "android.intent.action.DATA_SMS_RECEIVED",
        "android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED",
        "android.intent.action.NETWORK_SET_TIME",
        "android.intent.action.NETWORK_SET_TIMEZONE",
        "android.intent.action.RADIO_TECHNOLOGY",
        "android.intent.action.SERVICE_STATE",
        "android.intent.action.SIG_STR",
        "android.intent.action.SIM_STATE_CHANGED",
        "android.intent.action.stk.alpha_notify",
        "android.intent.action.stk.command",
        "android.intent.action.stk.icc_status_change",
        "android.intent.action.stk.session_end",
        "android.provider.Telephony.SIM_FULL",
        "android.provider.Telephony.SMS_CB_RECEIVED",
        "android.provider.Telephony.SMS_DELIVER",
        "android.provider.Telephony.SMS_EMERGENCY_CB_RECEIVED",
        "android.provider.Telephony.SMS_RECEIVED",
        "android.provider.Telephony.SPN_STRINGS_UPDATED",
        "android.provider.Telephony.WAP_PUSH_DELIVER",
        "android.provider.Telephony.WAP_PUSH_RECEIVED",
        "com.android.internal.telephony.data-restart-trysetup",
        "com.android.internal.telephony.data-stall"
    };
    private static final String TELECOM_BROADCASTS[] = new String[] {
        "android.intent.action.SHOW_MISSED_CALLS_NOTIFICATION"
    };

    /**
     * Verify that protected broadcast actions can't be sent.
     */
    public void testSendProtectedBroadcasts() {
        for (String action : BROADCASTS) {
            try {
                Intent intent = new Intent(action);
                getContext().sendBroadcast(intent);
                fail("expected security exception broadcasting action: " + action);
            } catch (SecurityException expected) {
                assertNotNull("security exception's error message.", expected.getMessage());
            }
        }
        try {
            PackageInfo packageInfo =
                    getPackageManager().getPackageInfo("com.android.phone", 0);
            for (String action : PHONE_BROADCASTS) {
                try {
                    Intent intent = new Intent(action);
                    getContext().sendBroadcast(intent);
                    fail("expected security exception broadcasting com.android.phone action: " +
                            action);
                } catch (SecurityException expected) {
                    assertNotNull("security exception's error message.", expected.getMessage());
                }
            }
        } catch (NameNotFoundException e) {
          // this catch intentionally left blank
        }
        try {
            PackageInfo packageInfo =
                    getPackageManager().getPackageInfo("com.android.server.telecom", 0);
            for (String action : TELECOM_BROADCASTS) {
                try {
                    Intent intent = new Intent(action);
                    getContext().sendBroadcast(intent);
                    fail("expected security exception broadcasting telcom action: " + action);
                } catch (SecurityException expected) {
                    assertNotNull("security exception's error message.", expected.getMessage());
                }
            }
        } catch (NameNotFoundException e) {
          // this catch intentionally left empty
        }
    }
}
