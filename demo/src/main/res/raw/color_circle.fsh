#version 300 es

precision mediump float;

uniform sampler2D uInputTexture;
uniform vec4 diffuseColor;

in vec2 textureCoord;
out vec4 fragColor;

void main() {
    vec2 uv = textureCoord - 0.3;
    if (length(uv) < 0.25)
        fragColor = diffuseColor;
    else
        fragColor = texture(uInputTexture, textureCoord);
}