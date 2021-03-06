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
package android.uirendering.cts.bitmapverifiers;

/**
 * Checks to see if a bitmap is entirely a single color
 */
public class ColorVerifier extends PerPixelBitmapVerifier {
    private int mColor;

    public ColorVerifier(int color) {
        this(color, 20);
    }

    public ColorVerifier(int color, int threshold) {
        super(threshold);
        mColor = color;
    }

    @Override
    protected int getExpectedColor(int x, int y) {
        return mColor;
    }
}
