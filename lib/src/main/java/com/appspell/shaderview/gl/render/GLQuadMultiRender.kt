package com.appspell.shaderview.gl.render

import android.opengl.GLES32
import android.opengl.Matrix
import com.appspell.shaderview.gl.params.ShaderParams
import com.appspell.shaderview.gl.render.GLQuadRenderImpl.Companion.VERTEX_SHADER_IN_POSITION
import com.appspell.shaderview.gl.render.GLQuadRenderImpl.Companion.VERTEX_SHADER_IN_TEXTURE_COORD
import com.appspell.shaderview.gl.render.GLQuadRenderImpl.Companion.VERTEX_SHADER_UNIFORM_MATRIX_MVP
import com.appspell.shaderview.gl.render.GLQuadRenderImpl.Companion.VERTEX_SHADER_UNIFORM_MATRIX_STM
import com.appspell.shaderview.gl.shader.GLShader
import com.appspell.shaderview.gl.view.GLTextureView
import com.appspell.shaderview.log.LibLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "GLQuadMultiRender"

private const val FLOAT_SIZE_BYTES = 4
private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

interface GLQuadMultiRender : GLTextureView.Renderer {

    var shaders: List<GLShader>

    var listener: MultiShaderViewListener?

    fun release()

    interface MultiShaderViewListener {
        fun onSurfaceCreated()

        fun onDrawFrame(index: Int, shaderParams: ShaderParams)
    }
}

internal class GLQuadMultiRenderImpl : GLQuadMultiRender {

    companion object {
        const val FRAGMENT_SHADER_IN_TEXTURE = "uInputTexture"
    }

    override var listener: GLQuadMultiRender.MultiShaderViewListener? = null

    override var shaders: List<GLShader> = emptyList()
        set(value) {
            field = value
            if (hasSurfaceCreated)
                initializeShaders()
        }

    private val quadVertices: FloatBuffer
    private val flippedQuadVertices: FloatBuffer
    private val matrixMVP = FloatArray(16)
    private val matrixSTM = FloatArray(16)

    private val frameBuffers = IntArray(2)
    private val fboTextures = IntArray(2)

    private var width = -1
    private var height = -1

    private var hasSurfaceCreated = false

    private val inPositionAndTextureHandleMap = mutableMapOf<Int, Pair<Int, Int>>()

    init {
        // set array of Quad vertices
        val quadVerticesData = floatArrayOf(
            // [x,y,z, U,V]
            -1.0f, -1.0f, 0f, 0f, 1f,
            1.0f, -1.0f, 0f, 1f, 1f,
            -1.0f, 1.0f, 0f, 0f, 0f,
            1.0f, 1.0f, 0f, 1f, 0f
        )
        val flippedQuadVerticesData = floatArrayOf(
            // [x,y,z, U,V] (V flipped)
            -1.0f, -1.0f, 0f, 0f, 0f,
            1.0f, -1.0f, 0f, 1f, 0f,
            -1.0f,  1.0f, 0f, 0f, 1f,
            1.0f,  1.0f, 0f, 1f, 1f
        )
        quadVertices = createByteBuffer(quadVerticesData)
        flippedQuadVertices = createByteBuffer(flippedQuadVerticesData)

        // initialize matrix
        Matrix.setIdentityM(matrixSTM, 0)
    }

    private fun createByteBuffer(verticesData: FloatArray) =
        ByteBuffer
            .allocateDirect(verticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(verticesData).position(0)
            }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES32.glViewport(0, 0, width, height)

