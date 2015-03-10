/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Size;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class ItsUtils {
    public static final String TAG = ItsUtils.class.getSimpleName();

    public static ByteBuffer jsonToByteBuffer(JSONObject jsonObj) {
        return ByteBuffer.wrap(jsonObj.toString().getBytes(Charset.defaultCharset()));
    }

    public static MeteringRectangle[] getJsonWeightedRectsFromArray(
            JSONArray a, boolean normalized, int width, int height)
            throws ItsException {
        try {
            // Returns [x0,y0,x1,y1,wgt,  x0,y0,x1,y1,wgt,  x0,y0,x1,y1,wgt,  ...]
            assert(a.length() % 5 == 0);
            MeteringRectangle[] ma = new MeteringRectangle[a.length() / 5];
            for (int i = 0; i < a.length(); i += 5) {
                int x,y,w,h;
                if (normalized) {
                    x = (int)Math.floor(a.getDouble(i+0) * width + 0.5f);
                    y = (int)Math.floor(a.getDouble(i+1) * height + 0.5f);
                    w = (int)Math.floor(a.getDouble(i+2) * width + 0.5f);
                    h = (int)Math.floor(a.getDouble(i+3) * height + 0.5f);
                } else {
                    x = a.getInt(i+0);
                    y = a.getInt(i+1);
                    w = a.getInt(i+2);
                    h = a.getInt(i+3);
                }
                x = Math.max(x, 0);
                y = Math.max(y, 0);
                w = Math.min(w, width-x);
                h = Math.min(h, height-y);
                int wgt = a.getInt(i+4);
                ma[i/5] = new MeteringRectangle(x,y,w,h,wgt);
            }
            return ma;
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    public static JSONArray getOutputSpecs(JSONObject jsonObjTop)
            throws ItsException {
        try {
            if (jsonObjTop.has("outputSurfaces")) {
                return jsonObjTop.getJSONArray("outputSurfaces");
            }
            return null;
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    public static Size[] getRawOutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.RAW_SENSOR);
    }

    public static Size[] getJpegOutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.JPEG);
    }

    public static Size[] getYuvOutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.YUV_420_888);
    }

    private static Size[] getOutputSizes(CameraCharacteristics ccs, int format)
            throws ItsException {
        StreamConfigurationMap configMap = ccs.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (configMap == null) {
            throw new ItsException("Failed to get stream config");
        }
        return configMap.getOutputSizes(format);
    }

    public static byte[] getDataFromImage(Image image)
            throws ItsException {
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = null;

        // Read image data
        Plane[] planes = image.getPlanes();

        // Check image validity
        if (!checkAndroidImageFormat(image)) {
            throw new ItsException(
                    "Invalid image format passed to getDataFromImage: " + image.getFormat());
        }

        if (format == ImageFormat.JPEG) {
            // JPEG doesn't have pixelstride and rowstride, treat it as 1D buffer.
            ByteBuffer buffer = planes[0].getBuffer();
            data = new byte[buffer.capacity()];
            buffer.get(data);
            return data;
        } else if (format == ImageFormat.YUV_420_888 || format == ImageFormat.RAW_SENSOR
                || format == ImageFormat.RAW10) {
            int offset = 0;
            data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
            int maxRowSize = planes[0].getRowStride();
            for (int i = 0; i < planes.length; i++) {
                if (maxRowSize < planes[i].getRowStride()) {
                    maxRowSize = planes[i].getRowStride();
                }
            }
            byte[] rowData = new byte[maxRowSize];
            for (int i = 0; i < planes.length; i++) {
                ByteBuffer buffer = planes[i].getBuffer();
                int rowStride = planes[i].getRowStride();
                int pixelStride = planes[i].getPixelStride();
                int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
                Logt.i(TAG, String.format(
                        "Reading image: fmt %d, plane %d, w %d, h %d, rowStride %d, pixStride %d",
                        format, i, width, height, rowStride, pixelStride));
                // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
                int w = (i == 0) ? width : width / 2;
                int h = (i == 0) ? height : height / 2;
                for (int row = 0; row < h; row++) {
                    if (pixelStride == bytesPerPixel) {
                        // Special case: optimized read of the entire row
                        int length = w * bytesPerPixel;
                        buffer.get(data, offset, length);
                        // Advance buffer the remainder of the row stride
                        if (row < h - 1) {
                            buffer.position(buffer.position() + rowStride - length);
                        }
                        offset += length;
                    } else {
                        // Generic case: should work for any pixelStride but slower.
                        // Use intermediate buffer to avoid read byte-by-byte from
                        // DirectByteBuffer, which is very bad for performance.
                        // Also need avoid access out of bound by only reading the available
                        // bytes in the bytebuffer.
                        int readSize = rowStride;
                        if (buffer.remaining() < readSize) {
                            readSize = buffer.remaining();
                        }
                        buffer.get(rowData, 0, readSize);
                        if (pixelStride >= 1) {
                            for (int col = 0; col < w; col++) {
                                data[offset++] = rowData[col * pixelStride];
                            }
                        } else {
                            // PixelStride of 0 can mean pixel isn't a multiple of 8 bits, for
                            // example with RAW10. Just copy the buffer, dropping any padding at
                            // the end of the row.
                            int length = (w * ImageFormat.getBitsPerPixel(format)) / 8;
                            System.arraycopy(rowData,0,data,offset,length);
                            offset += length;
                        }
                    }
                }
            }
            Logt.i(TAG, String.format("Done reading image, format %d", format));
            return data;
        } else {
            throw new ItsException("Unsupported image format: " + format);
        }
    }

    private static boolean checkAndroidImageFormat(Image image) {
        int format = image.getFormat();
        Plane[] planes = image.getPlanes();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return 3 == planes.length;
            case ImageFormat.RAW_SENSOR:
            case ImageFormat.RAW10:
            case ImageFormat.JPEG:
                return 1 == planes.length;
            default:
                return false;
        }
    }
}

