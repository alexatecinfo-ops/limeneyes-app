package com.limenseguridad.limeneyes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

/**
 * Iconos vectoriales oficiales de Limen (estilo Feather / Lucide stroke 2px rounded)
 * idénticos a los usados en Limen Admin, Limen Bot y Mercury.
 */
public class LimenIcons {

    public static final int ICON_HOME       = 1;
    public static final int ICON_VIDEO      = 2;
    public static final int ICON_GRID_4     = 3;
    public static final int ICON_GRID_9     = 4;
    public static final int ICON_GRID_1     = 5;
    public static final int ICON_PLAY       = 6;
    public static final int ICON_PAUSE      = 7;
    public static final int ICON_VOLUME_ON  = 8;
    public static final int ICON_VOLUME_OFF = 9;
    public static final int ICON_CAMERA     = 10;
    public static final int ICON_SERVER     = 11;
    public static final int ICON_USER       = 12;
    public static final int ICON_BELL       = 13;
    public static final int ICON_SEARCH     = 14;
    public static final int ICON_PLUS       = 15;
    public static final int ICON_CLOSE      = 16;
    public static final int ICON_EYE        = 17;
    public static final int ICON_REFRESH    = 18;
    public static final int ICON_CHEVRON_R  = 19;
    public static final int ICON_LOGOUT     = 20;
    public static final int ICON_MORE_H     = 21;
    public static final int ICON_SHIELD     = 22;
    public static final int ICON_HD         = 23;
    public static final int ICON_SD         = 24;

    public static class IconView extends View {
        private int iconType;
        private final Paint paint;
        private final Path path = new Path();
        private final RectF rect = new RectF();

