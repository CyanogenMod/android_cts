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

package android.media.cts;

import com.android.cts.media.R;

import android.content.res.AssetFileDescriptor;
import android.cts.util.MediaUtils;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.opengl.GLES20;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * General MediaCodec tests.
 *
 * In particular, check various API edge cases.
 *
 * <p>The file in res/raw used by testDecodeShortInput are (c) copyright 2008,
 * Blender Foundation / www.bigbuckbunny.org, and are licensed under the Creative Commons
 * Attribution 3.0 License at http://creativecommons.org/licenses/by/3.0/us/.
 */
public class MediaCodecTest extends AndroidTestCase {
    private static final String TAG = "MediaCodecTest";
    private static final boolean VERBOSE = false;           // lots of logging

    // parameters for the video encoder
                                                            // H.264 Advanced Video Coding
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 2000000;            // 2Mbps
    private static final int FRAME_RATE = 15;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    // parameters for the audio encoder
    private static final String MIME_TYPE_AUDIO = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_AAC_PROFILE = 2; /* OMX_AUDIO_AACObjectLC */
    private static final int AUDIO_CHANNEL_COUNT = 2; // mono
    private static final int AUDIO_BIT_RATE = 128000;

    private static final int TIMEOUT_USEC = 100000;
    private static final int TIMEOUT_USEC_SHORT = 100;

    private boolean mVideoEncoderHadError = false;
    private boolean mAudioEncoderHadError = false;
    private volatile boolean mVideoEncodingOngoing = false;

    /**
     * Tests:
     * <br> Exceptions for MediaCodec factory methods
     * <br> Exceptions for MediaCodec methods when called in the incorrect state.
     *
     * A selective test to ensure proper exceptions are thrown from MediaCodec
     * methods when called in incorrect operational states.
     */
    public void testException() throws Exception {
        boolean tested = false;
        // audio decoder (MP3 should be present on all Android devices)
        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_MPEG, 44100 /* sampleRate */, 2 /* channelCount */);
        tested = verifyException(format, false /* isEncoder */) || tested;

