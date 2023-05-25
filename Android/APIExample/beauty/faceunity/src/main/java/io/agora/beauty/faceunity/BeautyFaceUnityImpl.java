package io.agora.beauty.faceunity;

import android.content.Context;
import android.opengl.GLES20;

import com.faceunity.FUConfig;
import com.faceunity.core.entity.FUBundleData;
import com.faceunity.core.enumeration.FUInputTextureEnum;
import com.faceunity.core.enumeration.FUTransformMatrixEnum;
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.model.facebeauty.FaceBeauty;
import com.faceunity.core.model.facebeauty.FaceBeautyFilterEnum;
import com.faceunity.core.model.makeup.SimpleMakeup;
import com.faceunity.core.model.prop.Prop;
import com.faceunity.core.model.prop.sticker.Sticker;
import com.faceunity.nama.FURenderer;

import java.io.File;

import io.agora.beauty.base.IBeautyFaceUnity;

public class BeautyFaceUnityImpl implements IBeautyFaceUnity {

    public static String BUNDLE_FACE_BEAUTIFICATION = "graphics" + File.separator + "face_beautification.bundle";
    public static String BUNDLE_FACE_MAKEUP = "graphics" + File.separator + "face_makeup.bundle";
    public static final String BUNDLE_BODY_BEAUTY = "graphics" + File.separator + "body_slim.bundle";


    /*渲染控制器*/
    private FURenderKit mFURenderKit = FURenderKit.getInstance();
    private final FURenderer fuRenderer = FURenderer.getInstance();

    private volatile boolean isReleased = false;

    public BeautyFaceUnityImpl(Context context) {
        fuRenderer.setup(context);

        initFaceBeauty();
    }

    private void initFaceBeauty() {
        // config face beauty
        FUAIKit.getInstance().faceProcessorSetFaceLandmarkQuality(FUConfig.DEVICE_LEVEL);
        FaceBeauty recommendFaceBeauty = new FaceBeauty(new FUBundleData(BUNDLE_FACE_BEAUTIFICATION));
        recommendFaceBeauty.setFilterName(FaceBeautyFilterEnum.FENNEN_1);
        recommendFaceBeauty.setFilterIntensity(0.7);
        // 美牙
        recommendFaceBeauty.setToothIntensity(0.3f);
        // 亮眼
        recommendFaceBeauty.setEyeBrightIntensity(0.3f);
        // 大眼
        recommendFaceBeauty.setEyeEnlargingIntensity(0.5f);
        // 红润
        recommendFaceBeauty.setRedIntensity(0.5f * 2);
        // 美白
        recommendFaceBeauty.setColorIntensity(0.75f * 2);
        // 磨皮
        recommendFaceBeauty.setBlurIntensity(0.75f * 6);
        // 嘴型
        recommendFaceBeauty.setMouthIntensity(0.3f);
        // 瘦鼻
        recommendFaceBeauty.setNoseIntensity(0.1f);
        // 额头
        recommendFaceBeauty.setForHeadIntensity(0.3f);
        // 下巴
        recommendFaceBeauty.setChinIntensity(0.f);
        // 瘦脸
        recommendFaceBeauty.setCheekThinningIntensity(0.3f);
        // 窄脸
        recommendFaceBeauty.setCheekNarrowIntensity(0.f);
        // 小脸
        recommendFaceBeauty.setCheekSmallIntensity(0.f);
        // v脸
        recommendFaceBeauty.setCheekVIntensity(0.0f);
        mFURenderKit.setFaceBeauty(recommendFaceBeauty);
    }

    @Override
    public int process(int texId, int texType, int width, int height, boolean isFront) {
        if (isReleased) {
            return -1;
        }
        if (isFront) {
            fuRenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0);
            fuRenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0);
            fuRenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT0);
        } else {
            fuRenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
            fuRenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
            fuRenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT0);
        }
        fuRenderer.setInputTextureType(texType == GLES20.GL_TEXTURE_2D ? FUInputTextureEnum.FU_ADM_FLAG_COMMON_TEXTURE : FUInputTextureEnum.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE);
        return fuRenderer.onDrawFrameInput(texId, width, height);
    }


    @Override
    public int process(byte[] nv21, int width, int height, boolean isFront) {
        if (isReleased) {
            return -1;
        }
        if(isFront){
            fuRenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0);
            fuRenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0);
            fuRenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
        }else{
            fuRenderer.setInputBufferMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
            fuRenderer.setInputTextureMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
            fuRenderer.setOutputMatrix(FUTransformMatrixEnum.CCROT0_FLIPVERTICAL);
        }
        return fuRenderer.onDrawFrameInput(nv21, width, height);
    }

    @Override
    public void release() {
        isReleased = true;
        fuRenderer.release();
    }

    @Override
    public void setFaceBeautifyEnable(boolean enable) {
        if (isReleased) {
            return;
        }
        if (mFURenderKit.getFaceBeauty() != null) {
            mFURenderKit.getFaceBeauty().setEnable(enable);
        }
    }

    @Override
    public void setMakeUpEnable(boolean enable) {
        if (isReleased) {
            return;
        }
        if (enable) {
            SimpleMakeup makeup = new SimpleMakeup(new FUBundleData(BUNDLE_FACE_MAKEUP));
            makeup.setCombinedConfig(new FUBundleData("makeup/naicha.bundle"));
            makeup.setMakeupIntensity(1.0);
            mFURenderKit.setMakeup(makeup);
        } else {
            mFURenderKit.setMakeup(null);
        }
    }

    @Override
    public void setStickerEnable(boolean enable) {
        if (isReleased) {
            return;
        }
        if (enable) {
            Prop prop = new Sticker(new FUBundleData("sticker/fashi.bundle"));
            mFURenderKit.getPropContainer().replaceProp(null, prop);
        } else {
            mFURenderKit.getPropContainer().removeAllProp();
        }
    }

    @Override
    public void setBodyBeautifyEnable(boolean enable) {
        if (isReleased) {
            return;
        }
        if (mFURenderKit.getBodyBeauty() != null) {
            mFURenderKit.getBodyBeauty().setEnable(enable);
        }
    }
}
