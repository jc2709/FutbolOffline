package com.jc2709.futboloffline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View {
    private static final int MAX_PLAYERS = 5;
    private static final int FREE_BALL = -1;
    private static final int RIVAL_OWNER_OFFSET = 100;

    private enum ScreenState { MENU, PLAYING, GAME_OVER }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float w, h;
    private float fieldLeft, fieldTop, fieldRight, fieldBottom;
    private float goalTop, goalBottom;
    private float playerRadius, ballRadius;

    private final float[] teamX = new float[MAX_PLAYERS];
    private final float[] teamY = new float[MAX_PLAYERS];
    private final float[] teamFacingX = new float[MAX_PLAYERS];
    private final float[] teamFacingY = new float[MAX_PLAYERS];

    private final float[] rivalX = new float[MAX_PLAYERS];
    private final float[] rivalY = new float[MAX_PLAYERS];
    private final float[] rivalFacingX = new float[MAX_PLAYERS];
    private final float[] rivalFacingY = new float[MAX_PLAYERS];
    private final float[] rivalStun = new float[MAX_PLAYERS];

    private float keeperLeftY, keeperRightY;
    private float ballX, ballY, ballVX, ballVY;
    private int ballOwner = FREE_BALL;
    private int controlledIndex = 0;
    private int pendingPassTarget = -1;

    private float joystickCX, joystickCY, joystickRadius;
    private float joystickDX, joystickDY;
    private float shootCX, shootCY, actionRadius;
    private float passCX, passCY;
    private float forceCX, forceCY;
    private int joystickPointerId = -1;

    private ScreenState state = ScreenState.MENU;

    // Configuración del menú.
    private int configuredMatchSeconds = 90;
    private boolean extraTimeEnabled = false;
    private boolean goldenGoalEnabled = false;
    private int configuredTeamSize = 1;

    // Estado del partido.
    private int teamSize = 1;
    private int playerScore = 0;
    private int rivalScore = 0;
    private float matchTime = 90f;
    private boolean inExtraTime = false;
    private boolean goldenGoalPhase = false;
    private String phaseLabel = "TIEMPO";
    private float rivalKickCooldown = 0f;
    private float forceCooldown = 0f;
    private long lastFrameNanos = 0L;

    private RectF timeMinusRect = new RectF();
    private RectF timePlusRect = new RectF();
    private RectF playersMinusRect = new RectF();
    private RectF playersPlusRect = new RectF();
    private RectF extraRect = new RectF();
    private RectF goldenRect = new RectF();
    private RectF startRect = new RectF();
    private RectF restartRect = new RectF();
    private RectF menuRect = new RectF();

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

        fieldLeft = w * 0.06f;
        fieldRight = w * 0.94f;
        fieldTop = h * 0.08f;
        fieldBottom = h * 0.92f;
        goalTop = h * 0.36f;
        goalBottom = h * 0.64f;

        playerRadius = Math.min(w, h) * 0.031f;
        ballRadius = playerRadius * 0.46f;

        joystickCX = w * 0.13f;
        joystickCY = h * 0.79f;
        joystickRadius = Math.min(w, h) * 0.105f;

        actionRadius = Math.min(w, h) * 0.060f;
        shootCX = w * 0.88f;
        shootCY = h * 0.76f;
        passCX = w * 0.76f;
        passCY = h * 0.84f;
        forceCX = w * 0.88f;
        forceCY = h * 0.89f;

        layoutMenuRects();
        resetPositions();
    }

    private void layoutMenuRects() {
        float cx = w * 0.50f;
        float rowW = w * 0.62f;
        float buttonW = w * 0.09f;
        float rowH = h * 0.085f;

        timeMinusRect.set(cx - rowW / 2f, h * 0.30f, cx - rowW / 2f + buttonW, h * 0.30f + rowH);
        timePlusRect.set(cx + rowW / 2f - buttonW, h * 0.30f, cx + rowW / 2f, h * 0.30f + rowH);

        extraRect.set(cx - rowW / 2f, h * 0.42f, cx + rowW / 2f, h * 0.42f + rowH);
        goldenRect.set(cx - rowW / 2f, h * 0.53f, cx + rowW / 2f, h * 0.53f + rowH);

        playersMinusRect.set(cx - rowW / 2f, h * 0.64f, cx - rowW / 2f + buttonW, h * 0.64f + rowH);
        playersPlusRect.set(cx + rowW / 2f - buttonW, h * 0.64f, cx + rowW / 2f, h * 0.64f + rowH);

        startRect.set(cx - w * 0.17f, h * 0.78f, cx + w * 0.17f, h * 0.88f);

        restartRect.set(cx - w * 0.21f, h * 0.61f, cx - w * 0.01f, h * 0.72f);
        menuRect.set(cx + w * 0.01f, h * 0.61f, cx + w * 0.21f, h * 0.72f);
    }

    private void startMatch() {
        teamSize = configuredTeamSize;
        playerScore = 0;
        rivalScore = 0;
        matchTime = configuredMatchSeconds;
        inExtraTime = false;
        goldenGoalPhase = false;
        phaseLabel = "TIEMPO";
        state = ScreenState.PLAYING;
        controlledIndex = 0;
        resetPositions();
        lastFrameNanos = System.nanoTime();
        invalidate();
    }

    private void restartMatch() {
        startMatch();
    }

    private void resetPositions() {
        if (w <= 0f || h <= 0f) return;

        float[] lanes = {0.50f, 0.30f, 0.70f, 0.42f, 0.58f};
        float[] teamXs = {0.29f, 0.36f, 0.36f, 0.46f, 0.46f};
        float[] rivalXs = {0.71f, 0.64f, 0.64f, 0.54f, 0.54f};

        for (int i = 0; i < MAX_PLAYERS; i++) {
            teamX[i] = w * teamXs[i];
            teamY[i] = h * lanes[i];
            teamFacingX[i] = 1f;
            teamFacingY[i] = 0f;

            rivalX[i] = w * rivalXs[i];
            rivalY[i] = h * lanes[i];
            rivalFacingX[i] = -1f;
            rivalFacingY[i] = 0f;
            rivalStun[i] = 0f;
        }

        keeperLeftY = h * 0.50f;
        keeperRightY = h * 0.50f;

        ballX = w * 0.50f;
        ballY = h * 0.50f;
        ballVX = 0f;
        ballVY = 0f;
        ballOwner = FREE_BALL;
        pendingPassTarget = -1;
        rivalKickCooldown = 0.35f;
        forceCooldown = 0f;
        joystickDX = 0f;
        joystickDY = 0f;
        joystickPointerId = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (state == ScreenState.MENU) {
            drawMenu(canvas);
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        if (dt > 0.033f) dt = 0.033f;

        if (state == ScreenState.PLAYING && w > 0f) {
            update(dt);
        }

        drawPitch(canvas);
        drawEntities(canvas);
        drawHud(canvas);
        drawControls(canvas);

        if (state == ScreenState.GAME_OVER) {
            drawGameOver(canvas);
        } else {
            postInvalidateOnAnimation();
        }
    }

    private void update(float dt) {
        rivalKickCooldown = Math.max(0f, rivalKickCooldown - dt);
        forceCooldown = Math.max(0f, forceCooldown - dt);
        for (int i = 0; i < teamSize; i++) {
            rivalStun[i] = Math.max(0f, rivalStun[i] - dt);
        }

        updateClock(dt);
        if (state != ScreenState.PLAYING) return;

        updateControlledPlayer(dt);
        updateTeammates(dt);
        updateRivals(dt);
        updateKeepers(dt);

        if (ballOwner != FREE_BALL) {
            attachBallToOwner();
        } else {
            updateFreeBall(dt);
            tryAcquirePossession();
        }

        if (ballOwner >= RIVAL_OWNER_OFFSET) {
            rivalWithBallBehavior(dt);
        }

        checkGoals();
    }

    private void updateClock(float dt) {
        if (goldenGoalPhase) {
            // Gol de oro sin reloj: el próximo gol termina el partido.
            return;
        }

        matchTime -= dt;
        if (matchTime > 0f) return;
        matchTime = 0f;

        if (!inExtraTime && playerScore == rivalScore && extraTimeEnabled) {
            inExtraTime = true;
            phaseLabel = goldenGoalEnabled ? "PRÓRROGA · GOL DE ORO" : "PRÓRROGA";
            matchTime = Math.max(30f, configuredMatchSeconds / 3f);
            return;
        }

        if (!inExtraTime && playerScore == rivalScore && goldenGoalEnabled) {
            goldenGoalPhase = true;
            phaseLabel = "GOL DE ORO";
            return;
        }

        state = ScreenState.GAME_OVER;
    }

    private void updateControlledPlayer(float dt) {
        float input = length(joystickDX, joystickDY);
        float speed = Math.min(w, h) * 0.52f;
        int i = controlledIndex;

        teamX[i] += joystickDX * speed * dt;
        teamY[i] += joystickDY * speed * dt;
        clampTeamPlayer(i);

        if (input > 0.08f) {
            teamFacingX[i] = joystickDX / input;
            teamFacingY[i] = joystickDY / input;
        }
    }

    private void updateTeammates(float dt) {
        for (int i = 0; i < teamSize; i++) {
            if (i == controlledIndex) continue;

            float targetX;
            float targetY;

            if (ballOwner == FREE_BALL && isClosestTeamToBall(i)) {
                targetX = ballX;
                targetY = ballY;
            } else {
                float[] formation = teamFormationTarget(i);
                targetX = formation[0];
                targetY = formation[1];
            }

            moveTeamPlayerToward(i, targetX, targetY, Math.min(w, h) * 0.25f * dt);
        }
    }

    private void updateRivals(float dt) {
        int chaser = findClosestRivalToBallOrOwner();

        for (int i = 0; i < teamSize; i++) {
            if (rivalStun[i] > 0f) continue;

            float targetX;
            float targetY;

            if (ballOwner == RIVAL_OWNER_OFFSET + i) {
                targetX = fieldLeft + w * 0.10f;
                targetY = h * 0.50f;
            } else if (i == chaser) {
                if (ballOwner >= 0 && ballOwner < RIVAL_OWNER_OFFSET) {
                    targetX = teamX[ballOwner];
                    targetY = teamY[ballOwner];
                } else {
                    targetX = ballX;
                    targetY = ballY;
                }
            } else {
                float[] formation = rivalFormationTarget(i);
                targetX = formation[0];
                targetY = formation[1];
            }

            moveRivalToward(i, targetX, targetY, Math.min(w, h) * 0.27f * dt);
        }

        // Un rival que alcanza al portador puede hacer que el balón quede dividido.
        if (ballOwner >= 0 && ballOwner < RIVAL_OWNER_OFFSET) {
            int owner = ballOwner;
            int closest = findClosestRivalTo(teamX[owner], teamY[owner]);
            float d = distance(rivalX[closest], rivalY[closest], teamX[owner], teamY[owner]);
            if (d < playerRadius * 1.72f && rivalStun[closest] <= 0f && forceCooldown <= 0f) {
                if (Math.random() < 0.010) {
                    releaseBall((teamFacingX[owner] + rivalFacingX[closest]) * 0.5f,
                            (teamFacingY[owner] + rivalFacingY[closest]) * 0.5f,
                            Math.min(w, h) * 0.22f);
                }
            }
        }
    }

    private void updateKeepers(float dt) {
        float keeperSpeed = Math.min(w, h) * 0.38f;
        keeperLeftY = moveToward(keeperLeftY, ballY, keeperSpeed * dt);
        keeperRightY = moveToward(keeperRightY, ballY, keeperSpeed * dt);
        keeperLeftY = clamp(keeperLeftY, goalTop + playerRadius, goalBottom - playerRadius);
        keeperRightY = clamp(keeperRightY, goalTop + playerRadius, goalBottom - playerRadius);
    }

    private void updateFreeBall(float dt) {
        ballX += ballVX * dt;
        ballY += ballVY * dt;

        float friction = (float) Math.pow(0.26, dt);
        ballVX *= friction;
        ballVY *= friction;

        resolveBallBounds();
        escapeCornerIfNeeded();

        resolveKeeperCollision(fieldLeft + playerRadius * 0.55f, keeperLeftY, true);
        resolveKeeperCollision(fieldRight - playerRadius * 0.55f, keeperRightY, false);
        resolveBallBounds();
    }

    private void tryAcquirePossession() {
        float capture = playerRadius + ballRadius + playerRadius * 0.25f;
        float speed = length(ballVX, ballVY);
        float maxCaptureSpeed = Math.min(w, h) * 0.72f;

        int bestTeam = -1;
        float bestTeamDist = Float.MAX_VALUE;
        for (int i = 0; i < teamSize; i++) {
            float d = distance(teamX[i], teamY[i], ballX, ballY);
            if (d < capture && d < bestTeamDist) {
                bestTeamDist = d;
                bestTeam = i;
            }
        }

        int bestRival = -1;
        float bestRivalDist = Float.MAX_VALUE;
        for (int i = 0; i < teamSize; i++) {
            if (rivalStun[i] > 0f) continue;
            float d = distance(rivalX[i], rivalY[i], ballX, ballY);
            if (d < capture && d < bestRivalDist) {
                bestRivalDist = d;
                bestRival = i;
            }
        }

        if (speed > maxCaptureSpeed && pendingPassTarget < 0) return;

        if (pendingPassTarget >= 0) {
            float d = distance(teamX[pendingPassTarget], teamY[pendingPassTarget], ballX, ballY);
            if (d < capture * 1.30f) {
                ballOwner = pendingPassTarget;
                controlledIndex = pendingPassTarget;
                pendingPassTarget = -1;
                ballVX = 0f;
                ballVY = 0f;
                return;
            }
        }

        if (bestTeam >= 0 && (bestRival < 0 || bestTeamDist <= bestRivalDist)) {
            ballOwner = bestTeam;
            controlledIndex = bestTeam;
            pendingPassTarget = -1;
            ballVX = 0f;
            ballVY = 0f;
        } else if (bestRival >= 0) {
            ballOwner = RIVAL_OWNER_OFFSET + bestRival;
            pendingPassTarget = -1;
            ballVX = 0f;
            ballVY = 0f;
        }
    }

    private void attachBallToOwner() {
        float ox, oy, fx, fy;

        if (ballOwner >= RIVAL_OWNER_OFFSET) {
            int i = ballOwner - RIVAL_OWNER_OFFSET;
            if (i < 0 || i >= teamSize || rivalStun[i] > 0f) {
                ballOwner = FREE_BALL;
                return;
            }
            ox = rivalX[i];
            oy = rivalY[i];
            fx = rivalFacingX[i];
            fy = rivalFacingY[i];
        } else {
            int i = ballOwner;
            if (i < 0 || i >= teamSize) {
                ballOwner = FREE_BALL;
                return;
            }
            ox = teamX[i];
            oy = teamY[i];
            fx = teamFacingX[i];
            fy = teamFacingY[i];
        }

        float d = playerRadius + ballRadius * 0.72f;
        ballX = ox + fx * d;
        ballY = oy + fy * d;
        ballVX = 0f;
        ballVY = 0f;

        // Mantiene la posesión visual dentro del campo, excepto frente a la portería.
        ballY = clamp(ballY, fieldTop + ballRadius, fieldBottom - ballRadius);
        boolean inGoalMouth = ballY >= goalTop && ballY <= goalBottom;
        if (!inGoalMouth) {
            ballX = clamp(ballX, fieldLeft + ballRadius, fieldRight - ballRadius);
        }
    }

    private void playerShoot() {
        if (state != ScreenState.PLAYING) return;

        if (ballOwner == controlledIndex) {
            float targetY = h * 0.50f + teamFacingY[controlledIndex] * h * 0.12f;
            kickBall(fieldRight + w * 0.08f, targetY, 1.20f);
            return;
        }

        if (ballOwner == FREE_BALL &&
                distance(teamX[controlledIndex], teamY[controlledIndex], ballX, ballY) <
                        playerRadius + ballRadius + playerRadius * 0.7f) {
            float targetY = h * 0.50f + teamFacingY[controlledIndex] * h * 0.12f;
            kickBall(fieldRight + w * 0.08f, targetY, 1.15f);
        }
    }

    private void playerPass() {
        if (state != ScreenState.PLAYING || ballOwner != controlledIndex || teamSize <= 1) return;

        int target = choosePassTarget();
        if (target < 0) return;

        pendingPassTarget = target;
        float tx = teamX[target] + teamFacingX[target] * playerRadius * 0.4f;
        float ty = teamY[target] + teamFacingY[target] * playerRadius * 0.4f;
        kickBall(tx, ty, 0.66f);
    }

    private int choosePassTarget() {
        int source = controlledIndex;
        int best = -1;
        float bestScore = Float.MAX_VALUE;

        for (int i = 0; i < teamSize; i++) {
            if (i == source) continue;
            float d = distance(teamX[source], teamY[source], teamX[i], teamY[i]);
            float forwardBonus = (teamX[i] > teamX[source]) ? -playerRadius * 3f : 0f;
            float score = d + forwardBonus;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private void playerForce() {
        if (state != ScreenState.PLAYING || forceCooldown > 0f) return;

        int p = controlledIndex;
        int target = findClosestRivalTo(teamX[p], teamY[p]);
        float d = distance(teamX[p], teamY[p], rivalX[target], rivalY[target]);
        float range = playerRadius * 2.45f;

        if (d > range || rivalStun[target] > 0f) return;

        float dx = rivalX[target] - teamX[p];
        float dy = rivalY[target] - teamY[p];
        float len = length(dx, dy);
        if (len < 1f) {
            dx = teamFacingX[p];
            dy = teamFacingY[p];
            len = 1f;
        }
        float nx = dx / len;
        float ny = dy / len;

        // El rival pierde estabilidad durante un instante y se desplaza hacia atrás.
        rivalStun[target] = 0.85f;
        rivalX[target] += nx * playerRadius * 0.75f;
        rivalY[target] += ny * playerRadius * 0.75f;
        clampRival(target);

        if (ballOwner == RIVAL_OWNER_OFFSET + target) {
            ballOwner = FREE_BALL;
            pendingPassTarget = -1;
            ballX = rivalX[target] - nx * (playerRadius + ballRadius);
            ballY = rivalY[target] - ny * (playerRadius + ballRadius);
            ballVX = -nx * Math.min(w, h) * 0.28f;
            ballVY = -ny * Math.min(w, h) * 0.28f;
            resolveBallBounds();
        }

        forceCooldown = 1.15f;
    }

    private void rivalWithBallBehavior(float dt) {
        int i = ballOwner - RIVAL_OWNER_OFFSET;
        if (i < 0 || i >= teamSize || rivalStun[i] > 0f) return;

        boolean closeEnoughToShoot = rivalX[i] < w * 0.40f;
        boolean facingGoal = rivalFacingX[i] < -0.35f;

        if (closeEnoughToShoot && facingGoal && rivalKickCooldown <= 0f) {
            float targetY = h * 0.50f + (rivalY[i] - h * 0.50f) * 0.25f;
            kickBall(fieldLeft - w * 0.08f, targetY, 0.98f);
            rivalKickCooldown = 0.85f;
        }
    }

    private void kickBall(float tx, float ty, float power) {
        float dx = tx - ballX;
        float dy = ty - ballY;
        float d = length(dx, dy);
        if (d <= 0f) return;

        ballOwner = FREE_BALL;
        float speed = Math.min(w, h) * 1.18f * power;
        ballVX = (dx / d) * speed;
        ballVY = (dy / d) * speed;
    }

    private void releaseBall(float dx, float dy, float speed) {
        float d = length(dx, dy);
        if (d < 0.1f) {
            dx = 1f;
            dy = 0f;
            d = 1f;
        }
        ballOwner = FREE_BALL;
        pendingPassTarget = -1;
        ballVX = dx / d * speed;
        ballVY = dy / d * speed;
    }

    private void checkGoals() {
        if (ballOwner != FREE_BALL) return;

        if (ballX + ballRadius < fieldLeft && ballY >= goalTop && ballY <= goalBottom) {
            rivalScore++;
            afterGoal(false);
            return;
        }

        if (ballX - ballRadius > fieldRight && ballY >= goalTop && ballY <= goalBottom) {
            playerScore++;
            afterGoal(true);
        }
    }

    private void afterGoal(boolean playerScored) {
        if (goldenGoalPhase || (inExtraTime && goldenGoalEnabled)) {
            state = ScreenState.GAME_OVER;
            phaseLabel = "GOL DE ORO";
            return;
        }
        resetPositions();
    }

    private boolean isClosestTeamToBall(int index) {
        float d = distance(teamX[index], teamY[index], ballX, ballY);
        for (int i = 0; i < teamSize; i++) {
            if (i == index || i == controlledIndex) continue;
            if (distance(teamX[i], teamY[i], ballX, ballY) < d) return false;
        }
        return true;
    }

    private int findClosestRivalToBallOrOwner() {
        float tx = ballX;
        float ty = ballY;

        if (ballOwner >= 0 && ballOwner < RIVAL_OWNER_OFFSET) {
            tx = teamX[ballOwner];
            ty = teamY[ballOwner];
        }

        return findClosestRivalTo(tx, ty);
    }

    private int findClosestRivalTo(float x, float y) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < teamSize; i++) {
            if (rivalStun[i] > 0f) continue;
            float d = distance(rivalX[i], rivalY[i], x, y);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private float[] teamFormationTarget(int i) {
        float[] lanes = {0.50f, 0.27f, 0.73f, 0.40f, 0.60f};
        float[] xs = {0.31f, 0.39f, 0.39f, 0.50f, 0.50f};
        float shiftX = (ballX - w * 0.50f) * 0.18f;
        return new float[]{
                clamp(w * xs[i] + shiftX, fieldLeft + playerRadius, fieldRight - playerRadius),
                h * lanes[i]
        };
    }

    private float[] rivalFormationTarget(int i) {
        float[] lanes = {0.50f, 0.27f, 0.73f, 0.40f, 0.60f};
        float[] xs = {0.69f, 0.61f, 0.61f, 0.50f, 0.50f};
        float shiftX = (ballX - w * 0.50f) * 0.18f;
        return new float[]{
                clamp(w * xs[i] + shiftX, fieldLeft + playerRadius, fieldRight - playerRadius),
                h * lanes[i]
        };
    }

    private void moveTeamPlayerToward(int i, float tx, float ty, float maxDelta) {
        float dx = tx - teamX[i];
        float dy = ty - teamY[i];
        float d = length(dx, dy);
        if (d < 1f) return;

        float step = Math.min(maxDelta, d);
        float nx = dx / d;
        float ny = dy / d;
        teamX[i] += nx * step;
        teamY[i] += ny * step;
        teamFacingX[i] = nx;
        teamFacingY[i] = ny;
        clampTeamPlayer(i);
    }

    private void moveRivalToward(int i, float tx, float ty, float maxDelta) {
        float dx = tx - rivalX[i];
        float dy = ty - rivalY[i];
        float d = length(dx, dy);
        if (d < 1f) return;

        float step = Math.min(maxDelta, d);
        float nx = dx / d;
        float ny = dy / d;
        rivalX[i] += nx * step;
        rivalY[i] += ny * step;
        rivalFacingX[i] = nx;
        rivalFacingY[i] = ny;
        clampRival(i);
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
            ballOwner = FREE_BALL;
            pendingPassTarget = -1;
        }
    }

    private void resolveBallBounds() {
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
    }

    private void escapeCornerIfNeeded() {
        float margin = ballRadius * 1.35f;
        boolean nearLeft = ballX <= fieldLeft + ballRadius + margin;
        boolean nearRight = ballX >= fieldRight - ballRadius - margin;
        boolean nearTop = ballY <= fieldTop + ballRadius + margin;
        boolean nearBottom = ballY >= fieldBottom - ballRadius - margin;
        boolean inCorner = (nearLeft || nearRight) && (nearTop || nearBottom);

        float speed = length(ballVX, ballVY);
        float slowSpeed = Math.min(w, h) * 0.12f;

        if (inCorner && speed < slowSpeed) {
            float escapeSpeed = Math.min(w, h) * 0.24f;
            ballVX = (nearLeft ? 1f : -1f) * escapeSpeed;
            ballVY = (nearTop ? 1f : -1f) * escapeSpeed * 0.72f;
            float inset = ballRadius * 0.30f;
            ballX += nearLeft ? inset : -inset;
            ballY += nearTop ? inset : -inset;
        }
    }

    private void clampTeamPlayer(int i) {
        teamX[i] = clamp(teamX[i], fieldLeft + playerRadius, fieldRight - playerRadius);
        teamY[i] = clamp(teamY[i], fieldTop + playerRadius, fieldBottom - playerRadius);
    }

    private void clampRival(int i) {
        rivalX[i] = clamp(rivalX[i], fieldLeft + playerRadius, fieldRight - playerRadius);
        rivalY[i] = clamp(rivalY[i], fieldTop + playerRadius, fieldBottom - playerRadius);
    }

    private void drawPitch(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(26, 132, 55));
        canvas.drawRect(fieldLeft, fieldTop, fieldRight, fieldBottom, paint);

        paint.setColor(Color.rgb(30, 145, 60));
        float stripe = (fieldRight - fieldLeft) / 8f;
        for (int i = 0; i < 8; i += 2) {
            canvas.drawRect(fieldLeft + i * stripe, fieldTop,
                    fieldLeft + (i + 1) * stripe, fieldBottom, paint);
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
        for (int i = 0; i < teamSize; i++) {
            boolean controlled = i == controlledIndex;
            paint.setColor(controlled ? Color.rgb(35, 110, 255) : Color.rgb(70, 155, 255));
            canvas.drawCircle(teamX[i], teamY[i], playerRadius, paint);

            if (controlled) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(3f, playerRadius * 0.14f));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(teamX[i], teamY[i], playerRadius * 1.18f, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(playerRadius * 0.75f);
            paint.setFakeBoldText(true);
            canvas.drawText(String.valueOf(i + 7), teamX[i], teamY[i] + playerRadius * 0.26f, paint);
        }

        for (int i = 0; i < teamSize; i++) {
            paint.setColor(rivalStun[i] > 0f ? Color.rgb(155, 75, 75) : Color.rgb(235, 65, 65));
            canvas.drawCircle(rivalX[i], rivalY[i], playerRadius, paint);
            paint.setColor(Color.WHITE);
            canvas.drawText(String.valueOf(i + 7), rivalX[i], rivalY[i] + playerRadius * 0.26f, paint);

            if (rivalStun[i] > 0f) {
                paint.setTextSize(playerRadius * 0.72f);
                canvas.drawText("★", rivalX[i], rivalY[i] - playerRadius * 1.15f, paint);
            }
        }

        float keeperRadius = playerRadius * 0.88f;
        paint.setColor(Color.rgb(255, 196, 0));
        canvas.drawCircle(fieldLeft + playerRadius * 0.55f, keeperLeftY, keeperRadius, paint);
        paint.setColor(Color.rgb(255, 145, 0));
        canvas.drawCircle(fieldRight - playerRadius * 0.55f, keeperRightY, keeperRadius, paint);

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
        paint.setTextSize(Math.max(23f, h * 0.050f));
        canvas.drawText(playerScore + "  -  " + rivalScore, w * 0.50f, h * 0.055f, paint);

        paint.setTextSize(Math.max(14f, h * 0.027f));
        String timeText = goldenGoalPhase ? "SIN LÍMITE" : ((int) Math.ceil(matchTime)) + " s";
        canvas.drawText(phaseLabel + " · " + timeText, w * 0.50f, h * 0.095f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawControls(Canvas canvas) {
        paint.setColor(Color.argb(105, 255, 255, 255));
        canvas.drawCircle(joystickCX, joystickCY, joystickRadius, paint);
        paint.setColor(Color.argb(190, 255, 255, 255));
        canvas.drawCircle(
                joystickCX + joystickDX * joystickRadius * 0.55f,
                joystickCY + joystickDY * joystickRadius * 0.55f,
                joystickRadius * 0.40f,
                paint
        );

        drawActionButton(canvas, shootCX, shootCY, "TIRO", Color.rgb(235, 70, 65),
                1f);
        drawActionButton(canvas, passCX, passCY, "PASE", Color.rgb(55, 125, 245),
                teamSize > 1 ? 1f : 0.42f);
        drawActionButton(canvas, forceCX, forceCY, "FUERZA", Color.rgb(245, 155, 45),
                forceCooldown <= 0f ? 1f : 0.45f);
    }

    private void drawActionButton(Canvas canvas, float cx, float cy, String text, int color, float alphaFactor) {
        int alpha = (int) (215 * alphaFactor);
        paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, actionRadius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(13f, actionRadius * 0.34f));
        canvas.drawText(text, cx, cy + actionRadius * 0.12f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawMenu(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(12, 70, 32));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(32f, h * 0.070f));
        canvas.drawText("FÚTBOL OFFLINE", w * 0.50f, h * 0.14f, paint);

        paint.setTextSize(Math.max(15f, h * 0.029f));
        paint.setFakeBoldText(false);
        canvas.drawText("Configura el partido", w * 0.50f, h * 0.20f, paint);

        drawStepperRow(canvas, "Tiempo", configuredMatchSeconds + " s",
                timeMinusRect, timePlusRect, h * 0.30f);
        drawToggleRow(canvas, "Prórroga", extraTimeEnabled, extraRect);
        drawToggleRow(canvas, "Gol de oro", goldenGoalEnabled, goldenRect);
        drawStepperRow(canvas, "Jugadores de campo", configuredTeamSize + " vs " + configuredTeamSize,
                playersMinusRect, playersPlusRect, h * 0.64f);

        paint.setColor(Color.argb(215, 35, 125, 255));
        canvas.drawRoundRect(startRect, 24f, 24f, paint);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(21f, h * 0.040f));
        canvas.drawText("JUGAR", startRect.centerX(), startRect.centerY() + h * 0.014f, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(11f, h * 0.020f));
        paint.setColor(Color.argb(205, 255, 255, 255));
        canvas.drawText("Máximo 5 jugadores de campo por equipo · arqueros aparte",
                w * 0.50f, h * 0.93f, paint);
    }

    private void drawStepperRow(Canvas canvas, String label, String value, RectF minus, RectF plus, float top) {
        float rowLeft = w * 0.19f;
        float rowRight = w * 0.81f;
        float rowBottom = top + h * 0.085f;

        paint.setColor(Color.argb(75, 255, 255, 255));
        canvas.drawRoundRect(new RectF(rowLeft, top, rowRight, rowBottom), 18f, 18f, paint);

        drawSmallMenuButton(canvas, minus, "−");
        drawSmallMenuButton(canvas, plus, "+");

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(15f, h * 0.027f));
        canvas.drawText(label, w * 0.40f, top + h * 0.035f, paint);
        paint.setTextSize(Math.max(14f, h * 0.025f));
        canvas.drawText(value, w * 0.57f, top + h * 0.058f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawToggleRow(Canvas canvas, String label, boolean enabled, RectF rect) {
        paint.setColor(Color.argb(75, 255, 255, 255));
        canvas.drawRoundRect(rect, 18f, 18f, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(15f, h * 0.028f));
        canvas.drawText(label, rect.left + w * 0.035f, rect.centerY() + h * 0.010f, paint);

        float toggleW = w * 0.115f;
        float toggleH = h * 0.047f;
        RectF toggle = new RectF(rect.right - toggleW - w * 0.025f,
                rect.centerY() - toggleH / 2f,
                rect.right - w * 0.025f,
                rect.centerY() + toggleH / 2f);

        paint.setColor(enabled ? Color.rgb(45, 185, 85) : Color.rgb(105, 105, 105));
        canvas.drawRoundRect(toggle, toggleH / 2f, toggleH / 2f, paint);
        float knobX = enabled ? toggle.right - toggleH / 2f : toggle.left + toggleH / 2f;
        paint.setColor(Color.WHITE);
        canvas.drawCircle(knobX, toggle.centerY(), toggleH * 0.36f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawSmallMenuButton(Canvas canvas, RectF rect, String text) {
        paint.setColor(Color.argb(205, 35, 125, 255));
        canvas.drawRoundRect(rect, 16f, 16f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(20f, h * 0.040f));
        canvas.drawText(text, rect.centerX(), rect.centerY() + h * 0.014f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawGameOver(Canvas canvas) {
        paint.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, w, h, paint);

        String result;
        if (playerScore > rivalScore) result = "¡GANASTE!";
        else if (playerScore < rivalScore) result = "PERDISTE";
        else result = "EMPATE";

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(34f, h * 0.080f));
        canvas.drawText(result, w * 0.50f, h * 0.40f, paint);

        paint.setTextSize(Math.max(25f, h * 0.050f));
        canvas.drawText(playerScore + " - " + rivalScore, w * 0.50f, h * 0.50f, paint);

        paint.setColor(Color.rgb(35, 125, 255));
        canvas.drawRoundRect(restartRect, 20f, 20f, paint);
        paint.setColor(Color.rgb(70, 70, 70));
        canvas.drawRoundRect(menuRect, 20f, 20f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(15f, h * 0.028f));
        canvas.drawText("REINICIAR", restartRect.centerX(), restartRect.centerY() + h * 0.010f, paint);
        canvas.drawText("MENÚ", menuRect.centerX(), menuRect.centerY() + h * 0.010f, paint);
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

            if (state == ScreenState.MENU) {
                handleMenuTouch(x, y);
                return true;
            }

            if (state == ScreenState.GAME_OVER) {
                if (restartRect.contains(x, y)) {
                    restartMatch();
                } else if (menuRect.contains(x, y)) {
                    state = ScreenState.MENU;
                    invalidate();
                }
                return true;
            }

            if (distance(x, y, joystickCX, joystickCY) <= joystickRadius * 1.28f &&
                    joystickPointerId == -1) {
                joystickPointerId = pointerId;
                updateJoystick(x, y);
            } else if (distance(x, y, shootCX, shootCY) <= actionRadius * 1.32f) {
                playerShoot();
            } else if (distance(x, y, passCX, passCY) <= actionRadius * 1.32f) {
                playerPass();
            } else if (distance(x, y, forceCX, forceCY) <= actionRadius * 1.32f) {
                playerForce();
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (joystickPointerId != -1) {
                int index = event.findPointerIndex(joystickPointerId);
                if (index >= 0) {
                    updateJoystick(event.getX(index), event.getY(index));
                }
            }
        } else if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            int pointerId = event.getPointerId(actionIndex);
            if (pointerId == joystickPointerId || action == MotionEvent.ACTION_CANCEL) {
                joystickPointerId = -1;
                joystickDX = 0f;
                joystickDY = 0f;
            }
        }

        return true;
    }

    private void handleMenuTouch(float x, float y) {
        if (timeMinusRect.contains(x, y)) {
            configuredMatchSeconds = Math.max(30, configuredMatchSeconds - 30);
        } else if (timePlusRect.contains(x, y)) {
            configuredMatchSeconds = Math.min(300, configuredMatchSeconds + 30);
        } else if (extraRect.contains(x, y)) {
            extraTimeEnabled = !extraTimeEnabled;
        } else if (goldenRect.contains(x, y)) {
            goldenGoalEnabled = !goldenGoalEnabled;
        } else if (playersMinusRect.contains(x, y)) {
            configuredTeamSize = Math.max(1, configuredTeamSize - 1);
        } else if (playersPlusRect.contains(x, y)) {
            configuredTeamSize = Math.min(MAX_PLAYERS, configuredTeamSize + 1);
        } else if (startRect.contains(x, y)) {
            startMatch();
            return;
        }
        invalidate();
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
