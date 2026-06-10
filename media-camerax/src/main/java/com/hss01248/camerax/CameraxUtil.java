package com.hss01248.camerax;

import android.content.Context;
import android.view.Surface;
import android.view.View;

final class CameraxUtil {

    private CameraxUtil() {}

    static int orientationToSurfaceRotation(int orientation) {
        if (orientation >= 45 && orientation < 135) {
            return Surface.ROTATION_270;
        } else if (orientation >= 135 && orientation < 225) {
            return Surface.ROTATION_180;
        } else if (orientation >= 225 && orientation < 315) {
            return Surface.ROTATION_90;
        }
        return Surface.ROTATION_0;
    }

    static float surfaceRotationToDegrees(int surfaceRotation) {
        switch (surfaceRotation) {
            case Surface.ROTATION_90:
                return 90f;
            case Surface.ROTATION_180:
                return 180f;
            case Surface.ROTATION_270:
                return 270f;
            default:
                return 0f;
        }
    }

    static void rotateViewForOrientation(View view, float degrees) {
        if (view != null) {
            view.setRotation(degrees);
        }
    }

    static int dp2px(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
