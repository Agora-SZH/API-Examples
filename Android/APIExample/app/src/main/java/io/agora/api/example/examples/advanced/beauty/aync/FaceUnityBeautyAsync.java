package io.agora.api.example.examples.advanced.beauty.aync;

import android.opengl.GLES20;

import io.agora.base.TextureBufferHelper;
import io.agora.beauty.base.IBeautyFaceUnity;

public class FaceUnityBeautyAsync extends BaseBeautyAsync {

    private final IBeautyFaceUnity beautyFaceUnity;

    public FaceUnityBeautyAsync(IBeautyFaceUnity beautyFaceUnity, TextureBufferHelper textureBufferHelper) {
        super(textureBufferHelper);
        this.beautyFaceUnity = beautyFaceUnity;
    }


    @Override
    protected int process(AsyncVideoFrame videoFrame, int width, int height, int originTexId) {
        return beautyFaceUnity.process(originTexId, GLES20.GL_TEXTURE_2D, width, height, videoFrame.isFront);
    }


}
