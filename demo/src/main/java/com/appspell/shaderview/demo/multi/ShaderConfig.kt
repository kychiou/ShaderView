package com.appspell.shaderview.demo.multi

import android.opengl.GLES32
import com.appspell.shaderview.ShaderRawResId
import com.appspell.shaderview.demo.R
import com.appspell.shaderview.gl.params.ShaderParams
import com.appspell.shaderview.gl.params.ShaderParamsBuilder
import com.appspell.shaderview.gl.view.GLTextureView

internal interface ShaderConfig {
    val shaderRawResId: ShaderRawResId
    val shaderParams: ShaderParams
    fun onUpdate(timestamp: Long)
}

internal class BlurShader(private val glTextureView: GLTextureView) : ShaderConfig {
    override val shaderRawResId = ShaderRawResId(fragment = R.raw.blur)
    override val shaderParams: ShaderParams =
        ShaderParamsBuilder()
            .addTexture2D(
                "uTexture",
                R.drawable.test_texture,
                GLES32.GL_TEXTURE1
            )
            .addVec2f("uScale", floatArrayOf(0f, 0f))
            .addInt("uBlurSize", 3)
            .build()
    override fun onUpdate(timestamp: Long) {
        val maxBlurSize = 25
        val size = ((timestamp) / 100) % maxBlurSize + 1
        shaderParams.updateValue("uBlurSize", size.toInt())
        shaderParams.updateValue(
            "uScale",
            floatArrayOf(1.0f / glTextureView.width, 1.0f / glTextureView.height)
        )
    }
}

internal class CircleColorShader : ShaderConfig {
    override val shaderRawResId = ShaderRawResId(fragment = R.raw.color_circle)
    override val shaderParams: ShaderParams =
        ShaderParamsBuilder()
            .addVec4f("diffuseColor", floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f))
            .build()
    override fun onUpdate(timestamp: Long) {
        val r = (((timestamp) / 10) % 254).toFloat() / 254.0f
        val g = (((timestamp) / 50) % 254).toFloat() / 254.0f
        val b = (((timestamp) / 100) % 254).toFloat() / 254.0f
        shaderParams.updateValue("diffuseColor", floatArrayOf(r, g, b, 1.0f))
    }
}

internal class AnimatedTextureShader : ShaderConfig {
    override val shaderRawResId = ShaderRawResId(fragment = R.raw.overlay_animated_texture)
    override val shaderParams: ShaderParams =
        ShaderParamsBuilder()
            .addTexture2D(
                "uTexture",
                R.drawable.android,
                GLES32.GL_TEXTURE2
            )
            .addVec2f("uOffset")
            .build()
    override fun onUpdate(timestamp: Long) {
        val u = (timestamp % 5000L) / 5000f
        val v = (timestamp % 1000L) / 1000f
        shaderParams.updateValue("uOffset", floatArrayOf(u, v))
    }
}