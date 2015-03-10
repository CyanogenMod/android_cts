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

package com.android.cts.videoperf;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.media.cts.CodecImage;
import android.media.cts.CodecUtils;
import android.media.Image;
import android.media.Image.Plane;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.util.Log;

import android.cts.util.CtsAndroidTestCase;
import com.android.cts.util.ResultType;
import com.android.cts.util.ResultUnit;
import com.android.cts.util.Stat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.lang.System;
import java.util.Random;
import java.util.Vector;

/**
 * This tries to test video encoder / decoder performance by running encoding / decoding
 * without displaying the raw data. To make things simpler, encoder is used to encode synthetic
 * data and decoder is used to decode the encoded video. This approach does not work where
 * there is only decoder. Performance index is total time taken for encoding and decoding
 * the whole frames.
 * To prevent sacrificing quality for faster encoding / decoding, randomly selected pixels are
 * compared with the original image. As the pixel comparison can slow down the decoding process,
 * only some randomly selected pixels are compared. As there can be only one performance index,
 * error above certain threshold in pixel value will be treated as an error.
 */
public class VideoEncoderDecoderTest extends CtsAndroidTestCase {
    private static final String TAG = "VideoEncoderDecoderTest";
    // this wait time affects fps as too big value will work as a blocker if device fps
    // is not very high.
    private static final long VIDEO_CODEC_WAIT_TIME_US = 5000;
    private static final boolean VERBOSE = false;
    private static final String VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int TOTAL_FRAMES = 300;
    private static final int NUMBER_OF_REPEAT = 10;
    // i frame interval for encoder
    private static final int KEY_I_FRAME_INTERVAL = 5;

    private static final int Y_CLAMP_MIN = 16;
    private static final int Y_CLAMP_MAX = 235;
    private static final int YUV_PLANE_ADDITIONAL_LENGTH = 200;
    private ByteBuffer mYBuffer, mYDirectBuffer;
    private ByteBuffer mUVBuffer, mUVDirectBuffer;
    private int mSrcColorFormat;
    private int mDstColorFormat;
    private int mBufferWidth;
    private int mBufferHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mFrameRate;

    private Vector<ByteBuffer> mEncodedOutputBuffer;
    // check this many pixels per each decoded frame
    // checking too many points decreases decoder frame rates a lot.
    private static final int PIXEL_CHECK_PER_FRAME = 1000;
    // RMS error in pixel values above this will be treated as error.
    private static final double PIXEL_RMS_ERROR_MARGAIN = 20.0;
    private Random mRandom;

    @Override
    protected void setUp() throws Exception {
        mEncodedOutputBuffer = new Vector<ByteBuffer>(TOTAL_FRAMES * 2);
        // Use time as a seed, hoping to prevent checking pixels in the same pattern
        long now = System.currentTimeMillis();
        mRandom = new Random(now);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        mEncodedOutputBuffer.clear();
        mEncodedOutputBuffer = null;
        mYBuffer = null;
        mUVBuffer = null;
        mYDirectBuffer = null;
        mUVDirectBuffer = null;
        mRandom = null;
        super.tearDown();
    }

    public void testAvc0176x0144() throws Exception {
        doTest(VIDEO_AVC, 176, 144, NUMBER_OF_REPEAT);
    }

    public void testAvc0352x0288() throws Exception {
        doTest(VIDEO_AVC, 352, 288, NUMBER_OF_REPEAT);
    }

    public void testAvc0720x0480() throws Exception {
        doTest(VIDEO_AVC, 720, 480, NUMBER_OF_REPEAT);
    }

    public void testAvc1280x0720() throws Exception {
        doTest(VIDEO_AVC, 1280, 720, NUMBER_OF_REPEAT);
    }

    /**
     * resolution intentionally set to 1072 not 1080
     * as 1080 is not multiple of 16, and it requires additional setting like stride
     * which is not specified in API documentation.
     */
    public void testAvc1920x1072() throws Exception {
        doTest(VIDEO_AVC, 1920, 1072, NUMBER_OF_REPEAT);
    }

