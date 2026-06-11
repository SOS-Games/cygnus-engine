#ifdef GL_ES
precision mediump float;
#endif

varying float v_alpha;

uniform vec3 u_color;
uniform float u_intensity;

void main() {
    float alpha = v_alpha * u_intensity;
    gl_FragColor = vec4(u_color * alpha, alpha);
}
