package io.agora.api.example.examples.advanced.beauty;

import android.graphics.Matrix;
import android.opengl.GLES20;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;

import io.agora.api.example.R;
import io.agora.api.example.common.BaseFragment;
import io.agora.api.example.common.widget.VideoReportLayout;
import io.agora.api.example.databinding.FragmentBeautyFaceunityBinding;
import io.agora.api.example.utils.TokenUtils;
import io.agora.base.TextureBufferHelper;
import io.agora.base.VideoFrame;
import io.agora.base.internal.video.GlRectDrawer;
import io.agora.base.internal.video.GlTextureFrameBuffer;
import io.agora.base.internal.video.GlUtil;
import io.agora.base.internal.video.YuvHelper;
import io.agora.beauty.base.IBeautyFaceUnity;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.gl.EglBaseProvider;
import io.agora.rtc2.video.IVideoFrameObserver;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class FaceUnityBeauty extends BaseFragment {
    private static final String TAG = "SceneTimeBeauty";

    private IBeautyFaceUnity iBeautyFaceUnity;
    private FragmentBeautyFaceunityBinding mBinding;
    private RtcEngine rtcEngine;
    private String channelId;
    private ByteBuffer nv21ByteBuffer;

    private byte[] nv21ByteArray;
    private boolean isFrontCamera = true;

    private TextureBufferHelper mTextureBufferHelper;

    // double cache input
    private int mCurrIndex = -1;
    private final int[] mOutTextureIds = new int[]{-1, -1};
    private final GlTextureFrameBuffer[] mCacheFrameBuffers = new GlTextureFrameBuffer[2];
    private final GlRectDrawer mDrawer = new GlRectDrawer();


    private VideoReportLayout mLocalVideoLayout;
    private VideoReportLayout mRemoteVideoLayout;
    private boolean isLocalFull = true;
    private IVideoFrameObserver mVideoFrameObserver;
    private IRtcEngineEventHandler mRtcEngineEventHandler;

    private volatile boolean isDestroyed = false;
    private int mFrameRotation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentBeautyFaceunityBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!IBeautyFaceUnity.hasIntegrated()) {
            mBinding.tvIntegrateTip.setVisibility(View.VISIBLE);
            return;
        }

        channelId = getArguments().getString(getString(R.string.key_channel_name));
        initVideoView();
        initRtcEngine();
        joinChannel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isDestroyed = true;
        if (mTextureBufferHelper != null) {
            mTextureBufferHelper.invoke(() -> {
                iBeautyFaceUnity.release();
                iBeautyFaceUnity = null;
                for (int i = 0; i < mCacheFrameBuffers.length; i++) {
                    GlTextureFrameBuffer buffer = mCacheFrameBuffers[i];
                    if(buffer != null){
                        buffer.release();
                        mCacheFrameBuffers[i] = null;
                    }
                }
                return null;
            });
            mTextureBufferHelper.dispose();
            mTextureBufferHelper = null;
        }
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
        }
        RtcEngine.destroy();
    }

    private void initVideoView() {
        mBinding.cbFaceBeautify.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (iBeautyFaceUnity == null) {
                return;
            }
            iBeautyFaceUnity.setFaceBeautifyEnable(isChecked);
        });
        mBinding.cbMakeup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (iBeautyFaceUnity == null) {
                return;
            }
            iBeautyFaceUnity.setMakeUpEnable(isChecked);
        });
        mBinding.cbSticker.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (iBeautyFaceUnity == null) {
                return;
            }
            iBeautyFaceUnity.setStickerEnable(isChecked);
        });
        mBinding.cbBodyBeauty.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (iBeautyFaceUnity == null) {
                return;
            }
            iBeautyFaceUnity.setBodyBeautifyEnable(isChecked);
        });
        mBinding.ivCamera.setOnClickListener(v -> {
            rtcEngine.switchCamera();
            isFrontCamera = !isFrontCamera;
        });
        mBinding.smallVideoContainer.setOnClickListener(v -> updateVideoLayouts(!FaceUnityBeauty.this.isLocalFull));
    }

    private void initRtcEngine() {
        try {
            mRtcEngineEventHandler = new IRtcEngineEventHandler() {
                @Override
                public void onError(int err) {
                    super.onError(err);
                    showLongToast(String.format(Locale.US, "msg:%s, code:%d", RtcEngine.getErrorDescription(err), err));
                }

                @Override
                public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
                    super.onJoinChannelSuccess(channel, uid, elapsed);
                    mLocalVideoLayout.setReportUid(uid);
                }

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    super.onUserJoined(uid, elapsed);
                    runOnUIThread(() -> {
                        if (mRemoteVideoLayout == null) {
                            mRemoteVideoLayout = new VideoReportLayout(requireContext());
                            mRemoteVideoLayout.setReportUid(uid);
                            TextureView videoView = new TextureView(requireContext());
                            rtcEngine.setupRemoteVideo(new VideoCanvas(videoView, Constants.RENDER_MODE_HIDDEN, uid));
                            mRemoteVideoLayout.addView(videoView);
                            updateVideoLayouts(isLocalFull);
                        }
                    });
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    super.onUserOffline(uid, reason);
                    runOnUIThread(() -> {
                        if (mRemoteVideoLayout != null && mRemoteVideoLayout.getReportUid() == uid) {
                            mRemoteVideoLayout.removeAllViews();
                            mRemoteVideoLayout = null;
                            updateVideoLayouts(isLocalFull);
                        }
                    });
                }

                @Override
                public void onLocalAudioStats(LocalAudioStats stats) {
                    super.onLocalAudioStats(stats);
                    runOnUIThread(() -> mLocalVideoLayout.setLocalAudioStats(stats));
                }

                @Override
                public void onLocalVideoStats(Constants.VideoSourceType source, LocalVideoStats stats) {
                    super.onLocalVideoStats(source, stats);
                    runOnUIThread(() -> mLocalVideoLayout.setLocalVideoStats(stats));
                }

                @Override
                public void onRemoteAudioStats(RemoteAudioStats stats) {
                    super.onRemoteAudioStats(stats);
                    if (mRemoteVideoLayout != null) {
                        runOnUIThread(() -> mRemoteVideoLayout.setRemoteAudioStats(stats));
                    }
                }

                @Override
                public void onRemoteVideoStats(RemoteVideoStats stats) {
                    super.onRemoteVideoStats(stats);
                    if (mRemoteVideoLayout != null) {
                        runOnUIThread(() -> mRemoteVideoLayout.setRemoteVideoStats(stats));
                    }
                }
            };
            rtcEngine = RtcEngine.create(getContext(), getString(R.string.agora_app_id), mRtcEngineEventHandler);

            if (rtcEngine == null) {
                return;
            }


            mVideoFrameObserver = new IVideoFrameObserver() {
                @Override
                public boolean onCaptureVideoFrame(VideoFrame videoFrame) {
                    if (isDestroyed) {
                        return true;
                    }

                    if (mTextureBufferHelper == null) {
                        doOnBeautyCreatingBegin();
                        mTextureBufferHelper = TextureBufferHelper.create("STRender", EglBaseProvider.getCurrentEglContext());
                        iBeautyFaceUnity = IBeautyFaceUnity.create(getContext());
                        doOnBeautyCreatingEnd();
                    }
                    boolean success = processBeauty(videoFrame, videoFrame.getSourceType() == VideoFrame.SourceType.kFrontCamera);
                    if (!success) {
                        return false;
                    }
                    // drag one frame to avoid reframe when switching camera.
                    if (mFrameRotation != videoFrame.getRotation()) {
                        mFrameRotation = videoFrame.getRotation();
                        return false;
                    }

                    return true;
                }

                @Override
                public boolean onPreEncodeVideoFrame( VideoFrame videoFrame) {
                    return false;
                }

                @Override
                public boolean onScreenCaptureVideoFrame(VideoFrame videoFrame) {
                    return false;
                }

                @Override
                public boolean onPreEncodeScreenVideoFrame(VideoFrame videoFrame) {
                    return false;
                }

                @Override
                public boolean onMediaPlayerVideoFrame(VideoFrame videoFrame, int mediaPlayerId) {
                    return false;
                }

                @Override
                public boolean onRenderVideoFrame(String channelId, int uid, VideoFrame videoFrame) {
                    return false;
                }

                @Override
                public int getVideoFrameProcessMode() {
                    return IVideoFrameObserver.PROCESS_MODE_READ_WRITE;
                }

                @Override
                public int getVideoFormatPreference() {
                    return IVideoFrameObserver.VIDEO_PIXEL_DEFAULT;
                }

                @Override
                public boolean getRotationApplied() {
                    return false;
                }

                @Override
                public boolean getMirrorApplied() {
                    return false;
                }

                @Override
                public int getObservedFramePosition() {
                    return IVideoFrameObserver.POSITION_POST_CAPTURER;
                }
            };
            rtcEngine.registerVideoFrameObserver(mVideoFrameObserver);
            rtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_1280x720,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    1600,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            ));
            rtcEngine.enableVideo();
            rtcEngine.disableAudio();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean processBeautyByTextureDouble(VideoFrame videoFrame, boolean isFrontCamera) {
        VideoFrame.Buffer buffer = videoFrame.getBuffer();
        if (!(buffer instanceof VideoFrame.TextureBuffer)) {
            return false;
        }
        VideoFrame.TextureBuffer texBuffer = (VideoFrame.TextureBuffer) buffer;

        int width = texBuffer.getWidth();
        int height = texBuffer.getHeight();

        long startTime = System.nanoTime();

        int processTexId = -1;
        if (mCurrIndex == -1) {
            mCurrIndex = 0;
            mTextureBufferHelper.invoke((Callable<Void>) () -> {
                // copy texture to cache
                if (mCacheFrameBuffers[0] == null) {
                    // create texture
                    mCacheFrameBuffers[0] = new GlTextureFrameBuffer(GLES20.GL_RGBA);
                }
                mCacheFrameBuffers[0].setSize(width, height);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mCacheFrameBuffers[0].getFrameBufferId());
                mDrawer.drawOes(texBuffer.getTextureId(), GlUtil.IDENTITY_MATRIX, width, height, 0, 0, width, height);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                return null;
            });
            mTextureBufferHelper.getHandler().post(() -> {
                mOutTextureIds[0] = iBeautyFaceUnity.process(
                        mCacheFrameBuffers[0].getTextureId(),
                        GLES20.GL_TEXTURE_2D,
                        width, height, isFrontCamera
                );
            });
            return false;

        } else if (mCurrIndex == 0) {
            processTexId = mTextureBufferHelper.invoke(() -> {
                return mOutTextureIds[0];
            });
            mCurrIndex = 1;
            mTextureBufferHelper.invoke((Callable<Void>) () -> {
                if (mCacheFrameBuffers[1] == null) {
                    // create texture
                    mCacheFrameBuffers[1] = new GlTextureFrameBuffer(GLES20.GL_RGBA);
                }
                mCacheFrameBuffers[1].setSize(width, height);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mCacheFrameBuffers[1].getFrameBufferId());
                mDrawer.drawOes(texBuffer.getTextureId(), GlUtil.IDENTITY_MATRIX, width, height, 0, 0, width, height);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                return null;
            });
            mTextureBufferHelper.getHandler().post(() -> {
                mOutTextureIds[1] = iBeautyFaceUnity.process(
                        mCacheFrameBuffers[1].getTextureId(),
                        GLES20.GL_TEXTURE_2D,
                        width, height, isFrontCamera
                );
            });

        } else if (mCurrIndex == 1) {
            processTexId = mTextureBufferHelper.invoke(() -> {
                return mOutTextureIds[1];
            });
            mCurrIndex = 0;
            mTextureBufferHelper.invoke((Callable<Void>) () -> {
                if (mCacheFrameBuffers[0] == null) {
                    // create texture
                    mCacheFrameBuffers[0] = new GlTextureFrameBuffer(GLES20.GL_TEXTURE_2D);
                }
                mCacheFrameBuffers[0].setSize(width, height);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mCacheFrameBuffers[0].getFrameBufferId());
                mDrawer.drawOes(texBuffer.getTextureId(), GlUtil.IDENTITY_MATRIX, width, height, 0, 0, width, height);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                return null;
            });
            mTextureBufferHelper.getHandler().post(() -> {
                mOutTextureIds[0] = iBeautyFaceUnity.process(
                        mCacheFrameBuffers[0].getTextureId(),
                        GLES20.GL_TEXTURE_2D,
                        width, height, isFrontCamera
                );
            });
        }
        if (mFrameRotation != videoFrame.getRotation()) {
            mFrameRotation = videoFrame.getRotation();
            mCurrIndex = -1;
            return false;
        }
        long processDuration = (System.nanoTime() - startTime) / 1000000;
        Log.d(TAG, "processBeautyByTextureDouble consume time: " + processDuration);

        VideoFrame.TextureBuffer textureBuffer = mTextureBufferHelper.wrapTextureBuffer(
                width, height, VideoFrame.TextureBuffer.Type.RGB, processTexId,
                texBuffer.getTransformMatrix());
        videoFrame.replaceBuffer(textureBuffer, videoFrame.getRotation(), videoFrame.getTimestampNs());
        return true;
    }

    private boolean processBeautyByNV21(VideoFrame videoFrame, boolean isFrontCamera) {
        VideoFrame.Buffer buffer = videoFrame.getBuffer();
        int width = buffer.getWidth();
        int height = buffer.getHeight();

        long startTime = System.nanoTime();

        int nv21Size = (int) (width * height * 3.0f / 2.0f + 0.5f);
        if (nv21ByteBuffer == null || nv21ByteBuffer.capacity() != nv21Size) {
            if (nv21ByteBuffer != null) {
                nv21ByteBuffer.clear();
            }
            nv21ByteBuffer = ByteBuffer.allocateDirect(nv21Size);
            nv21ByteArray = new byte[nv21Size];
        }


        VideoFrame.I420Buffer i420Buffer = buffer.toI420();
        YuvHelper.I420ToNV12(i420Buffer.getDataY(), i420Buffer.getStrideY(),
                i420Buffer.getDataV(), i420Buffer.getStrideV(),
                i420Buffer.getDataU(), i420Buffer.getStrideU(),
                nv21ByteBuffer, width, height);
        nv21ByteBuffer.position(0);
        nv21ByteBuffer.get(nv21ByteArray);
        i420Buffer.release();

        long nv21TranDuration = (System.nanoTime() - startTime) / 1000000;

        Integer processTexId = mTextureBufferHelper.invoke(() -> iBeautyFaceUnity.process(
                nv21ByteArray,
                width, height, isFrontCamera
        ));

        long processDuration = (System.nanoTime() - startTime) / 1000000;
        Log.d(TAG, "processBeautyByNV21 consume time: " + processDuration + "(" + nv21TranDuration + ")");

        VideoFrame.TextureBuffer textureBuffer = mTextureBufferHelper.wrapTextureBuffer(
                width, height, VideoFrame.TextureBuffer.Type.RGB, processTexId,
                new Matrix());
        videoFrame.replaceBuffer(textureBuffer, videoFrame.getRotation(), videoFrame.getTimestampNs());
        return true;
    }


    private boolean processBeauty(VideoFrame videoFrame, boolean isFrontCamera) {
        if (videoFrame.getBuffer() instanceof VideoFrame.TextureBuffer) {
            return processBeautyByTextureDouble(videoFrame, isFrontCamera);
        } else {
            return processBeautyByNV21(videoFrame, isFrontCamera);
        }
    }

    private void joinChannel() {
        int uid = new Random(System.currentTimeMillis()).nextInt(1000) + 10000;
        ChannelMediaOptions options = new ChannelMediaOptions();
        options.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        TokenUtils.gen(requireActivity(), channelId, uid, token -> {
            int ret = rtcEngine.joinChannel(token, channelId, uid, options);
            if (ret != Constants.ERR_OK) {
                showAlert(String.format(Locale.US, "%s\ncode:%d", RtcEngine.getErrorDescription(ret), ret));
            }
        });

        mLocalVideoLayout = new VideoReportLayout(requireContext());
        TextureView videoView = new TextureView(requireContext());
        VideoCanvas local = new VideoCanvas(videoView, Constants.RENDER_MODE_HIDDEN);
        local.mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED;
        rtcEngine.setupLocalVideo(local);
        mLocalVideoLayout.addView(videoView);
        rtcEngine.startPreview();

        updateVideoLayouts(isLocalFull);
    }

    private void updateVideoLayouts(boolean isLocalFull) {
        this.isLocalFull = isLocalFull;
        mBinding.fullVideoContainer.removeAllViews();
        mBinding.smallVideoContainer.removeAllViews();
        if (isLocalFull) {
            if (mLocalVideoLayout != null) {
                mBinding.fullVideoContainer.addView(mLocalVideoLayout);
            }

            if (mRemoteVideoLayout != null) {
                mRemoteVideoLayout.getChildAt(0).setOnClickListener(v -> updateVideoLayouts(!FaceUnityBeauty.this.isLocalFull));
                mBinding.smallVideoContainer.addView(mRemoteVideoLayout);
            }
        } else {
            if (mLocalVideoLayout != null) {
                mLocalVideoLayout.getChildAt(0).setOnClickListener(v -> updateVideoLayouts(!FaceUnityBeauty.this.isLocalFull));
                mBinding.smallVideoContainer.addView(mLocalVideoLayout);
            }
            if (mRemoteVideoLayout != null) {
                mBinding.fullVideoContainer.addView(mRemoteVideoLayout);
            }
        }
    }

    private void doOnBeautyCreatingBegin() {
        Log.d(TAG, "doOnBeautyCreatingBegin...");
    }

    private void doOnBeautyCreatingEnd() {
        Log.d(TAG, "doOnBeautyCreatingEnd.");
        runOnUIThread(() -> {
            mBinding.cbBodyBeauty.setChecked(false);
            mBinding.cbFaceBeautify.setChecked(false);
            mBinding.cbSticker.setChecked(false);
            mBinding.cbMakeup.setChecked(false);
        });
    }

    private void doOnBeautyReleasingBegin() {
        Log.d(TAG, "doOnBeautyReleasingBegin...");
    }

    private void doOnBeautyReleasingEnd() {
        Log.d(TAG, "doOnBeautyReleasingEnd.");
    }
}
