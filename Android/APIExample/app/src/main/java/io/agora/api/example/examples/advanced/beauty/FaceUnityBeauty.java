package io.agora.api.example.examples.advanced.beauty;

import android.graphics.Matrix;
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

import io.agora.api.example.R;
import io.agora.api.example.common.BaseFragment;
import io.agora.api.example.common.widget.VideoReportLayout;
import io.agora.api.example.databinding.FragmentBeautyFaceunityBinding;
import io.agora.api.example.examples.advanced.beauty.aync.FaceUnityBeautyAsync;
import io.agora.api.example.utils.TokenUtils;
import io.agora.base.TextureBufferHelper;
import io.agora.base.VideoFrame;
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
    private FaceUnityBeautyAsync faceUnityAsync;

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
        if (rtcEngine != null) {
            rtcEngine.leaveChannel();
        }
        if(faceUnityAsync != null){
            faceUnityAsync.release();
            faceUnityAsync = null;
        }
        if (mTextureBufferHelper != null) {
            mTextureBufferHelper.invoke(() -> {
                iBeautyFaceUnity.release();
                iBeautyFaceUnity = null;
                return null;
            });
            mTextureBufferHelper.dispose();
            mTextureBufferHelper = null;
        }
        RtcEngine.destroy();
    }

    @Override
    protected void onBackPressed() {
        mBinding.fullVideoContainer.removeAllViews();
        mBinding.smallVideoContainer.removeAllViews();
        super.onBackPressed();
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
                public boolean onCaptureVideoFrame(int type, VideoFrame videoFrame) {
                    if (isDestroyed) {
                        return true;
                    }

                    if (mTextureBufferHelper == null) {
                        mTextureBufferHelper = TextureBufferHelper.create("FURender", EglBaseProvider.instance().getRootEglBase().getEglBaseContext());
                    }

                    boolean success = processBeauty(videoFrame);
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
                public boolean onPreEncodeVideoFrame( int type, VideoFrame videoFrame) {
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
                    VideoEncoderConfiguration.VD_1920x1080,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    1600,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            ));
            rtcEngine.enableVideo();
            rtcEngine.disableAudio();

            iBeautyFaceUnity = IBeautyFaceUnity.create(getContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean processBeautyByTextureAsync(VideoFrame videoFrame) {
        VideoFrame.Buffer buffer = videoFrame.getBuffer();
        if (!(buffer instanceof VideoFrame.TextureBuffer)) {
            return false;
        }
        if(faceUnityAsync == null){
            faceUnityAsync = new FaceUnityBeautyAsync(iBeautyFaceUnity, mTextureBufferHelper);
        }
        long startTime = System.nanoTime();
        boolean process = faceUnityAsync.process(videoFrame);
        long processDuration = (System.nanoTime() - startTime) / 1000000;
        runOnUIThread(() -> mBinding.tvBeautyCost.setText("BeautyCost(TextureAsync): " + processDuration + " ms"));
        Log.d(TAG, "processBeautyByTextureDouble consume time: " + processDuration);
        return process;
    }

    private boolean processBeautyByNV21(VideoFrame videoFrame) {
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
        runOnUIThread(() -> mBinding.tvBeautyCost.setText("BeautyCost(NV21): " + processDuration + " ms"));
        Log.d(TAG, "processBeautyByNV21 consume time: " + processDuration + "(" + nv21TranDuration + ")");

        VideoFrame.TextureBuffer textureBuffer = mTextureBufferHelper.wrapTextureBuffer(
                width, height, VideoFrame.TextureBuffer.Type.RGB, processTexId,
                new Matrix());
        videoFrame.replaceBuffer(textureBuffer, videoFrame.getRotation(), videoFrame.getTimestampNs());
        return true;
    }


    private boolean processBeauty(VideoFrame videoFrame) {
        if (videoFrame.getBuffer() instanceof VideoFrame.TextureBuffer) {
            return processBeautyByTextureAsync(videoFrame);
        } else {
            return processBeautyByNV21(videoFrame);
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

}
