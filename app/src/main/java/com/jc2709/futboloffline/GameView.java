package com.jc2709.futboloffline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float w, h;
    private float fieldLeft, fieldTop, fieldRight, fieldBottom;
    private float goalTop, goalBottom;

    private float playerX, playerY;
    private float rivalX, rivalY;
    private float keeperLeftY, keeperRightY;
    private float ballX, ballY, ballVX, ballVY;

    private float playerRadius, ballRadius;
    private float joystickCX, joystickCY, joystickRadius;
    private float joystickDX, joystickDY;
    private float shootCX, shootCY, shootRadius;

    private int playerScore = 0;
    private int rivalScore = 0;
    private float matchTime = 90f;
    private boolean gameOver = false;
    private long lastFrameNanos = 0L;

    private int joystickPointerId = -1;

    public GameView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(20, 110, 45));
        setFocusable(true);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        w = width;
        h = height;

        fieldLeft = w * 0.08f;
        fieldRight = w * 0.92f;
        fieldTop = h * 0.09f;
        fieldBottom = h * 0.91f;
        goalTop = h * 0.36f;
        goalBottom = h * 0.64f;

        playerRadius = Math.min(w, h) * 0.035f;
        ballRadius = playerRadius * 0.48f;

        joystickCX = w * 0.13f;
        joystickCY = h * 0.78f;
        joystickRadius = Math.min(w, h) * 0.11f;

        shootCX = w * 0.87f;
        shootCY = h * 0.78f;
        shootRadius = Math.min(w, h) * 0.085f;

        resetPositions();
    }

    private void resetPositions() {
        playerX = w * 0.27f;
        playerY = h * 0.50f;
        rivalX = w * 0.73f;
        rivalY = h * 0.50f;
        keeperLeftY = h * 0.50f;
        keeperRightY = h * 0.50f;
        ballX = w * 0.50f;
        ballY = h * 0.50f;
        ballVX = 0f;
        ballVY = 0f;
    }

    private void restartMatch() {
        playerScore = 0;
        rivalScore = 0;
        matchTime = 90f;
        gameOver = false;
        resetPositions();
        lastFrameNanos = System.nanoTime();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        if (dt > 0.033f) dt = 0.033f;

        if (!gameOver && w > 0f) {
            update(dt);
        }

        drawPitch(canvas);
        drawEntities(canvas);
        drawHud(canvas);
        drawControls(canvas);

        if (gameOver) {
            drawGameOver(canvas);
        } else {
            postInvalidateOnAnimation();
        }
    }

    private void update(float dt) {
        matchTime -= dt;
        if (matchTime <= 0f) {
            matchTime = 0f;
            gameOver = true;
            return;
        }

        float playerSpeed = Math.min(w, h) * 0.55f;
        playerX += joystickDX * playerSpeed * dt;
        playerY += joystickDY * playerSpeed * dt;
        clampPlayer();

        // El rival persigue la pelota con una IA sencilla.
        float rx = ballX - rivalX;
        float ry = ballY - rivalY;
        float rd = length(rx, ry);
        if (rd > 1f) {
            float rivalSpeed = Math.min(w, h) * 0.35f;
            rivalX += (rx / rd) * rivalSpeed * dt;
            rivalY += (ry / rd) * rivalSpeed * dt;
        }
        rivalX = clamp(rivalX, fieldLeft + playerRadius, fieldRight - playerRadius);
        rivalY = clamp(rivalY, fieldTop + playerRadius, fieldBottom - playerRadius);

        // Control suave de los arqueros siguiendo la pelota.
        float keeperSpeed = Math.min(w, h) * 0.40f;
        keeperLeftY = moveToward(keeperLeftY, ballY, keeperSpeed * dt);
        keeperRightY = moveToward(keeperRightY, ballY, keeperSpeed * dt);
        keeperLeftY = clamp(keeperLeftY, goalTop + playerRadius, goalBottom - playerRadius);
        keeperRightY = clamp(keeperRightY, goalTop + playerRadius, goalBottom - playerRadius);

        // Empuje al conducir la pelota.
        resolveDribble(playerX, playerY, true);
        resolveDribble(rivalX, rivalY, false);

        // El rival dispara automáticamente si está suficientemente cerca.
        if (distance(rivalX, rivalY, ballX, ballY) < playerRadius + ballRadius + 8f) {
            kickToward(fieldLeft - w * 0.05f, h * 0.50f, 1.0f);
        }

        // Física básica de pelota.
        ballX += ballVX * dt;
        ballY += ballVY * dt;
        float friction = (float) Math.pow(0.22, dt);
        ballVX *= friction;
        ballVY *= friction;

        // Rebote contra líneas laterales y de fondo fuera de la portería.
        if (ballY - ballRadius < fieldTop) {
            ballY = fieldTop + ballRadius;
            ballVY = Math.abs(ballVY) * 0.72f;
        } else if (ballY + ballRadius > fieldBottom) {
            ballY = fieldBottom - ballRadius;
            ballVY = -Math.abs(ballVY) * 0.72f;
        }

        if (ballX - ballRadius < fieldLeft && (ballY < goalTop || ballY > goalBottom)) {
            ballX = fieldLeft + ballRadius;
            ballVX = Math.abs(ballVX) * 0.72f;
        }
        if (ballX + ballRadius > fieldRight && (ballY < goalTop || ballY > goalBottom)) {
            ballX = fieldRight - ballRadius;
            ballVX = -Math.abs(ballVX) * 0.72f;
        }

        resolveKeeperCollision(fieldLeft + playerRadius * 0.55f, keeperLeftY, true);
        resolveKeeperCollision(fieldRight - playerRadius * 0.55f, keeperRightY, false);

        // Gol del rival.
        if (ballX + ballRadius < fieldLeft && ballY >= goalTop && ballY <= goalBottom) {
            rivalScore++;
            resetPositions();
            return;
        }

        // Gol del jugador.
        if (ballX - ballRadius > fieldRight && ballY >= goalTop && ballY <= goalBottom) {
            playerScore++;
            resetPositions();
        }
    }

    private void resolveDribble(float px, float py, boolean isPlayer) {
        float dx = ballX - px;
        float dy = ballY - py;
        float dist = length(dx, dy);
        float minDist = playerRadius + ballRadius;
        if (dist > 0f && dist < minDist) {
            float nx = dx / dist;
            float ny = dy / dist;
            ballX = px + nx * minDist;
            ballY = py + ny * minDist;
            float push = isPlayer ? 65f : 45f;
            ballVX += nx * push;
            ballVY += ny * push;
        }
    }

    private void resolveKeeperCollision(float kx, float ky, boolean leftKeeper) {
        float dx = ballX - kx;
        float dy = ballY - ky;
        float dist = length(dx, dy);
        float keeperRadius = playerRadius * 0.88f;
        float minDist = keeperRadius + ballRadius;
        if (dist > 0f && dist < minDist) {
            float nx = dx / dist;
            float ny = dy / dist;
            ballX = kx + nx * minDist;
            ballY = ky + ny * minDist;
            float speed = Math.min(w, h) * 0.72f;
            ballVX = (leftKeeper ? Math.abs(nx) : -Math.abs(nx)) * speed;
            ballVY = ny * speed * 0.55f;
        }
    }

    private void playerShoot() {
        if (gameOver) return;
        float d = distance(playerX, playerY, ballX, ballY);
        if (d <= playerRadius + ballRadius + Math.min(w, h) * 0.05f) {
            float targetY = h * 0.50f + (ballY - playerY) * 1.5f;
            kickToward(fieldRight + w * 0.07f, targetY, 1.22f);
        }
    }

    private void kickToward(float tx, float ty, float power) {
        float dx = tx - ballX;
        float dy = ty - ballY;
        float d = length(dx, dy);
        if (d <= 0f) return;
        float speed = Math.min(w, h) * 1.18f * power;
        ballVX = (dx / d) * speed;
        ballVY = (dy / d) * speed;
    }

    private void clampPlayer() {
        playerX = clamp(playerX, fieldLeft + playerRadius, fieldRight - playerRadius);
        playerY = clamp(playerY, fieldTop + playerRadius, fieldBottom - playerRadius);
    }

    private void drawPitch(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(26, 132, 55));
        canvas.drawRect(fieldLeft, fieldTop, fieldRight, fieldBottom, paint);

        // Franjas del césped.
        paint.setColor(Color.rgb(30, 145, 60));
        float stripe = (fieldRight - fieldLeft) / 8f;
        for (int i = 0; i < 8; i += 2) {
            canvas.drawRect(fieldLeft + i * stripe, fieldTop, fieldLeft + (i + 1) * stripe, fieldBottom, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, h * 0.005f));
        paint.setColor(Color.WHITE);
        canvas.drawRect(fieldLeft, fieldTop, fieldRight, fieldBottom, paint);
        canvas.drawLine(w * 0.50f, fieldTop, w * 0.50f, fieldBottom, paint);
        canvas.drawCircle(w * 0.50f, h * 0.50f, Math.min(w, h) * 0.105f, paint);

        float areaW = (fieldRight - fieldLeft) * 0.16f;
        float areaTop = h * 0.29f;
        float areaBottom = h * 0.71f;
        canvas.drawRect(fieldLeft, areaTop, fieldLeft + areaW, areaBottom, paint);
        canvas.drawRect(fieldRight - areaW, areaTop, fieldRight, areaBottom, paint);

        float goalDepth = w * 0.035f;
        canvas.drawRect(fieldLeft - goalDepth, goalTop, fieldLeft, goalBottom, paint);
        canvas.drawRect(fieldRight, goalTop, fieldRight + goalDepth, goalBottom, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(w * 0.50f, h * 0.50f, 5f, paint);
    }

    private void drawEntities(Canvas canvas) {
        // Jugador azul.
        paint.setColor(Color.rgb(35, 110, 255));
        canvas.drawCircle(playerX, playerY, playerRadius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(playerRadius * 0.95f);
        paint.setFakeBoldText(true);
        canvas.drawText("10", playerX, playerY + playerRadius * 0.32f, paint);

        // Rival rojo.
        paint.setColor(Color.rgb(235, 65, 65));
        canvas.drawCircle(rivalX, rivalY, playerRadius, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText("9", rivalX, rivalY + playerRadius * 0.32f, paint);

        // Arqueros.
        float keeperRadius = playerRadius * 0.88f;
        paint.setColor(Color.rgb(255, 196, 0));
        canvas.drawCircle(fieldLeft + playerRadius * 0.55f, keeperLeftY, keeperRadius, paint);
        paint.setColor(Color.rgb(255, 145, 0));
        canvas.drawCircle(fieldRight - playerRadius * 0.55f, keeperRightY, keeperRadius, paint);

        // Pelota.
        paint.setColor(Color.WHITE);
        canvas.drawCircle(ballX, ballY, ballRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, ballRadius * 0.14f));
        paint.setColor(Color.BLACK);
        canvas.drawCircle(ballX, ballY, ballRadius, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(ballX, ballY, ballRadius * 0.28f, paint);

        paint.setFakeBoldText(false);
    }

    private void drawHud(Canvas canvas) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(26f, h * 0.055f));
        int seconds = (int) Math.ceil(matchTime);
        canvas.drawText(playerScore + "  -  " + rivalScore, w * 0.50f, h * 0.07f, paint);

        paint.setTextSize(Math.max(18f, h * 0.035f));
        canvas.drawText(seconds + " s", w * 0.50f, h * 0.12f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawControls(Canvas canvas) {
        // Joystick.
        paint.setColor(Color.argb(110, 255, 255, 255));
        canvas.drawCircle(joystickCX, joystickCY, joystickRadius, paint);
        paint.setColor(Color.argb(190, 255, 255, 255));
        canvas.drawCircle(
                joystickCX + joystickDX * joystickRadius * 0.55f,
                joystickCY + joystickDY * joystickRadius * 0.55f,
                joystickRadius * 0.40f,
                paint
        );

        // Botón de tiro.
        paint.setColor(Color.argb(210, 245, 70, 65));
        canvas.drawCircle(shootCX, shootCY, shootRadius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(18f, shootRadius * 0.48f));
        canvas.drawText("TIRO", shootCX, shootCY + shootRadius * 0.16f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawGameOver(Canvas canvas) {
        paint.setColor(Color.argb(175, 0, 0, 0));
        canvas.drawRect(0, 0, w, h, paint);

        String result;
        if (playerScore > rivalScore) result = "¡GANASTE!";
        else if (playerScore < rivalScore) result = "PERDISTE";
        else result = "EMPATE";

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(36f, h * 0.09f));
        canvas.drawText(result, w * 0.50f, h * 0.42f, paint);

        paint.setTextSize(Math.max(26f, h * 0.055f));
        canvas.drawText(playerScore + " - " + rivalScore, w * 0.50f, h * 0.52f, paint);

        float bw = w * 0.24f;
        float bh = h * 0.12f;
        RectF button = new RectF(w * 0.50f - bw / 2f, h * 0.61f, w * 0.50f + bw / 2f, h * 0.61f + bh);
        paint.setColor(Color.rgb(35, 125, 255));
        canvas.drawRoundRect(button, 22f, 22f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(18f, h * 0.038f));
        canvas.drawText("REINICIAR", w * 0.50f, h * 0.61f + bh * 0.65f, paint);
        paint.setFakeBoldText(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(actionIndex);
            float y = event.getY(actionIndex);
            int pointerId = event.getPointerId(actionIndex);

            if (gameOver) {
                float bw = w * 0.24f;
                float bh = h * 0.12f;
                if (x >= w * 0.50f - bw / 2f && x <= w * 0.50f + bw / 2f &&
                        y >= h * 0.61f && y <= h * 0.61f + bh) {
                    restartMatch();
                }
                return true;
            }

            if (distance(x, y, joystickCX, joystickCY) <= joystickRadius * 1.25f && joystickPointerId == -1) {
                joystickPointerId = pointerId;
                updateJoystick(x, y);
            } else if (distance(x, y, shootCX, shootCY) <= shootRadius * 1.35f) {
                playerShoot();
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (joystickPointerId != -1) {
                int index = event.findPointerIndex(joystickPointerId);
                if (index >= 0) updateJoystick(event.getX(index), event.getY(index));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            int pointerId = event.getPointerId(actionIndex);
            if (pointerId == joystickPointerId || action == MotionEvent.ACTION_CANCEL) {
                joystickPointerId = -1;
                joystickDX = 0f;
                joystickDY = 0f;
            }
        }

        return true;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joystickCX;
        float dy = y - joystickCY;
        float d = length(dx, dy);
        if (d <= 1f) {
            joystickDX = 0f;
            joystickDY = 0f;
            return;
        }
        float magnitude = Math.min(1f, d / joystickRadius);
        joystickDX = (dx / d) * magnitude;
        joystickDY = (dy / d) * magnitude;
    }

    private static float moveToward(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) return target;
        return current + Math.signum(target - current) * maxDelta;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return length(x2 - x1, y2 - y1);
    }
}
