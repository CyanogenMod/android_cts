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
package com.android.compatibility.common.deviceinfo;

import android.media.CamcorderProfile;

import com.android.compatibility.common.util.InfoStore;

/**
 * Camera information collector.
 */
public final class CameraDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(InfoStore store) throws Exception {
        store.addResult("profile_480p", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P));
        store.addResult("profile_720p", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P));
        store.addResult("profile_1080p", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P));
        store.addResult("profile_cif", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_CIF));
        store.addResult("profile_qcif", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QCIF));
        store.addResult("profile_qvga", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA));
    }
}
