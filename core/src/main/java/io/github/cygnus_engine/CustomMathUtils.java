package io.github.cygnus_engine;

public class CustomMathUtils {
    

    public static float getAngleBetweenPoints(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    public static float deltaDeg(float fromDeg, float toDeg) {
        float angleDiff = toDeg - fromDeg;
        return limitAngle180(angleDiff);
    }

    /* warning: deltaDeg and angleDifference have different behavior! */
    public static float angleDifference(float target, float current) {
        float angleDiff = target - current;
        return limitAngle180(angleDiff);
    }

    public static float limitAngle180(float angle) {
        // limit angle from -180 to 180
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;

        return angle;
    }

    public static float normalizeAngle360(float angle) {
        // limit angle from 0 to 360
        float n = angle % 360f;
        if (n < 0f) n += 360f;
        return n;
    }

}