        // FBOs
        if (width != this.width || height != this.height) {
            for (i in 0..1) {
                GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, fboTextures[i])
                GLES32.glTexImage2D(
                    GLES32.GL_TEXTURE_2D, 0, GLES32.GL_RGBA,
                    width, height, 0, GLES32.GL_RGBA,
                    GLES32.GL_UNSIGNED_BYTE, null
                )
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_LINEAR)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE)
                GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE)

                GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, frameBuffers[i])
                GLES32.glFramebufferTexture2D(
                    GLES32.GL_FRAMEBUFFER, GLES32.GL_COLOR_ATTACHMENT0,
                    GLES32.GL_TEXTURE_2D, fboTextures[i], 0
                )
                val status = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER)
                if (status != GLES32.GL_FRAMEBUFFER_COMPLETE) {
                    LibLog.e(TAG, "Framebuffer not complete #$i: $status")
                }
            }
            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0)
        }

        this.width = width
        this.height = height
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES32.glGenFramebuffers(2, frameBuffers, 0)
        GLES32.glGenTextures(2, fboTextures, 0)

        listener?.onSurfaceCreated()
        initializeShaders()

        hasSurfaceCreated = true
    }

    private fun initializeShaders() {
        val shaders = shaders.toList()
        if (shaders.isEmpty() || !shaders.all { it.isReady }) {
            return
        }

        shaders.forEachIndexed { index, shader ->
            // built-in parameters
            shader.params = shader.params
                .newBuilder()
                .addMat4f(VERTEX_SHADER_UNIFORM_MATRIX_MVP)
                .addMat4f(VERTEX_SHADER_UNIFORM_MATRIX_STM)
                .apply { if (index > 0) addTexture2D(FRAGMENT_SHADER_IN_TEXTURE) }
                .build()

            // as far as we set the new
            shader.bindParams(null)

            // set attributes (input for Vertex Shader)
            val inPositionHandle = glGetAttribLocation(shader, VERTEX_SHADER_IN_POSITION)
            val inTextureHandle = glGetAttribLocation(shader, VERTEX_SHADER_IN_TEXTURE_COORD)
            inPositionAndTextureHandleMap[index] = inPositionHandle to inTextureHandle
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val shaders = shaders.toList()
        if (shaders.isEmpty() || !shaders.all { it.isReady }) {
            return
        }

        shaders.forEachIndexed { index, shader ->
            // bind input
            if (index > 0) {
                GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
                GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, fboTextures[(index - 1) % 2])
            }

            // bind output
            val outputFbo = if (index == shaders.lastIndex) 0 else frameBuffers[index % 2]
            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, outputFbo)

            GLES32.glClearColor(0f, 0f, 0f, 1f)
            GLES32.glClear(GLES32.GL_DEPTH_BUFFER_BIT or GLES32.GL_COLOR_BUFFER_BIT)

            GLES32.glUseProgram(shader.program)
            checkGlError("$index, glUseProgram")

            // shader input (built-in attributes)
            val useFlipped = index != shaders.lastIndex // flip y when passing in to FBO
            val bufferToUse = if (useFlipped) flippedQuadVertices else quadVertices
            inPositionAndTextureHandleMap[index]?.also { (inPositionHandle, inTextureHandle) ->
                setAttribute(inPositionHandle, VERTEX_SHADER_IN_POSITION, 3, TRIANGLE_VERTICES_DATA_POS_OFFSET, bufferToUse)
                setAttribute(inTextureHandle, VERTEX_SHADER_IN_TEXTURE_COORD, 2, TRIANGLE_VERTICES_DATA_UV_OFFSET, bufferToUse)
            }

            // build-in uniforms
            Matrix.setIdentityM(matrixMVP, 0)
            shader.params.updateValue(VERTEX_SHADER_UNIFORM_MATRIX_MVP, matrixMVP)
            shader.params.updateValue(VERTEX_SHADER_UNIFORM_MATRIX_STM, matrixSTM)

            // callback if we need to update some custom parameters
            listener?.onDrawFrame(index = index, shaderParams = shader.params)

            // send params to the shader
            shader.onDrawFrame()

            // activate blending for textures
            GLES32.glBlendFunc(GLES32.GL_SRC_ALPHA, GLES32.GL_ONE_MINUS_SRC_ALPHA)
            GLES32.glEnable(GLES32.GL_BLEND)

            // draw scene
            GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4)
            checkGlError("$index, glDrawArrays")

            GLES32.glFinish()
        }
    }

    /**
     * get location of some input attribute for shader
     */
    private fun glGetAttribLocation(shader: GLShader, attrName: String): Int {
        val attrLocation = GLES32.glGetAttribLocation(shader.program, attrName)
        checkGlError("glGetAttribLocation $attrName")
        return attrLocation
    }

    /**
     * set values for attributes of input vertices
     */
    private fun setAttribute(attrLocation: Int, attrName: String, size: Int, offset: Int, buffer: FloatBuffer) {
        buffer.position(offset)
        GLES32.glVertexAttribPointer(
            attrLocation,
            size,
            GLES32.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            buffer
        )
        checkGlError("glVertexAttribPointer $attrName")
        GLES32.glEnableVertexAttribArray(attrLocation)
        checkGlError("glEnableVertexAttribArray $attrName")
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES32.glGetError().also { error = it } != GLES32.GL_NO_ERROR) {
            LibLog.e(TAG, "$op: glError $error")
            // don't throw an exception since it cannot be caught outside of ShaderView
            //throw RuntimeException("$op: glError $error")
        }
    }

    override fun release() {
        // release FBOs
        GLES32.glDeleteTextures(2, fboTextures, 0)
        GLES32.glDeleteFramebuffers(2, frameBuffers, 0)

        for (i in 0..1) {
            fboTextures[i] = 0
            frameBuffers[i] = 0
        }

        // release shaders
        shaders.forEach { it.release() }

        // reset size
        width = -1
        height = -1
    }
}