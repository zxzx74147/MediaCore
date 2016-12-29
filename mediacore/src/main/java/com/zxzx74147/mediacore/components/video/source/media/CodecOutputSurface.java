package com.zxzx74147.mediacore.components.video.source.media;


import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.zxzx74147.mediacore.components.video.filter.IChangeFilter;
import com.zxzx74147.mediacore.components.video.filter.base.MagicSurfaceInputFilter;
import com.zxzx74147.mediacore.components.video.filter.base.gpuimage.GPUImageFilter;
import com.zxzx74147.mediacore.components.video.filter.helper.MagicFilterFactory;
import com.zxzx74147.mediacore.components.video.filter.helper.MagicFilterType;
import com.zxzx74147.mediacore.components.video.source.camera.gles.GlUtil;
import com.zxzx74147.mediacore.components.video.util.OpenGlUtils;
import com.zxzx74147.mediacore.components.video.util.Rotation;
import com.zxzx74147.mediacore.components.video.util.TextureRotationUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by guoheng-iri on 2016/9/1.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CodecOutputSurface
        implements SurfaceTexture.OnFrameAvailableListener, IChangeFilter {

    private static final String TAG = "MediaMixer";
    private static final boolean VERBOSE = false;           // lots of logging

    //    private STextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    public Surface EncodeSurface;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLContext mEGLContextEncoder = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    private EGLSurface mEGLSurfaceEncoder = EGL14.EGL_NO_SURFACE;
    int mWidth;
    int mHeight;

    int mVideoWidth;
    int mVideoHeight;
    int mVideoXOffset;
    int mVideoYOffset;


    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;

//    private ByteBuffer mPixelBuf;                       // used by saveFrame()

    private int mTextureId = OpenGlUtils.NO_TEXTURE;
    private MagicSurfaceInputFilter mSurfaceInputFilter = null;
    private GPUImageFilter mImageFilter = null;
    private FloatBuffer mGLCubeBuffer;
    private FloatBuffer mGLTextureBuffer;

    /**
     * Creates a CodecOutputSurface backed by a pbuffer with the specified dimensions.  The
     * new EGL context and surface will be made current.  Creates a Surface that can be passed
     * to MediaCodec.configure().
     */
    public CodecOutputSurface(int width, int height, Surface surface) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        mWidth = width;
        mHeight = height;

        eglSetup(surface);
        makeCurrent();
        setup();

        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);
        mGLTextureBuffer.clear();
        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(0), false, true);
        mGLTextureBuffer.put(textureCords).position(0);
        makeCurrent(1);
        mSurfaceInputFilter = new MagicSurfaceInputFilter();
        mSurfaceInputFilter.init();
        mImageFilter = MagicFilterFactory.initFilters(MagicFilterType.NONE);
        mImageFilter.init();
    }

    public void setRevert(){
        mGLTextureBuffer.clear();
        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(0), false, false);
        mGLTextureBuffer.put(textureCords).position(0);
    }

    /**
     * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
     */
    private void setup() {
        mTextureId = OpenGlUtils.getExternalOESTextureID();
        if (VERBOSE) Log.d(TAG, "textureID=" + mTextureId);
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        mSurfaceTexture.setOnFrameAvailableListener(this);

        mSurface = new Surface(mSurfaceTexture);
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
     */
    private void eglSetup(Surface surface) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        EGLConfig configEncoder = getConfig(2);

        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        mEGLContextEncoder = EGL14.eglCreateContext(mEGLDisplay, configEncoder, mEGLContext,
                attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContextEncoder == null) {
            throw new RuntimeException("null context2");
        }

        // Create a pbuffer surface.
        int[] surfaceAttribs = {
                EGL14.EGL_WIDTH, mWidth,
                EGL14.EGL_HEIGHT, mHeight,
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs, 0);


        checkEglError("eglCreatePbufferSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }


        int[] surfaceAttribs2 = {
                EGL14.EGL_NONE
        };
        mEGLSurfaceEncoder = EGL14.eglCreateWindowSurface(mEGLDisplay, configEncoder, surface,
                surfaceAttribs2, 0);   //creates an EGL window surface and returns its handle
        checkEglError("eglCreateWindowSurface");
        if (mEGLSurfaceEncoder == null) {
            throw new RuntimeException("surface was null");
        }
        EncodeSurface = surface;

    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    public void release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLSurface = EGL14.EGL_NO_SURFACE;

        mSurface.release();

        mSurface = null;
        EncodeSurface = null;
        mSurfaceTexture = null;

        if (mSurfaceInputFilter != null) {
            mSurfaceInputFilter.destroy();
            mSurfaceInputFilter = null;
        }

        if (mImageFilter != null) {
            mImageFilter.destroy();
            mImageFilter = null;
        }
    }

    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurfaceEncoder, nsecs);
        checkEglError("eglPresentationTimeANDROID");
    }


    public boolean swapBuffers() {
        boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurfaceEncoder);
        checkEglError("eglSwapBuffers");
        return result;
    }

    private EGLConfig getConfig(int version) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        }

        // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            Log.w(TAG, "unable to find RGB8888 / " + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }


    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }


    public void makeCurrent(int index) {
        if (index == 0) {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        } else {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurfaceEncoder, mEGLSurfaceEncoder, mEGLContextEncoder)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        }

    }

    /**
     * Returns the Surface.
     */
    public Surface getSurface() {
        return mSurface;
    }


    public void awaitNewImage() {
        final int TIMEOUT_MS = 2500;

        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    mFrameSyncObject.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        throw new RuntimeException("frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }

        // Latch the data.
        GlUtil.checkGlError("before updateTexImage");
        mSurfaceTexture.updateTexImage();
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     *
     * @param invert if set, render the image with Y inverted (0,0 in top left)
     */
    float[] mtx = new float[16];

    public void drawImage() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        mSurfaceTexture.getTransformMatrix(mtx);
        mSurfaceInputFilter.setTextureTransformMatrix(mtx);
        if (mImageFilter == null) {
            mSurfaceInputFilter.onDrawFrame(mTextureId, mGLCubeBuffer, mGLTextureBuffer);
        } else {
            int id = mSurfaceInputFilter.onDrawToTexture(mTextureId);

            GLES20.glViewport(mVideoXOffset, mVideoYOffset, mVideoWidth, mVideoHeight);
            mImageFilter.onDrawFrame(id, mGLCubeBuffer, mGLTextureBuffer);
        }
    }

    public void drawStaticImage(int textureId) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        if (mImageFilter != null) {
            GLES20.glViewport(mVideoXOffset, mVideoYOffset, mVideoWidth, mVideoHeight);
            mImageFilter.onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);
        }
    }

    // SurfaceTexture callback
    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        if (VERBOSE) Log.d(TAG, "new frame available");
        synchronized (mFrameSyncObject) {
            if (mFrameAvailable) {
                throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
            }
            mFrameAvailable = true;
            mFrameSyncObject.notifyAll();
        }
    }


    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    public void setVideoWH(int videoWidth, int videoHeight) {
        float scaleW = (float) mWidth / videoWidth;
        float scaleH = (float) mHeight / videoHeight;
        float scale = Math.min(scaleH, scaleW);
        int width = (int) (videoWidth * scale);
        int height = (int) (videoHeight * scale);
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoXOffset = (mWidth - mVideoWidth) / 2;
        mVideoYOffset = (mHeight - mVideoHeight) / 2;
        mSurfaceInputFilter.onDisplaySizeChanged(width, height);
        mSurfaceInputFilter.onInputSizeChanged(width, height);
        mSurfaceInputFilter.initSurfaceFrameBuffer(width, height);

        mImageFilter.onInputSizeChanged(width, height);
        mImageFilter.onDisplaySizeChanged(width, height);

    }

    @Override
    public void setFilter(MagicFilterType type) {
        if(type==null){
            type = MagicFilterType.NONE;
        }
        if(mImageFilter!=null){
            mImageFilter.destroy();
            mImageFilter = null;
        }
        mImageFilter = MagicFilterFactory.initFilters(type);
        mImageFilter.init();
    }
}