        public IconView(Context context, int iconType, String colorHex) {
            super(context);
            this.iconType = iconType;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setColor(Color.parseColor(colorHex));
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(dp(context, 1.9f));
            this.paint.setStrokeCap(Paint.Cap.ROUND);
            this.paint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setIconType(int iconType) {
            this.iconType = iconType;
            invalidate();
        }

        public void setIconColor(String colorHex) {
            this.paint.setColor(Color.parseColor(colorHex));
            invalidate();
        }

        private float dp(Context ctx, float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float s = Math.min(w, h) / 24f;
            float ox = (w - 24f * s) / 2f;
            float oy = (h - 24f * s) / 2f;

            canvas.save();
            canvas.translate(ox, oy);

            path.reset();
            paint.setStyle(Paint.Style.STROKE);

            switch (iconType) {
                case ICON_HOME: {
                    // <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>
                    path.moveTo(3 * s, 9.5f * s);
                    path.lineTo(12 * s, 2.5f * s);
                    path.lineTo(21 * s, 9.5f * s);
                    path.lineTo(21 * s, 20 * s);
                    path.quadTo(21 * s, 21.5f * s, 19.5f * s, 21.5f * s);
                    path.lineTo(4.5f * s, 21.5f * s);
                    path.quadTo(3 * s, 21.5f * s, 3 * s, 20 * s);
                    path.close();
                    path.moveTo(9 * s, 21.5f * s);
                    path.lineTo(9 * s, 13 * s);
                    path.lineTo(15 * s, 13 * s);
                    path.lineTo(15 * s, 21.5f * s);
                    canvas.drawPath(path, paint);
                    break;
                }
                case ICON_VIDEO: {
                    // <path d="M23 7l-7 5 7 5V7z"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
                    rect.set(1.5f * s, 5.5f * s, 15.5f * s, 18.5f * s);
                    canvas.drawRoundRect(rect, 2.2f * s, 2.2f * s, paint);
                    path.moveTo(22.5f * s, 7.5f * s);
                    path.lineTo(16 * s, 12 * s);
                    path.lineTo(22.5f * s, 16.5f * s);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                }
                case ICON_GRID_1: {
                    rect.set(3.5f * s, 4.5f * s, 20.5f * s, 19.5f * s);
                    canvas.drawRoundRect(rect, 2f * s, 2f * s, paint);
                    break;
                }
                case ICON_GRID_4: {
                    // <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>...
                    rect.set(3 * s, 3 * s, 10 * s, 10 * s);
                    canvas.drawRoundRect(rect, 1.2f * s, 1.2f * s, paint);
                    rect.set(14 * s, 3 * s, 21 * s, 10 * s);
                    canvas.drawRoundRect(rect, 1.2f * s, 1.2f * s, paint);
                    rect.set(3 * s, 14 * s, 10 * s, 21 * s);
                    canvas.drawRoundRect(rect, 1.2f * s, 1.2f * s, paint);
                    rect.set(14 * s, 14 * s, 21 * s, 21 * s);
                    canvas.drawRoundRect(rect, 1.2f * s, 1.2f * s, paint);
                    break;
                }
                case ICON_GRID_9: {
                    // 3x3 cuadrados limpios
                    float[] pos = {2.5f, 9.75f, 17f};
                    float sz = 4.5f * s;
                    for (float r : pos) {
                        for (float c : pos) {
                            rect.set(c * s, r * s, c * s + sz, r * s + sz);
                            canvas.drawRoundRect(rect, 0.8f * s, 0.8f * s, paint);
                        }
                    }
                    break;
                }
                case ICON_PLAY: {
                    path.moveTo(6.5f * s, 4f * s);
                    path.lineTo(19.5f * s, 12f * s);
                    path.lineTo(6.5f * s, 20f * s);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                }
                case ICON_PAUSE: {
                    rect.set(6 * s, 4.5f * s, 10 * s, 19.5f * s);
                    canvas.drawRoundRect(rect, 1f * s, 1f * s, paint);
                    rect.set(14 * s, 4.5f * s, 18 * s, 19.5f * s);
                    canvas.drawRoundRect(rect, 1f * s, 1f * s, paint);
                    break;
                }
                case ICON_VOLUME_ON: {
                    path.moveTo(11 * s, 5 * s);
                    path.lineTo(6 * s, 9 * s);
                    path.lineTo(2.5f * s, 9 * s);
                    path.lineTo(2.5f * s, 15 * s);
                    path.lineTo(6 * s, 15 * s);
                    path.lineTo(11 * s, 19 * s);
                    path.close();
                    canvas.drawPath(path, paint);
                    rect.set(11 * s, 8.5f * s, 16.5f * s, 15.5f * s);
                    canvas.drawArc(rect, -55, 110, false, paint);
                    rect.set(11 * s, 5.5f * s, 20.5f * s, 18.5f * s);
                    canvas.drawArc(rect, -55, 110, false, paint);
                    break;
                }
                case ICON_VOLUME_OFF: {
                    path.moveTo(11 * s, 5 * s);
                    path.lineTo(6 * s, 9 * s);
                    path.lineTo(2.5f * s, 9 * s);
                    path.lineTo(2.5f * s, 15 * s);
                    path.lineTo(6 * s, 15 * s);
                    path.lineTo(11 * s, 19 * s);
                    path.close();
                    canvas.drawPath(path, paint);
                    canvas.drawLine(16 * s, 9.5f * s, 21.5f * s, 15 * s, paint);
                    canvas.drawLine(21.5f * s, 9.5f * s, 16 * s, 15 * s, paint);
                    break;
                }
                case ICON_CAMERA: {
                    rect.set(2 * s, 6.5f * s, 22 * s, 19.5f * s);
                    canvas.drawRoundRect(rect, 2f * s, 2f * s, paint);
                    canvas.drawCircle(12 * s, 13 * s, 3.6f * s, paint);
                    canvas.drawLine(8.5f * s, 4f * s, 15.5f * s, 4f * s, paint);
                    break;
                }
                case ICON_SERVER: {
                    rect.set(2.5f * s, 3.5f * s, 21.5f * s, 10.5f * s);
                    canvas.drawRoundRect(rect, 2f * s, 2f * s, paint);
                    rect.set(2.5f * s, 13.5f * s, 21.5f * s, 20.5f * s);
                    canvas.drawRoundRect(rect, 2f * s, 2f * s, paint);
                    canvas.drawCircle(6.5f * s, 7f * s, 1f * s, paint);
                    canvas.drawCircle(6.5f * s, 17f * s, 1f * s, paint);
                    break;
                }
                case ICON_USER: {
                    canvas.drawCircle(12 * s, 7.5f * s, 4f * s, paint);
                    rect.set(4 * s, 14.5f * s, 20 * s, 25f * s);
                    canvas.drawArc(rect, 180, 180, false, paint);
                    break;
                }
                case ICON_BELL: {
                    rect.set(6 * s, 3.5f * s, 18 * s, 16 * s);
                    canvas.drawArc(rect, 180, 180, false, paint);
                    canvas.drawLine(6 * s, 9.5f * s, 4 * s, 17 * s, paint);
                    canvas.drawLine(18 * s, 9.5f * s, 20 * s, 17 * s, paint);
                    canvas.drawLine(3.5f * s, 17 * s, 20.5f * s, 17 * s, paint);
                    rect.set(10 * s, 17 * s, 14 * s, 21 * s);
                    canvas.drawArc(rect, 0, 180, false, paint);
                    break;
                }
                case ICON_SEARCH: {
                    canvas.drawCircle(10.5f * s, 10.5f * s, 6.5f * s, paint);
                    canvas.drawLine(15.5f * s, 15.5f * s, 20.5f * s, 20.5f * s, paint);
                    break;
                }
                case ICON_PLUS: {
                    canvas.drawLine(12 * s, 5 * s, 12 * s, 19 * s, paint);
                    canvas.drawLine(5 * s, 12 * s, 19 * s, 12 * s, paint);
                    break;
                }
                case ICON_CLOSE: {
                    canvas.drawLine(6 * s, 6 * s, 18 * s, 18 * s, paint);
                    canvas.drawLine(18 * s, 6 * s, 6 * s, 18 * s, paint);
                    break;
                }
                case ICON_EYE: {
                    rect.set(2 * s, 6 * s, 22 * s, 18 * s);
                    canvas.drawOval(rect, paint);
                    canvas.drawCircle(12 * s, 12 * s, 2.8f * s, paint);
                    break;
                }
                case ICON_REFRESH: {
                    rect.set(4.5f * s, 4.5f * s, 19.5f * s, 19.5f * s);
                    canvas.drawArc(rect, -40, 290, false, paint);
                    path.moveTo(16.5f * s, 3.5f * s);
                    path.lineTo(19.5f * s, 7.5f * s);
                    path.lineTo(15f * s, 8f * s);
                    canvas.drawPath(path, paint);
                    break;
                }
                case ICON_CHEVRON_R: {
                    path.moveTo(9 * s, 6 * s);
                    path.lineTo(15 * s, 12 * s);
                    path.lineTo(9 * s, 18 * s);
                    canvas.drawPath(path, paint);
                    break;
                }
                case ICON_LOGOUT: {
                    path.moveTo(9 * s, 20 * s);
                    path.lineTo(4.5f * s, 20 * s);
                    path.lineTo(4.5f * s, 4 * s);
                    path.lineTo(9 * s, 4 * s);
                    canvas.drawPath(path, paint);
                    canvas.drawLine(10 * s, 12 * s, 20 * s, 12 * s, paint);
                    path.moveTo(16 * s, 8 * s);
                    path.lineTo(20.5f * s, 12 * s);
                    path.lineTo(16 * s, 16 * s);
                    canvas.drawPath(path, paint);
                    break;
                }
                case ICON_MORE_H: {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(5.5f * s, 12 * s, 1.6f * s, paint);
                    canvas.drawCircle(12 * s, 12 * s, 1.6f * s, paint);
                    canvas.drawCircle(18.5f * s, 12 * s, 1.6f * s, paint);
                    break;
                }
                case ICON_SHIELD: {
                    path.moveTo(12 * s, 2.5f * s);
                    path.lineTo(20 * s, 6 * s);
                    path.lineTo(20 * s, 12 * s);
                    path.quadTo(20 * s, 18.5f * s, 12 * s, 21.5f * s);
                    path.quadTo(4 * s, 18.5f * s, 4 * s, 12 * s);
                    path.lineTo(4 * s, 6 * s);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                }
                case ICON_HD:
                case ICON_SD: {
                    rect.set(2.5f * s, 5.5f * s, 21.5f * s, 18.5f * s);
                    canvas.drawRoundRect(rect, 2.5f * s, 2.5f * s, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextSize(8.8f * s);
                    paint.setTextAlign(Paint.Align.CENTER);
                    String t = (iconType == ICON_HD) ? "HD" : "SD";
                    canvas.drawText(t, 12 * s, 15.1f * s, paint);
                    break;
                }
            }

            canvas.restore();
        }
    }
}