        // audio encoder (AMR-WB may not be present on some Android devices)
        format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AMR_WB, 16000 /* sampleRate */, 1 /* channelCount */);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 19850);
        tested = verifyException(format, true /* isEncoder */) || tested;

        // video decoder (H.264/AVC may not be present on some Android devices)
        format = createMediaFormat();
        tested = verifyException(format, false /* isEncoder */) || tested;

        // video encoder (H.264/AVC may not be present on some Android devices)
        tested = verifyException(format, true /* isEncoder */) || tested;

        // signal test is skipped due to no device media codecs.
        if (!tested) {
            MediaUtils.skipTest(TAG, "cannot find any compatible device codecs");
        }
    }

    // wrap MediaCodec encoder and decoder creation
    private static MediaCodec createCodecByType(String type, boolean isEncoder)
            throws IOException {
        if (isEncoder) {
            return MediaCodec.createEncoderByType(type);
        }
        return MediaCodec.createDecoderByType(type);
    }

    private static void logMediaCodecException(MediaCodec.CodecException ex) {
        if (ex.isRecoverable()) {
            Log.w(TAG, "CodecException Recoverable: " + ex.getErrorCode());
        } else if (ex.isTransient()) {
            Log.w(TAG, "CodecException Transient: " + ex.getErrorCode());
        } else {
            Log.w(TAG, "CodecException Fatal: " + ex.getErrorCode());
        }
    }

    private static boolean verifyException(MediaFormat format, boolean isEncoder)
            throws IOException {
        String mimeType = format.getString(MediaFormat.KEY_MIME);
        if (!supportsCodec(mimeType, isEncoder)) {
            Log.i(TAG, "No " + (isEncoder ? "encoder" : "decoder")
                    + " found for mimeType= " + mimeType);
            return false;
        }

        // create codec (enter Initialized State)
        MediaCodec codec;

        // create improperly
        final String methodName = isEncoder ? "createEncoderByType" : "createDecoderByType";
        try {
            codec = createCodecByType(null, isEncoder);
            fail(methodName + " should return NullPointerException on null");
        } catch (NullPointerException e) { // expected
        }
        try {
            codec = createCodecByType("foobarplan9", isEncoder); // invalid type
            fail(methodName + " should return IllegalArgumentException on invalid type");
        } catch (IllegalArgumentException e) { // expected
        }
        try {
            codec = MediaCodec.createByCodecName("foobarplan9"); // invalid name
            fail(methodName + " should return IllegalArgumentException on invalid name");
        } catch (IllegalArgumentException e) { // expected
        }
        // correct
        codec = createCodecByType(format.getString(MediaFormat.KEY_MIME), isEncoder);

        // test a few commands
        try {
            codec.start();
            fail("start should return IllegalStateException when in Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("start should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            codec.flush();
            fail("flush should return IllegalStateException when in Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("flush should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        MediaCodecInfo codecInfo = codec.getCodecInfo(); // obtaining the codec info now is fine.
        try {
            int bufIndex = codec.dequeueInputBuffer(0);
            fail("dequeueInputBuffer should return IllegalStateException"
                    + " when in the Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("dequeueInputBuffer should not return MediaCodec.CodecException"
                    + " on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int bufIndex = codec.dequeueOutputBuffer(info, 0);
            fail("dequeueOutputBuffer should return IllegalStateException"
                    + " when in the Initialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("dequeueOutputBuffer should not return MediaCodec.CodecException"
                    + " on wrong state");
        } catch (IllegalStateException e) { // expected
        }

        // configure (enter Configured State)

        // configure improperly
        try {
            codec.configure(format, null /* surface */, null /* crypto */,
                    isEncoder ? 0 : MediaCodec.CONFIGURE_FLAG_ENCODE /* flags */);
            fail("configure needs MediaCodec.CONFIGURE_FLAG_ENCODE for encoders only");
        } catch (MediaCodec.CodecException e) { // expected
            logMediaCodecException(e);
        } catch (IllegalStateException e) {
            fail("configure should not return IllegalStateException when improperly configured");
        }
        // correct
        codec.configure(format, null /* surface */, null /* crypto */,
                isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0 /* flags */);

        // test a few commands
        try {
            codec.flush();
            fail("flush should return IllegalStateException when in Configured state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("flush should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            Surface surface = codec.createInputSurface();
            if (!isEncoder) {
                fail("createInputSurface should not work on a decoder");
            }
        } catch (IllegalStateException e) { // expected for decoder and audio encoder
            if (isEncoder && format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                throw e;
            }
        }

        // start codec (enter Executing state)
        codec.start();

        // test a few commands
        try {
            codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
            fail("configure should return IllegalStateException when in Executing state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            // TODO: consider configuring after a flush.
            fail("configure should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }

        // two flushes should be fine.
        codec.flush();
        codec.flush();

        // stop codec (enter Initialized state)
        // two stops should be fine.
        codec.stop();
        codec.stop();

        // release codec (enter Uninitialized state)
        // two releases should be fine.
        codec.release();
        codec.release();

        try {
            codecInfo = codec.getCodecInfo();
            fail("getCodecInfo should should return IllegalStateException" +
                    " when in Uninitialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("getCodecInfo should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        try {
            codec.stop();
            fail("stop should return IllegalStateException when in Uninitialized state");
        } catch (MediaCodec.CodecException e) {
            logMediaCodecException(e);
            fail("stop should not return MediaCodec.CodecException on wrong state");
        } catch (IllegalStateException e) { // expected
        }
        return true;
    }

    /**
     * Tests:
     * <br> calling createInputSurface() before configure() throws exception
     * <br> calling createInputSurface() after start() throws exception
     * <br> calling createInputSurface() with a non-Surface color format throws exception
     */
    public void testCreateInputSurfaceErrors() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        Surface surface = null;

        // Replace color format with something that isn't COLOR_FormatSurface.
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        int colorFormat = findNonSurfaceColorFormat(codecInfo, MIME_TYPE);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

        try {
            try {
                encoder = MediaCodec.createByCodecName(codecInfo.getName());
            } catch (IOException e) {
                fail("failed to create codec " + codecInfo.getName());
            }
            try {
                surface = encoder.createInputSurface();
                fail("createInputSurface should not work pre-configure");
            } catch (IllegalStateException ise) {
                // good
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            try {
                surface = encoder.createInputSurface();
                fail("createInputSurface should require COLOR_FormatSurface");
            } catch (IllegalStateException ise) {
                // good
            }

            encoder.start();

            try {
                surface = encoder.createInputSurface();
                fail("createInputSurface should not work post-start");
            } catch (IllegalStateException ise) {
                // good
            }
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
        }
        assertNull(surface);
    }

    /**
     * Tests:
     * <br> signaling end-of-stream before any data is sent works
     * <br> signaling EOS twice throws exception
     * <br> submitting a frame after EOS throws exception [TODO]
     */
    public void testSignalSurfaceEOS() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        InputSurface inputSurface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            // send an immediate EOS
            encoder.signalEndOfInputStream();

            try {
                encoder.signalEndOfInputStream();
                fail("should not be able to signal EOS twice");
            } catch (IllegalStateException ise) {
                // good
            }

            // submit a frame post-EOS
            GLES20.glClearColor(0.0f, 0.5f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            try {
                inputSurface.swapBuffers();
                if (false) {    // TODO
                    fail("should not be able to submit frame after EOS");
                }
            } catch (Exception ex) {
                // good
            }
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    /**
     * Tests:
     * <br> stopping with buffers in flight doesn't crash or hang
     */
    public void testAbruptStop() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        // There appears to be a race, so run it several times with a short delay between runs
        // to allow any previous activity to shut down.
        for (int i = 0; i < 50; i++) {
            Log.d(TAG, "testAbruptStop " + i);
            doTestAbruptStop();
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }
    }
    private void doTestAbruptStop() {
        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        InputSurface inputSurface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            int totalBuffers = encoder.getOutputBuffers().length;
            if (VERBOSE) Log.d(TAG, "Total buffers: " + totalBuffers);

            // Submit several frames quickly, without draining the encoder output, to try to
            // ensure that we've got some queued up when we call stop().  If we do too many
            // we'll block in swapBuffers().
            for (int i = 0; i < totalBuffers; i++) {
                GLES20.glClearColor(0.0f, (i % 8) / 8.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                inputSurface.swapBuffers();
            }
            Log.d(TAG, "stopping");
            encoder.stop();
            Log.d(TAG, "stopped");
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    /**
     * Tests:
     * <br> dequeueInputBuffer() fails when encoder configured with an input Surface
     */
    public void testDequeueSurface() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        Surface surface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            encoder.start();

            try {
                encoder.dequeueInputBuffer(-1);
                fail("dequeueInputBuffer should fail on encoder with input surface");
            } catch (IllegalStateException ise) {
                // good
            }

        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (surface != null) {
                surface.release();
            }
        }
    }

    /**
     * Tests:
     * <br> configure() encoder with Surface, re-configure() without Surface works
     * <br> sending EOS with signalEndOfInputStream on non-Surface encoder fails
     */
    public void testReconfigureWithoutSurface() {
        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE);
            return;
        }

        MediaFormat format = createMediaFormat();
        MediaCodec encoder = null;
        Surface surface = null;

        try {
            try {
                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE + " encoder");
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            encoder.start();

            encoder.getOutputBuffers();

            // re-configure, this time without an input surface
            if (VERBOSE) Log.d(TAG, "reconfiguring");
            encoder.stop();
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            if (VERBOSE) Log.d(TAG, "reconfigured");

            encoder.getOutputBuffers();
            encoder.dequeueInputBuffer(-1);

            try {
                encoder.signalEndOfInputStream();
                fail("signalEndOfInputStream only works on surface input");
            } catch (IllegalStateException ise) {
                // good
            }
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (surface != null) {
                surface.release();
            }
        }
    }

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortInput() throws InterruptedException {
        // Input buffers from this input video are queued up to and including the video frame with
        // timestamp LAST_BUFFER_TIMESTAMP_US.
        final int INPUT_RESOURCE_ID =
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        final long LAST_BUFFER_TIMESTAMP_US = 166666;

        // The test should fail if the decoder never produces output frames for the truncated input.
        // Time out decoding, as we have no way to query whether the decoder will produce output.
        final int DECODING_TIMEOUT_MS = 2000;

        final AtomicBoolean completed = new AtomicBoolean();
        Thread videoDecodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                completed.set(runDecodeShortInput(INPUT_RESOURCE_ID, LAST_BUFFER_TIMESTAMP_US));
            }
        });
        videoDecodingThread.start();
        videoDecodingThread.join(DECODING_TIMEOUT_MS);
        if (!completed.get()) {
            throw new RuntimeException("timed out decoding to end-of-stream");
        }
    }

    private boolean runDecodeShortInput(int inputResourceId, long lastBufferTimestampUs) {
        final int NO_BUFFER_INDEX = -1;

        OutputSurface outputSurface = null;
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            outputSurface = new OutputSurface(1, 1);
            mediaExtractor = getMediaExtractorForMimeType(inputResourceId, "video/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (!supportsCodec(mimeType, false)) {
                Log.i(TAG, "No decoder found for mimeType= " + MIME_TYPE);
                return true;
            }
            mediaCodec =
                    MediaCodec.createDecoderByType(mimeType);
            mediaCodec.configure(mediaFormat, outputSurface.getSurface(), null, 0);
            mediaCodec.start();
            boolean eos = false;
            boolean signaledEos = false;
            MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = NO_BUFFER_INDEX;
            while (!eos && !Thread.interrupted()) {
                // Try to feed more data into the codec.
                if (mediaExtractor.getSampleTrackIndex() != -1 && !signaledEos) {
                    int bufferIndex = mediaCodec.dequeueInputBuffer(0);
                    if (bufferIndex != NO_BUFFER_INDEX) {
                        ByteBuffer buffer = mediaCodec.getInputBuffers()[bufferIndex];
                        int size = mediaExtractor.readSampleData(buffer, 0);
                        long timestampUs = mediaExtractor.getSampleTime();
                        mediaExtractor.advance();
                        signaledEos = mediaExtractor.getSampleTrackIndex() == -1
                                || timestampUs == lastBufferTimestampUs;
                        mediaCodec.queueInputBuffer(bufferIndex,
                                0,
                                size,
                                timestampUs,
                                signaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    }
                }

                // If we don't have an output buffer, try to get one now.
                if (outputBufferIndex == NO_BUFFER_INDEX) {
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(outputBufferInfo, 0);
                }

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                        || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                        || outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    outputBufferIndex = NO_BUFFER_INDEX;
                } else if (outputBufferIndex != NO_BUFFER_INDEX) {
                    eos = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    boolean render = outputBufferInfo.size > 0;
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, render);
                    if (render) {
                        outputSurface.awaitNewImage();
                    }

                    outputBufferIndex = NO_BUFFER_INDEX;
                }
            }

            return eos;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
        }
    }

    /**
     * Tests creating two decoders for {@link #MIME_TYPE_AUDIO} at the same time.
     */
    public void testCreateTwoAudioDecoders() {
        final MediaFormat format = MediaFormat.createAudioFormat(
                MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);

        MediaCodec audioDecoderA = null;
        MediaCodec audioDecoderB = null;
        try {
            try {
                audioDecoderA = MediaCodec.createDecoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create first " + MIME_TYPE_AUDIO + " decoder");
            }
            audioDecoderA.configure(format, null, null, 0);
            audioDecoderA.start();

            try {
                audioDecoderB = MediaCodec.createDecoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create second " + MIME_TYPE_AUDIO + " decoder");
            }
            audioDecoderB.configure(format, null, null, 0);
            audioDecoderB.start();
        } finally {
            if (audioDecoderB != null) {
                try {
                    audioDecoderB.stop();
                    audioDecoderB.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }

            if (audioDecoderA != null) {
                try {
                    audioDecoderA.stop();
                    audioDecoderA.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }
        }
    }

    /**
     * Tests creating an encoder and decoder for {@link #MIME_TYPE_AUDIO} at the same time.
     */
    public void testCreateAudioDecoderAndEncoder() {
        if (!supportsCodec(MIME_TYPE_AUDIO, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE_AUDIO);
            return;
        }

        if (!supportsCodec(MIME_TYPE_AUDIO, false)) {
            Log.i(TAG, "No decoder found for mimeType= " + MIME_TYPE_AUDIO);
            return;
        }

        final MediaFormat encoderFormat = MediaFormat.createAudioFormat(
                MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);
        encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, AUDIO_AAC_PROFILE);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        final MediaFormat decoderFormat = MediaFormat.createAudioFormat(
                MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT);

        MediaCodec audioEncoder = null;
        MediaCodec audioDecoder = null;
        try {
            try {
                audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE_AUDIO + " encoder");
            }
            audioEncoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            try {
                audioDecoder = MediaCodec.createDecoderByType(MIME_TYPE_AUDIO);
            } catch (IOException e) {
                fail("failed to create " + MIME_TYPE_AUDIO + " decoder");
            }
            audioDecoder.configure(decoderFormat, null, null, 0);
            audioDecoder.start();
        } finally {
            if (audioDecoder != null) {
                try {
                    audioDecoder.stop();
                    audioDecoder.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }

            if (audioEncoder != null) {
                try {
                    audioEncoder.stop();
                    audioEncoder.release();
                } catch (RuntimeException e) {
                    Log.w(TAG, "exception stopping/releasing codec", e);
                }
            }
        }
    }

    public void testConcurrentAudioVideoEncodings() throws InterruptedException {
        if (!supportsCodec(MIME_TYPE_AUDIO, true)) {
            Log.i(TAG, "No encoder found for mimeType= " + MIME_TYPE_AUDIO);
            return;
        }

        if (!supportsCodec(MIME_TYPE, true)) {
            Log.i(TAG, "No decoder found for mimeType= " + MIME_TYPE);
            return;
        }

        final int VIDEO_NUM_SWAPS = 100;
        // audio only checks this and stop
        mVideoEncodingOngoing = true;
        final CodecInfo info = getAvcSupportedFormatInfo();
        long start = System.currentTimeMillis();
        Thread videoEncodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runVideoEncoding(VIDEO_NUM_SWAPS, info);
            }
        });
        Thread audioEncodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runAudioEncoding();
            }
        });
        videoEncodingThread.start();
        audioEncodingThread.start();
        videoEncodingThread.join();
        mVideoEncodingOngoing = false;
        audioEncodingThread.join();
        assertFalse("Video encoding error. Chekc logcat", mVideoEncoderHadError);
        assertFalse("Audio encoding error. Chekc logcat", mAudioEncoderHadError);
        long end = System.currentTimeMillis();
        Log.w(TAG, "Concurrent AV encoding took " + (end - start) + " ms for " + VIDEO_NUM_SWAPS +
                " video frames");
    }

    private static class CodecInfo {
        public int mMaxW;
        public int mMaxH;
        public int mFps;
        public int mBitRate;
    };

    private static CodecInfo getAvcSupportedFormatInfo() {
        MediaCodecInfo mediaCodecInfo = selectCodec(MIME_TYPE);
        CodecCapabilities cap = mediaCodecInfo.getCapabilitiesForType(MIME_TYPE);
        if (cap == null) { // not supported
            return null;
        }
        CodecInfo info = new CodecInfo();
        int highestLevel = 0;
        for (CodecProfileLevel lvl : cap.profileLevels) {
            if (lvl.level > highestLevel) {
                highestLevel = lvl.level;
            }
        }
        int maxW = 0;
        int maxH = 0;
        int bitRate = 0;
        int fps = 0; // frame rate for the max resolution
        switch(highestLevel) {
            // Do not support Level 1 to 2.
            case CodecProfileLevel.AVCLevel1:
            case CodecProfileLevel.AVCLevel11:
            case CodecProfileLevel.AVCLevel12:
            case CodecProfileLevel.AVCLevel13:
            case CodecProfileLevel.AVCLevel1b:
            case CodecProfileLevel.AVCLevel2:
                return null;
            case CodecProfileLevel.AVCLevel21:
                maxW = 352;
                maxH = 576;
                bitRate = 4000000;
                fps = 25;
                break;
            case CodecProfileLevel.AVCLevel22:
                maxW = 720;
                maxH = 480;
                bitRate = 4000000;
                fps = 15;
                break;
            case CodecProfileLevel.AVCLevel3:
                maxW = 720;
                maxH = 480;
                bitRate = 10000000;
                fps = 30;
                break;
            case CodecProfileLevel.AVCLevel31:
                maxW = 1280;
                maxH = 720;
                bitRate = 14000000;
                fps = 30;
                break;
            case CodecProfileLevel.AVCLevel32:
                maxW = 1280;
                maxH = 720;
                bitRate = 20000000;
                fps = 60;
                break;
            case CodecProfileLevel.AVCLevel4: // only try up to 1080p
            default:
                maxW = 1920;
                maxH = 1080;
                bitRate = 20000000;
                fps = 30;
                break;
        }
        info.mMaxW = maxW;
        info.mMaxH = maxH;
        info.mFps = fps;
        info.mBitRate = bitRate;
        Log.i(TAG, "AVC Level 0x" + Integer.toHexString(highestLevel) + " bit rate " + bitRate +
                " fps " + info.mFps + " w " + maxW + " h " + maxH);

        return info;
    }

    private void runVideoEncoding(int numSwap, CodecInfo info) {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, info.mMaxW, info.mMaxH);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, info.mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, info.mFps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        mVideoEncoderHadError = false;
        try {
            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            encoder.start();
            for (int i = 0; i < numSwap; i++) {
                GLES20.glClearColor(0.0f, 0.5f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                inputSurface.swapBuffers();
                // dequeue buffers until not available
                int index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                while (index >= 0) {
                    encoder.releaseOutputBuffer(index, false);
                    // just throw away output
                    // allow shorter wait for 2nd round to move on quickly.
                    index = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC_SHORT);
                }
            }
            encoder.signalEndOfInputStream();
        } catch (Throwable e) {
            Log.w(TAG, "runVideoEncoding got error: " + e);
            mVideoEncoderHadError = true;
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    private void runAudioEncoding() {
        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, AUDIO_AAC_PROFILE);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        MediaCodec encoder = null;
        mAudioEncoderHadError = false;
        try {
            encoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            encoder.start();
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            ByteBuffer source = ByteBuffer.allocate(inputBuffers[0].capacity());
            for (int i = 0; i < source.capacity()/2; i++) {
                source.putShort((short)i);
            }
            source.rewind();
            int currentInputBufferIndex = 0;
            long encodingLatencySum = 0;
            int totalEncoded = 0;
            int numRepeat = 0;
            while (mVideoEncodingOngoing) {
                numRepeat++;
                int inputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                while (inputIndex == -1) {
                    inputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                }
                ByteBuffer inputBuffer = inputBuffers[inputIndex];
                inputBuffer.rewind();
                inputBuffer.put(source);
                long start = System.currentTimeMillis();
                totalEncoded += inputBuffers[inputIndex].limit();
                encoder.queueInputBuffer(inputIndex, 0, inputBuffer.limit(), 0, 0);
                source.rewind();
                int index = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                long end = System.currentTimeMillis();
                encodingLatencySum += (end - start);
                while (index >= 0) {
                    encoder.releaseOutputBuffer(index, false);
                    // just throw away output
                    // allow shorter wait for 2nd round to move on quickly.
                    index = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC_SHORT);
                }
            }
            Log.w(TAG, "Audio encoding average latency " + encodingLatencySum / numRepeat +
                    " ms for average write size " + totalEncoded / numRepeat +
                    " total latency " + encodingLatencySum + " ms for total bytes " + totalEncoded);
        } catch (Throwable e) {
            Log.w(TAG, "runAudioEncoding got error: " + e);
            mAudioEncoderHadError = true;
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
        }
    }

    /**
     * Creates a MediaFormat with the basic set of values.
     */
    private static MediaFormat createMediaFormat() {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        return format;
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        // FIXME: select codecs based on the complete use-case, not just the mime
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (!info.isEncoder()) {
                continue;
            }

            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and isn't COLOR_FormatSurface.  Throws
     * an exception if none found.
     */
    private static int findNonSurfaceColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return colorFormat;
            }
        }
        fail("couldn't find a good color format for " + codecInfo.getName() + " / " + MIME_TYPE);
        return 0;   // not reached
    }

    private MediaExtractor getMediaExtractorForMimeType(int resourceId, String mimeTypePrefix)
            throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resourceId);
        try {
            mediaExtractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        int trackIndex;
        for (trackIndex = 0; trackIndex < mediaExtractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = mediaExtractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                mediaExtractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == mediaExtractor.getTrackCount()) {
            throw new IllegalStateException("couldn't get a video track");
        }

        return mediaExtractor;
    }

    private static boolean supportsCodec(String mimeType, boolean encoder) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (encoder && !info.isEncoder()) {
                continue;
            }
            if (!encoder && info.isEncoder()) {
                continue;
            }
            
            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }
}
