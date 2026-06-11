attribute vec2 a_position;
attribute float a_alpha;

uniform mat4 u_projTrans;

varying float v_alpha;

void main() {
    v_alpha = a_alpha;
    gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);
}
