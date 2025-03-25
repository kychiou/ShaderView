#version 300 es

precision mediump float;

uniform sampler2D uInputTexture;
uniform sampler2D uTexture;
uniform vec2 uOffset;

in vec2 textureCoord;
out vec4 fragColor;

void main() {
    vec4 base = texture(uInputTexture, textureCoord);
    vec4 overlay = texture(uTexture, textureCoord + uOffset);
    bool isBlack = all(lessThanEqual(abs(overlay.rgb), vec3(0.1)));
    bool isTransparent = overlay.a <= 0.1;
    bool useBase = isBlack || isTransparent;
//    vec3 color = mix(base, overlay, useBase ? 0.0 : 1.0);
    fragColor = mix(base, overlay, useBase ? 0.0 : 1.0);
}