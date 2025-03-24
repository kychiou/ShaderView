package com.appspell.shaderview

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.RawRes
import androidx.annotation.StyleRes
import com.appspell.shaderview.gl.params.ShaderParams
import com.appspell.shaderview.gl.render.GLQuadMultiRender
import com.appspell.shaderview.gl.render.GLQuadMultiRenderImpl
import com.appspell.shaderview.gl.shader.GLShader
import com.appspell.shaderview.gl.shader.ShaderBuilder
import com.appspell.shaderview.gl.view.GLTextureView
import com.appspell.shaderview.log.LibLog

private const val OPENGL_VERSION = 3

private const val BIT_PER_CHANEL = 8
private const val DEPTH_BIT_PER_CHANEL = 16
private const val STENCIL_BIT_PER_CHANEL = 0

private val DEFAULT_VERTEX_SHADER_RESOURCE = R.raw.quad_vert
private val DEFAULT_FRAGMENT_SHADER_RESOURCE = R.raw.default_frag

class MultiShaderView @JvmOverloads constructor(
    context: Context,
    @AttrRes attrs: AttributeSet? = null,
    @StyleRes defStyleAttr: Int = 0
) : GLTextureView(context, attrs, defStyleAttr) {

    companion object {
        const val TAG = "MultiShaderView"
    }

    var shaderRawResIds: List<ShaderRawResId> = emptyList()
        set(value) {
            needToRecreateShaders = true
            field = value
        }

    var shaderCodes: List<ShaderCode> = emptyList()
        set(value) {
            needToRecreateShaders = true
            field = value
        }

    var shaderParams: List<ShaderParams> = emptyList()
        set(value) {
            field = value
            updateShaderParams()
        }
    var onViewReadyListener: ((shaders: List<GLShader>) -> Unit)? = null
    var onDrawFrameListener: ((index: Int, shaderParams: ShaderParams) -> Unit)? = null

    private var needToRecreateShaders = false

    /**
     * Enable or disable logging for all of ShaderView globally
     * TODO it need to enable logs for this view only
     */
    var debugMode = false
        set(value) {
            field = value
            LibLog.isEnabled = value // TODO should be enabled for particular view only
            if (value) {
                setDebugFlags(DEBUG_CHECK_GL_ERROR.and(DEBUG_LOG_GL_CALLS))
                enableLogPauseResume = true
                enableLogEgl = true
                enableLogSurface = true
            }
        }

    /**
     * should we re-render this view all the time
     */
    var updateContinuously: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) {
                setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
            } else {
                setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
            }
        }

    /**
     * how many frames the shader should be drawn per second
     */
    var framerate: Int
        set(value) {
            setFPS(value)
        }
        get(): Int {
            return getFPS()
        }

    private val rendererListener = object : GLQuadMultiRender.MultiShaderViewListener {
        override fun onSurfaceCreated() {
            LibLog.d(TAG, "onSurfaceCreated")
            initShaders()
            post {
                onViewReadyListener?.invoke(renderer.shaders)
            }
        }

        override fun onDrawFrame(index: Int, shaderParams: ShaderParams) {
            post {
                onDrawFrameListener?.invoke(index, shaderParams)
            }
        }
    }

    private val renderer: GLQuadMultiRender = GLQuadMultiRenderImpl()

    init {
        setEGLContextClientVersion(OPENGL_VERSION)
        renderer.listener = rendererListener

        // use RGBA_8888 buffer to support transparency
        setEGLConfigChooser(
            BIT_PER_CHANEL,
            BIT_PER_CHANEL,
            BIT_PER_CHANEL,
            BIT_PER_CHANEL,
            DEPTH_BIT_PER_CHANEL,
            STENCIL_BIT_PER_CHANEL
        )

        setRenderer(renderer)

        // make this view transparent
        isOpaque = false

        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
    }

    private fun updateShaderParams() {
        if (needToRecreateShaders) {
            return
        }
        shaderParams.forEachIndexed { index, shaderParams ->
            renderer.shaders.getOrNull(index)?.params = shaderParams
        }
    }

    private fun initShaders() {
        LibLog.d(TAG, "initShaders. needToRecreateShaders: $needToRecreateShaders, shaderRawResIds: $shaderRawResIds")
        if (needToRecreateShaders) {
            val tmpShaderParams = arrayListOf<GLShader>()

            val shaderBuilders =
                if (shaderCodes.isNotEmpty())
                    shaderCodes.map { shaderCode ->
                        ShaderBuilder()
                            .create(
                                vertexShader = shaderCode.vertex.orEmpty(),
                                fragmentShader = shaderCode.fragment.orEmpty()
                            )
                    }
                else
                    shaderRawResIds.map { shaderRawResId ->
                        ShaderBuilder()
                            .create(
                                context = context,
                                vertexShaderRawResId = shaderRawResId.vertex
                                    ?: DEFAULT_VERTEX_SHADER_RESOURCE,
                                fragmentShaderRawResId = shaderRawResId.fragment
                                    ?: DEFAULT_FRAGMENT_SHADER_RESOURCE
                            )
                    }

            shaderBuilders.forEachIndexed { index, shaderBuilder ->
                shaderParams.getOrNull(index)?.also {
                    shaderBuilder.params(it)
                }
                tmpShaderParams.add(shaderBuilder.build())
            }

            val previousShader = renderer.shaders
            renderer.shaders = tmpShaderParams
            previousShader.forEach { it.release() }

            needToRecreateShaders = true
        }

        // bind shader params.
        // note: we have to pass [android.content.res.Resources] to be able to load textures from Resources
        renderer.shaders.forEach { it.bindParams(resources) }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderer.release()
        return super.onSurfaceTextureDestroyed(surface)
    }

    fun updateShaders() {
        initShaders()
    }
}

data class ShaderRawResId(
    @RawRes
    val vertex: Int? = null,
    @RawRes
    val fragment: Int? = null
)

data class ShaderCode(
    val vertex: String? = null,
    val fragment: String? = null
)