    private boolean isSrcSemiPlanar() {
        return mSrcColorFormat == CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    private boolean isSrcFlexYUV() {
        return mSrcColorFormat == CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private boolean isDstSemiPlanar() {
        return mDstColorFormat == CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    private boolean isDstFlexYUV() {
        return mDstColorFormat == CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private static int getColorFormat(CodecInfo info) {
        if (info.mSupportSemiPlanar) {
            return CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        } else if (info.mSupportPlanar) {
            return CodecCapabilities.COLOR_FormatYUV420Planar;
        } else {
            // FlexYUV must be supported
            return CodecCapabilities.COLOR_FormatYUV420Flexible;
        }
    }

    /**
     * Run encoding / decoding test for given mimeType of codec
     * @param mimeType like video/avc
     * @param w video width
     * @param h video height
     * @param numberRepeat how many times to repeat the encoding / decoding process
     */
    private void doTest(String mimeType, int w, int h, int numberRepeat) throws Exception {
        CodecInfo infoEnc = CodecInfo.getSupportedFormatInfo(mimeType, w, h, true /* encoder */);
        if (infoEnc == null) {
            Log.i(TAG, "Encoder " + mimeType + " with " + w + "," + h + " not supported");
            return;
        }
        CodecInfo infoDec = CodecInfo.getSupportedFormatInfo(mimeType, w, h, false /* encoder */);
        assertNotNull(infoDec);
        mVideoWidth = w;
        mVideoHeight = h;

        mSrcColorFormat = getColorFormat(infoEnc);
        mDstColorFormat = getColorFormat(infoDec);
        Log.i(TAG, "Testing video resolution " + w + "x" + h +
                   ": enc format " + mSrcColorFormat +
                   ", dec format " + mDstColorFormat);

        initYUVPlane(w + YUV_PLANE_ADDITIONAL_LENGTH, h + YUV_PLANE_ADDITIONAL_LENGTH);
        double[] encoderFpsResults = new double[numberRepeat];
        double[] decoderFpsResults = new double[numberRepeat];
        double[] totalFpsResults = new double[numberRepeat];
        double[] decoderRmsErrorResults = new double[numberRepeat];
        boolean success = true;
        for (int i = 0; i < numberRepeat && success; i++) {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, mimeType);
            format.setInteger(MediaFormat.KEY_BIT_RATE, infoEnc.mBitRate);
            format.setInteger(MediaFormat.KEY_WIDTH, w);
            format.setInteger(MediaFormat.KEY_HEIGHT, h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mSrcColorFormat);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, infoEnc.mFps);
            mFrameRate = infoEnc.mFps;
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_I_FRAME_INTERVAL);
            double encodingTime = runEncoder(VIDEO_AVC, format, TOTAL_FRAMES);
            // re-initialize format for decoder
            format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, mimeType);
            format.setInteger(MediaFormat.KEY_WIDTH, w);
            format.setInteger(MediaFormat.KEY_HEIGHT, h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mDstColorFormat);
            double[] decoderResult = runDecoder(VIDEO_AVC, format);
            if (decoderResult == null) {
                success = false;
            } else {
                double decodingTime = decoderResult[0];
                decoderRmsErrorResults[i] = decoderResult[1];
                encoderFpsResults[i] = (double)TOTAL_FRAMES / encodingTime * 1000.0;
                decoderFpsResults[i] = (double)TOTAL_FRAMES / decodingTime * 1000.0;
                totalFpsResults[i] = (double)TOTAL_FRAMES / (encodingTime + decodingTime) * 1000.0;
            }

            // clear things for re-start
            mEncodedOutputBuffer.clear();
            // it will be good to clean everything to make every run the same.
            System.gc();
        }
        getReportLog().printArray("encoder", encoderFpsResults, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        getReportLog().printArray("rms error", decoderRmsErrorResults, ResultType.LOWER_BETTER,
                ResultUnit.NONE);
        getReportLog().printArray("decoder", decoderFpsResults, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        getReportLog().printArray("encoder decoder", totalFpsResults, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        getReportLog().printSummary("encoder decoder", Stat.getAverage(totalFpsResults),
                ResultType.HIGHER_BETTER, ResultUnit.FPS);
        // make sure that rms error is not too big.
        for (int i = 0; i < numberRepeat; i++) {
            assertTrue(decoderRmsErrorResults[i] < PIXEL_RMS_ERROR_MARGAIN);
        }
    }

    /**
     * run encoder benchmarking
     * @param mimeType encoder type like video/avc
     * @param format format of media to encode
     * @param totalFrames total number of frames to encode
     * @return time taken in ms to encode the frames. This does not include initialization time.
     */
    private double runEncoder(String mimeType, MediaFormat format, int totalFrames) {
        MediaCodec codec = null;
        try {
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String encoderName = mcl.findEncoderForFormat(format);
            codec = MediaCodec.createByCodecName(encoderName);
            codec.configure(
                    format,
                    null /* surface */,
                    null /* crypto */,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IllegalStateException e) {
            Log.e(TAG, "codec '" + mimeType + "' failed configuration.");
            codec.release();
            assertTrue("codec '" + mimeType + "' failed configuration.", false);
        } catch (IOException | NullPointerException e) {
            Log.i(TAG, "could not find codec for " + format);
            return Double.NaN;
        }
        codec.start();
        ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

        int numBytesSubmitted = 0;
        int numBytesDequeued = 0;
        int inFramesCount = 0;
        long start = System.currentTimeMillis();
        while (true) {
            int index;

            if (inFramesCount < totalFrames) {
                index = codec.dequeueInputBuffer(VIDEO_CODEC_WAIT_TIME_US /* timeoutUs */);
                if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    int size;
                    // when encoder only supports flexYUV, use Image only; otherwise,
                    // use ByteBuffer & Image each on half of the frames to test both
                    if (isSrcFlexYUV() || inFramesCount % 2 == 0) {
                        Image image = codec.getInputImage(index);
                        // image should always be available
                        assertTrue(image != null);
                        size = queueInputImageEncoder(
                                codec, image, index, inFramesCount,
                                (inFramesCount == (totalFrames - 1)) ?
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    } else {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        size = queueInputBufferEncoder(
                                codec, buffer, index, inFramesCount,
                                (inFramesCount == (totalFrames - 1)) ?
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    }
                    inFramesCount++;
                    numBytesSubmitted += size;
                    if (VERBOSE) {
                        Log.d(TAG, "queued " + size + " bytes of input data, frame " +
                                (inFramesCount - 1));
                    }

                }
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            index = codec.dequeueOutputBuffer(info, VIDEO_CODEC_WAIT_TIME_US /* timeoutUs */);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            } else if (index >= 0) {
                dequeueOutputBufferEncoder(codec, codecOutputBuffers, index, info);
                numBytesDequeued += info.size;
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (VERBOSE) {
                        Log.d(TAG, "dequeued output EOS.");
                    }
                    break;
                }
                if (VERBOSE) {
                    Log.d(TAG, "dequeued " + info.size + " bytes of output data.");
                }
            }
        }
        long finish = System.currentTimeMillis();
        if (VERBOSE) {
            Log.d(TAG, "queued a total of " + numBytesSubmitted + "bytes, "
                    + "dequeued " + numBytesDequeued + " bytes.");
        }
        codec.stop();
        codec.release();
        codec = null;
        return (double)(finish - start);
    }

    /**
     * Fills input buffer for encoder from YUV buffers.
     * @return size of enqueued data.
     */
    private int queueInputBufferEncoder(
            MediaCodec codec, ByteBuffer buffer, int index, int frameCount, int flags) {
        buffer.clear();

        Point origin = getOrigin(frameCount);
        // Y color first
        int srcOffsetY = origin.x + origin.y * mBufferWidth;
        final byte[] yBuffer = mYBuffer.array();
        for (int i = 0; i < mVideoHeight; i++) {
            buffer.put(yBuffer, srcOffsetY, mVideoWidth);
            srcOffsetY += mBufferWidth;
        }
        if (isSrcSemiPlanar()) {
            int srcOffsetU = origin.y / 2 * mBufferWidth + origin.x / 2 * 2;
            final byte[] uvBuffer = mUVBuffer.array();
            for (int i = 0; i < mVideoHeight / 2; i++) {
                buffer.put(uvBuffer, srcOffsetU, mVideoWidth);
                srcOffsetU += mBufferWidth;
            }
        } else {
            int srcOffsetU = origin.y / 2 * mBufferWidth / 2 + origin.x / 2;
            int srcOffsetV = srcOffsetU + mBufferWidth / 2 * mBufferHeight / 2;
            final byte[] uvBuffer = mUVBuffer.array();
            for (int i = 0; i < mVideoHeight / 2; i++) { //U only
                buffer.put(uvBuffer, srcOffsetU, mVideoWidth / 2);
                srcOffsetU += mBufferWidth / 2;
            }
            for (int i = 0; i < mVideoHeight / 2; i++) { //V only
                buffer.put(uvBuffer, srcOffsetV, mVideoWidth / 2);
                srcOffsetV += mBufferWidth / 2;
            }
        }
        int size = mVideoHeight * mVideoWidth * 3 / 2;
        long ptsUsec = computePresentationTime(frameCount);

        codec.queueInputBuffer(index, 0 /* offset */, size, ptsUsec /* timeUs */, flags);
        if (VERBOSE && (frameCount == 0)) {
            printByteArray("Y ", mYBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), mBufferWidth * 60, 20);
        }
        return size;
    }

    class YUVImage extends CodecImage {
        private final int mImageWidth;
        private final int mImageHeight;
        private final Plane[] mPlanes;

        YUVImage(
                Point origin,
                int imageWidth, int imageHeight,
                int arrayWidth, int arrayHeight,
                boolean semiPlanar,
                ByteBuffer bufferY, ByteBuffer bufferUV) {
            mImageWidth = imageWidth;
            mImageHeight = imageHeight;
            ByteBuffer dupY = bufferY.duplicate();
            ByteBuffer dupUV = bufferUV.duplicate();
            mPlanes = new Plane[3];

            int srcOffsetY = origin.x + origin.y * arrayWidth;

            mPlanes[0] = new YUVPlane(
                        mImageWidth, mImageHeight, arrayWidth, 1,
                        dupY, srcOffsetY);

            if (semiPlanar) {
                int srcOffsetUV = origin.y / 2 * arrayWidth + origin.x / 2 * 2;

                mPlanes[1] = new YUVPlane(
                        mImageWidth / 2, mImageHeight / 2, arrayWidth, 2,
                        dupUV, srcOffsetUV);
                mPlanes[2] = new YUVPlane(
                        mImageWidth / 2, mImageHeight / 2, arrayWidth, 2,
                        dupUV, srcOffsetUV + 1);
            } else {
                int srcOffsetU = origin.y / 2 * arrayWidth / 2 + origin.x / 2;
                int srcOffsetV = srcOffsetU + arrayWidth / 2 * arrayHeight / 2;

                mPlanes[1] = new YUVPlane(
                        mImageWidth / 2, mImageHeight / 2, arrayWidth / 2, 1,
                        dupUV, srcOffsetU);
                mPlanes[2] = new YUVPlane(
                        mImageWidth / 2, mImageHeight / 2, arrayWidth / 2, 1,
                        dupUV, srcOffsetV);
            }
        }

        @Override
        public int getFormat() {
            return ImageFormat.YUV_420_888;
        }

        @Override
        public int getWidth() {
            return mImageWidth;
        }

        @Override
        public int getHeight() {
            return mImageHeight;
        }

        @Override
        public long getTimestamp() {
            return 0;
        }

        @Override
        public Plane[] getPlanes() {
            return mPlanes;
        }

        @Override
        public void close() {
            mPlanes[0] = null;
            mPlanes[1] = null;
            mPlanes[2] = null;
        }

        class YUVPlane extends CodecImage.Plane {
            private final int mRowStride;
            private final int mPixelStride;
            private final ByteBuffer mByteBuffer;

            YUVPlane(int w, int h, int rowStride, int pixelStride,
                    ByteBuffer buffer, int offset) {
                mRowStride = rowStride;
                mPixelStride = pixelStride;

                // only safe to access length bytes starting from buffer[offset]
                int length = (h - 1) * rowStride + (w - 1) * pixelStride + 1;

                buffer.position(offset);
                mByteBuffer = buffer.slice();
                mByteBuffer.limit(length);
            }

            @Override
            public int getRowStride() {
                return mRowStride;
            }

            @Override
            public int getPixelStride() {
                return mPixelStride;
            }

            @Override
            public ByteBuffer getBuffer() {
                return mByteBuffer;
            }
        }
    }

    /**
     * Fills input image for encoder from YUV buffers.
     * @return size of enqueued data.
     */
    private int queueInputImageEncoder(
            MediaCodec codec, Image image, int index, int frameCount, int flags) {
        assertTrue(image.getFormat() == ImageFormat.YUV_420_888);


        Point origin = getOrigin(frameCount);

        // Y color first
        CodecImage srcImage = new YUVImage(
                origin,
                mVideoWidth, mVideoHeight,
                mBufferWidth, mBufferHeight,
                isSrcSemiPlanar(),
                mYDirectBuffer, mUVDirectBuffer);

        CodecUtils.copyFlexYUVImage(image, srcImage);

        int size = mVideoHeight * mVideoWidth * 3 / 2;
        long ptsUsec = computePresentationTime(frameCount);

        codec.queueInputBuffer(index, 0 /* offset */, size, ptsUsec /* timeUs */, flags);
        if (VERBOSE && (frameCount == 0)) {
            printByteArray("Y ", mYBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), mBufferWidth * 60, 20);
        }
        return size;
    }

    /**
     * Dequeue encoded data from output buffer and store for later usage.
     */
    private void dequeueOutputBufferEncoder(
            MediaCodec codec, ByteBuffer[] outputBuffers,
            int index, MediaCodec.BufferInfo info) {
        ByteBuffer output = outputBuffers[index];
        output.clear();
        int l = info.size;
        ByteBuffer copied = ByteBuffer.allocate(l);
        output.get(copied.array(), 0, l);
        mEncodedOutputBuffer.add(copied);
        codec.releaseOutputBuffer(index, false /* render */);
    }

    /**
     * run encoder benchmarking with encoded stream stored from encoding phase
     * @param mimeType encoder type like video/avc
     * @param format format of media to decode
     * @return returns length-2 array with 0: time for decoding, 1 : rms error of pixels
     */
    private double[] runDecoder(String mimeType, MediaFormat format) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String decoderName = mcl.findDecoderForFormat(format);
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createByCodecName(decoderName);
        } catch (IOException | NullPointerException e) {
            Log.i(TAG, "could not find codec for " + format);
            return null;
        }
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();
        ByteBuffer[] codecInputBuffers = codec.getInputBuffers();

        double totalErrorSquared = 0;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawOutputEOS = false;
        int inputLeft = mEncodedOutputBuffer.size();
        int inputBufferCount = 0;
        int outFrameCount = 0;
        YUVValue expected = new YUVValue();
        YUVValue decoded = new YUVValue();
        long start = System.currentTimeMillis();
        while (!sawOutputEOS) {
            if (inputLeft > 0) {
                int inputBufIndex = codec.dequeueInputBuffer(VIDEO_CODEC_WAIT_TIME_US);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    dstBuf.clear();
                    ByteBuffer src = mEncodedOutputBuffer.get(inputBufferCount);
                    int writeSize = src.capacity();
                    dstBuf.put(src.array(), 0, writeSize);
                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            writeSize,
                            0,
                            (inputLeft == 1) ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    inputLeft --;
                    inputBufferCount ++;
                }
            }

            int res = codec.dequeueOutputBuffer(info, VIDEO_CODEC_WAIT_TIME_US);
            if (res >= 0) {
                int outputBufIndex = res;

                // only do YUV compare on EOS frame if the buffer size is none-zero
                if (info.size > 0) {
                    Point origin = getOrigin(outFrameCount);
                    int i;

                    // if decoder supports planar or semiplanar, check output with
                    // ByteBuffer & Image each on half of the points
                    int pixelCheckPerFrame = PIXEL_CHECK_PER_FRAME;
                    if (!isDstFlexYUV()) {
                        pixelCheckPerFrame /= 2;
                        ByteBuffer buf = codec.getOutputBuffer(outputBufIndex);
                        if (VERBOSE && (outFrameCount == 0)) {
                            printByteBuffer("Y ", buf, 0, 20);
                            printByteBuffer("UV ", buf, mVideoWidth * mVideoHeight, 20);
                            printByteBuffer("UV ", buf,
                                    mVideoWidth * mVideoHeight + mVideoWidth * 60, 20);
                        }
                        for (i = 0; i < pixelCheckPerFrame; i++) {
                            int w = mRandom.nextInt(mVideoWidth);
                            int h = mRandom.nextInt(mVideoHeight);
                            getPixelValuesFromYUVBuffers(origin.x, origin.y, w, h, expected);
                            getPixelValuesFromOutputBuffer(buf, w, h, decoded);
                            if (VERBOSE) {
                                Log.i(TAG, outFrameCount + "-" + i + "- th round: ByteBuffer:"
                                        + " expected "
                                        + expected.mY + "," + expected.mU + "," + expected.mV
                                        + " decoded "
                                        + decoded.mY + "," + decoded.mU + "," + decoded.mV);
                            }
                            totalErrorSquared += expected.calcErrorSquared(decoded);
                        }
                    }

                    Image image = codec.getOutputImage(outputBufIndex);
                    assertTrue(image != null);
                    for (i = 0; i < pixelCheckPerFrame; i++) {
                        int w = mRandom.nextInt(mVideoWidth);
                        int h = mRandom.nextInt(mVideoHeight);
                        getPixelValuesFromYUVBuffers(origin.x, origin.y, w, h, expected);
                        getPixelValuesFromImage(image, w, h, decoded);
                        if (VERBOSE) {
                            Log.i(TAG, outFrameCount + "-" + i + "- th round: FlexYUV:"
                                    + " expcted "
                                    + expected.mY + "," + expected.mU + "," + expected.mV
                                    + " decoded "
                                    + decoded.mY + "," + decoded.mU + "," + decoded.mV);
                        }
                        totalErrorSquared += expected.calcErrorSquared(decoded);
                    }
                    outFrameCount++;
                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + oformat);
                int colorFormat = oformat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                if (colorFormat == CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                        || colorFormat == CodecCapabilities.COLOR_FormatYUV420Planar) {
                    mDstColorFormat = colorFormat;
                } else {
                    mDstColorFormat = CodecCapabilities.COLOR_FormatYUV420Flexible;
                    Log.w(TAG, "output format changed to unsupported one " +
                            Integer.toHexString(colorFormat) + ", using FlexYUV");
                }
            }
        }
        long finish = System.currentTimeMillis();
        codec.stop();
        codec.release();
        codec = null;
        assertTrue(outFrameCount >= TOTAL_FRAMES);
        // divide by 3 as sum is done for Y, U, V.
        double errorRms = Math.sqrt(totalErrorSquared / PIXEL_CHECK_PER_FRAME / outFrameCount / 3);
        double[] result = { (double) finish - start, errorRms };
        return result;
    }

    /**
     *  returns origin in the absolute frame for given frame count.
     *  The video scene is moving by moving origin per each frame.
     */
    private Point getOrigin(int frameCount) {
        if (frameCount < 100) {
            return new Point(2 * frameCount, 0);
        } else if (frameCount < 200) {
            return new Point(200, (frameCount - 100) * 2);
        } else {
            if (frameCount > 300) { // for safety
                frameCount = 300;
            }
            return new Point(600 - frameCount * 2, 600 - frameCount * 2);
        }
    }

    /**
     * initialize reference YUV plane
     * @param w This should be YUV_PLANE_ADDITIONAL_LENGTH pixels bigger than video resolution
     *          to allow movements
     * @param h This should be YUV_PLANE_ADDITIONAL_LENGTH pixels bigger than video resolution
     *          to allow movements
     * @param semiPlanarEnc
     * @param semiPlanarDec
     */
    private void initYUVPlane(int w, int h) {
        int bufferSizeY = w * h;
        mYBuffer = ByteBuffer.allocate(bufferSizeY);
        mUVBuffer = ByteBuffer.allocate(bufferSizeY / 2);
        mYDirectBuffer = ByteBuffer.allocateDirect(bufferSizeY);
        mUVDirectBuffer = ByteBuffer.allocateDirect(bufferSizeY / 2);
        mBufferWidth = w;
        mBufferHeight = h;
        final byte[] yArray = mYBuffer.array();
        final byte[] uvArray = mUVBuffer.array();
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                yArray[i * w + j]  = clampY((i + j) & 0xff);
            }
        }
        if (isSrcSemiPlanar()) {
            for (int i = 0; i < h/2; i++) {
                for (int j = 0; j < w/2; j++) {
                    uvArray[i * w + 2 * j]  = (byte) (i & 0xff);
                    uvArray[i * w + 2 * j + 1]  = (byte) (j & 0xff);
                }
            }
        } else { // planar, U first, then V
            int vOffset = bufferSizeY / 4;
            for (int i = 0; i < h/2; i++) {
                for (int j = 0; j < w/2; j++) {
                    uvArray[i * w/2 + j]  = (byte) (i & 0xff);
                    uvArray[i * w/2 + vOffset + j]  = (byte) (j & 0xff);
                }
            }
        }
        mYDirectBuffer.put(yArray);
        mUVDirectBuffer.put(uvArray);
        mYDirectBuffer.rewind();
        mUVDirectBuffer.rewind();
    }

    /**
     * class to store pixel values in YUV
     *
     */
    public class YUVValue {
        public byte mY;
        public byte mU;
        public byte mV;
        public YUVValue() {
        }

        public boolean equalTo(YUVValue other) {
            return (mY == other.mY) && (mU == other.mU) && (mV == other.mV);
        }

        public double calcErrorSquared(YUVValue other) {
            double yDelta = mY - other.mY;
            double uDelta = mU - other.mU;
            double vDelta = mV - other.mV;
            return yDelta * yDelta + uDelta * uDelta + vDelta * vDelta;
        }
    }

    /**
     * Read YUV values from given position (x,y) for given origin (originX, originY)
     * The whole data is already available from YBuffer and UVBuffer.
     * @param result pass the result via this. This is for avoiding creating / destroying too many
     *               instances
     */
    private void getPixelValuesFromYUVBuffers(int originX, int originY, int x, int y,
            YUVValue result) {
        result.mY = mYBuffer.get((originY + y) * mBufferWidth + (originX + x));
        if (isSrcSemiPlanar()) {
            int index = (originY + y) / 2 * mBufferWidth + (originX + x) / 2 * 2;
            //Log.d(TAG, "YUV " + originX + "," + originY + "," + x + "," + y + "," + index);
            result.mU = mUVBuffer.get(index);
            result.mV = mUVBuffer.get(index + 1);
        } else {
            int vOffset = mBufferWidth * mBufferHeight / 4;
            int index = (originY + y) / 2 * mBufferWidth / 2 + (originX + x) / 2;
            result.mU = mUVBuffer.get(index);
            result.mV = mUVBuffer.get(vOffset + index);
        }
    }

    /**
     * Read YUV pixels from decoded output buffer for give (x, y) position
     * Output buffer is composed of Y parts followed by U/V
     * @param result pass the result via this. This is for avoiding creating / destroying too many
     *               instances
     */
    private void getPixelValuesFromOutputBuffer(ByteBuffer buffer, int x, int y, YUVValue result) {
        result.mY = buffer.get(y * mVideoWidth + x);
        if (isDstSemiPlanar()) {
            int index = mVideoWidth * mVideoHeight + y / 2 * mVideoWidth + x / 2 * 2;
            //Log.d(TAG, "Decoded " + x + "," + y + "," + index);
            result.mU = buffer.get(index);
            result.mV = buffer.get(index + 1);
        } else {
            int vOffset = mVideoWidth * mVideoHeight / 4;
            int index = mVideoWidth * mVideoHeight + y / 2 * mVideoWidth / 2 + x / 2;
            result.mU = buffer.get(index);
            result.mV = buffer.get(index + vOffset);
        }
    }

    private void getPixelValuesFromImage(Image image, int x, int y, YUVValue result) {
        assertTrue(image.getFormat() == ImageFormat.YUV_420_888);

        Plane[] planes = image.getPlanes();
        assertTrue(planes.length == 3);

        result.mY = getPixelFromPlane(planes[0], x, y);
        result.mU = getPixelFromPlane(planes[1], x / 2, y / 2);
        result.mV = getPixelFromPlane(planes[2], x / 2, y / 2);
    }

    private byte getPixelFromPlane(Plane plane, int x, int y) {
        ByteBuffer buf = plane.getBuffer();
        return buf.get(y * plane.getRowStride() + x * plane.getPixelStride());
    }

    /**
     * Y cannot have full range. clamp it to prevent invalid value.
     */
    private byte clampY(int y) {
        if (y < Y_CLAMP_MIN) {
            y = Y_CLAMP_MIN;
        } else if (y > Y_CLAMP_MAX) {
            y = Y_CLAMP_MAX;
        }
        return (byte) (y & 0xff);
    }

    // for debugging
    private void printByteArray(String msg, byte[] data, int offset, int len) {
        StringBuilder builder = new StringBuilder();
        builder.append(msg);
        builder.append(":");
        for (int i = offset; i < offset + len; i++) {
            builder.append(Integer.toHexString(data[i]));
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        Log.i(TAG, builder.toString());
    }

    // for debugging
    private void printByteBuffer(String msg, ByteBuffer data, int offset, int len) {
        StringBuilder builder = new StringBuilder();
        builder.append(msg);
        builder.append(":");
        for (int i = offset; i < offset + len; i++) {
            builder.append(Integer.toHexString(data.get(i)));
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        Log.i(TAG, builder.toString());
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000L / mFrameRate;
    }
}
