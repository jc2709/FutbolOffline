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

    private float keeperLeftY, keeperRightY;
    private float ballX, ballY, ballVX, ballVY;
    private int ballOwner = FREE_BALL;
    private int controlledIndex = 0;
    private int pendingPassTarget = -1;

    private float joystickCX, joystickCY, joystickRadius;
    private float joystickDX, joystickDY;
    private float shootCX, shootCY, actionRadius;
    private float passCX, passCY;
    private int joystickPointerId = -1;

    private ScreenState state = ScreenState.MENU;

    private int configuredMatchSeconds = 90;
    private boolean extraTimeEnabled = false;
    private boolean goldenGoalEnabled = false;
    private int configuredTeamSize = 1;

    private int teamSize = 1;
    private int playerScore = 0;
    private int rivalScore = 0;
    private float matchTime = 90f;
    private boolean inExtraTime = false;
    private boolean goldenGoalPhase = false;
    private String phaseLabel = "TIEMPO";
    private float rivalKickCooldown = 0f;
    private float possessionGrace = 0f;
    private float collisionCooldown = 0f;
    private long lastFrameNanos = 0L;

    private final RectF timeMinusRect = new RectF();
    private final RectF timePlusRect = new RectF();
    private final RectF playersMinusRect = new RectF();
    private final RectF playersPlusRect = new RectF();
    private final RectF extraRect = new RectF();
    private final RectF goldenRect = new RectF();
    private final RectF startRect = new RectF();
    private final RectF restartRect = new RectF();
    private final RectF menuRect = new RectF();

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

        actionRadius = Math.min(w, h) * 0.066f;
        shootCX = w * 0.88f;
        shootCY = h * 0.79f;
        passCX = w * 0.75f;
        passCY = h * 0.86f;

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
        possessionGrace = 0f;
        collisionCooldown = 0f;
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
        possessionGrace = Math.max(0f, possessionGrace - dt);
        collisionCooldown = Math.max(0f, collisionCooldown - dt);

        updateClock(dt);
        if (state != ScreenState.PLAYING) return;

        updateControlledPlayer(dt);
        updateTeammates(dt);
        updateRivals(dt);
        resolveOpposingPlayerCollisions();
        updateKeepers(dt);

        if (ballOwner != FREE_BALL) {
            attachBallToOwner();
        } else {
            updateFreeBall(dt);
            tryAcquirePossession();
        }

        rivalWithBallBehavior();
        checkGoals();
    }

    private void updateClock(float dt) {
        if (goldenGoalPhase) return;

        matchTime -= dt;
        if (matchTime > 0f) return;
        matchTime = 0f;

        if (!inExtraTime && playerScore == rivalScore && extraTimeEnabled) {
            inExtraTime = true;
            phaseLabel = goldenGoalEnabled ? "PRÓRROGA · GOL DE ORO" : "PRÓRROGA";
            matchTime = Math.max(30f, configuredMatchSeconds / 3f);
            if (goldenGoalEnabled) goldenGoalPhase = true;
            return;
        }

        if (!inExtraTime && playerScore == rivalScore && goldenGoalEnabled) {
            goldenGoalPhase = true;
            phaseLabel = "GOL DE ORO";
            return;
        }

        state = ScreenState.GAME_OVER;
    }

    private float basePlayerSpeed() {
        return Math.min(w, h) * 0.40f;
    }

    private float speedForOwner(int ownerId) {
        return basePlayerSpeed() * (ballOwner == ownerId ? 0.78f : 1f);
    }

    private void updateControlledPlayer(float dt) {
        int i = controlledIndex;
        float input = length(joystickDX, joystickDY);
        float speed = speedForOwner(i);

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

            if (ballOwner == FREE_BALL && pendingPassTarget == i) {
                float leadTime = 0.18f;
                targetX = ballX + ballVX * leadTime;
                targetY = ballY + ballVY * leadTime;
            } else if (ballOwner == FREE_BALL && isClosestTeamToBall(i)) {
                targetX = ballX;
                targetY = ballY;
            } else {
                float[] formation = teamFormationTarget(i);
                targetX = formation[0];
                targetY = formation[1];
            }

            moveTeamPlayerToward(i, targetX, targetY, speedForOwner(i) * dt);
        }
    }

    private void updateRivals(float dt) {
        int chaser = findClosestRivalToBallOrOwner();

        for (int i = 0; i < teamSize; i++) {
            float targetX;
            float targetY;
            int ownerId = RIVAL_OWNER_OFFSET + i;

            if (ballOwner == ownerId) {
                targetX = fieldLeft + playerRadius * 2.8f;
                targetY = clamp(h * 0.50f + (rivalY[i] - h * 0.50f) * 0.28f,
                        goalTop + playerRadius, goalBottom - playerRadius);
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

            moveRivalToward(i, targetX, targetY, speedForOwner(ownerId) * dt);
        }
    }

    private void resolveOpposingPlayerCollisions() {
        float minDist = playerRadius * 2f;
        for (int t = 0; t < teamSize; t++) {
            for (int r = 0; r < teamSize; r++) {
                float dx = rivalX[r] - teamX[t];
                float dy = rivalY[r] - teamY[t];
                float d = length(dx, dy);
                if (d <= 0.001f || d >= minDist) continue;

                float nx = dx / d;
                float ny = dy / d;
                float overlap = minDist - d;

                teamX[t] -= nx * overlap * 0.50f;
                teamY[t] -= ny * overlap * 0.50f;
                rivalX[r] += nx * overlap * 0.50f;
                rivalY[r] += ny * overlap * 0.50f;
                clampTeamPlayer(t);
                clampRival(r);

                if (collisionCooldown > 0f || possessionGrace > 0f) continue;

                if (ballOwner == t && d < playerRadius * 1.68f) {
                    loseBallFromCollision(false, t, r, -nx, -ny);
                    return;
                }
                if (ballOwner == RIVAL_OWNER_OFFSET + r && d < playerRadius * 1.68f) {
                    loseBallFromCollision(true, t, r, nx, ny);
                    return;
                }
            }
        }
    }

    private void loseBallFromCollision(boolean rivalWasOwner, int teamIndex, int rivalIndex, float awayX, float awayY) {
        float d = length(awayX, awayY);
        if (d < 0.001f) {
            awayX = rivalWasOwner ? 1f : -1f;
            awayY = 0f;
            d = 1f;
        }
        awayX /= d;
        awayY /= d;

        ballOwner = FREE_BALL;
        pendingPassTarget = -1;
        float sourceX = rivalWasOwner ? rivalX[rivalIndex] : teamX[teamIndex];
        float sourceY = rivalWasOwner ? rivalY[rivalIndex] : teamY[teamIndex];
        ballX = sourceX + awayX * (playerRadius + ballRadius + 2f);
        ballY = sourceY + awayY * (playerRadius + ballRadius + 2f);
        float looseSpeed = Math.min(w, h) * 0.32f;
        ballVX = awayX * looseSpeed;
        ballVY = awayY * looseSpeed;
        collisionCooldown = 0.48f;
        resolveBallBounds();
    }

    private void updateKeepers(float dt) {
        float keeperSpeed = basePlayerSpeed() * 0.90f;
        keeperLeftY = moveToward(keeperLeftY, ballY, keeperSpeed * dt);
        keeperRightY = moveToward(keeperRightY, ballY, keeperSpeed * dt);
        keeperLeftY = clamp(keeperLeftY, goalTop + playerRadius, goalBottom - playerRadius);
        keeperRightY = clamp(keeperRightY, goalTop + playerRadius, goalBottom - playerRadius);
    }

    private void updateFreeBall(float dt) {
        ballX += ballVX * dt;
        ballY += ballVY * dt;

        float friction = (float) Math.pow(0.28, dt);
        ballVX *= friction;
        ballVY *= friction;

        resolveBallBounds();
        escapeCornerIfNeeded();
        resolveKeeperCollision(fieldLeft + playerRadius * 0.55f, keeperLeftY, true);
        resolveKeeperCollision(fieldRight - playerRadius * 0.55f, keeperRightY, false);
        resolveBallBounds();
    }

    private void tryAcquirePossession() {
        float capture = playerRadius + ballRadius + playerRadius * 0.30f;
        float speed = length(ballVX, ballVY);
        float maxCaptureSpeed = Math.min(w, h) * 1.25f;

        if (pendingPassTarget >= 0 && pendingPassTarget < teamSize) {
            float d = distance(teamX[pendingPassTarget], teamY[pendingPassTarget], ballX, ballY);
            if (d < capture * 1.65f && speed < maxCaptureSpeed) {
                giveTeamPossession(pendingPassTarget);
                pendingPassTarget = -1;
                return;
            }
        }

        int bestTeam = -1;
        float bestTeamDist = Float.MAX_VALUE;
        for (int i = 0; i < teamSize; i++) {
            float d = distance(teamX[i], teamY[i], ballX, ballY);
            if (d < bestTeamDist) {
                bestTeamDist = d;
                bestTeam = i;
            }
        }

        int bestRival = -1;
        float bestRivalDist = Float.MAX_VALUE;
        for (int i = 0; i < teamSize; i++) {
            float d = distance(rivalX[i], rivalY[i], ballX, ballY);
            if (d < bestRivalDist) {
                bestRivalDist = d;
                bestRival = i;
            }
        }

        if (speed > maxCaptureSpeed) return;

        if (bestTeam >= 0 && bestTeamDist < capture &&
                (bestRival < 0 || bestTeamDist <= bestRivalDist)) {
            giveTeamPossession(bestTeam);
        } else if (bestRival >= 0 && bestRivalDist < capture) {
            giveRivalPossession(bestRival);
        }
    }

    private void giveTeamPossession(int index) {
        ballOwner = index;
        controlledIndex = index;
        pendingPassTarget = -1;
        ballVX = 0f;
        ballVY = 0f;
        possessionGrace = 0.24f;
        attachBallToOwner();
    }

    private void giveRivalPossession(int index) {
        ballOwner = RIVAL_OWNER_OFFSET + index;
        pendingPassTarget = -1;
        ballVX = 0f;
        ballVY = 0f;
        possessionGrace = 0.24f;
        rivalFacingX[index] = -1f;
        rivalFacingY[index] = 0f;
        attachBallToOwner();
    }

    private void attachBallToOwner() {
        float carryGap = playerRadius + ballRadius + 1f;

        if (ballOwner >= 0 && ballOwner < RIVAL_OWNER_OFFSET) {
            int i = ballOwner;
            float fx = teamFacingX[i];
            float fy = teamFacingY[i];
            if (length(fx, fy) < 0.1f) { fx = 1f; fy = 0f; }
            ballX = teamX[i] + fx * carryGap;
            ballY = teamY[i] + fy * carryGap;
        } else if (ballOwner >= RIVAL_OWNER_OFFSET) {
            int i = ballOwner - RIVAL_OWNER_OFFSET;
            if (i < 0 || i >= teamSize) return;
            float fx = rivalFacingX[i];
            float fy = rivalFacingY[i];
            if (length(fx, fy) < 0.1f) { fx = -1f; fy = 0f; }
            ballX = rivalX[i] + fx * carryGap;
            ballY = rivalY[i] + fy * carryGap;
        }
        ballVX = 0f;
        ballVY = 0f;
        constrainAttachedBall();
    }

    private void constrainAttachedBall() {
        ballY = clamp(ballY, fieldTop + ballRadius, fieldBottom - ballRadius);
        boolean inGoalMouth = ballY >= goalTop && ballY <= goalBottom;
        if (!inGoalMouth) {
            ballX = clamp(ballX, fieldLeft + ballRadius, fieldRight - ballRadius);
        }
    }

    private void playerShootOrClear() {
        if (state != ScreenState.PLAYING) return;

        boolean owns = ballOwner == controlledIndex;
        boolean looseAndClose = ballOwner == FREE_BALL &&
                distance(teamX[controlledIndex], teamY[controlledIndex], ballX, ballY) <
                        playerRadius + ballRadius + playerRadius * 0.75f;
        if (!owns && !looseAndClose) return;

        float midX = (fieldLeft + fieldRight) * 0.50f;
        boolean inRivalHalf = teamX[controlledIndex] >= midX;

        if (inRivalHalf) {
            float targetY = clamp(h * 0.50f + teamFacingY[controlledIndex] * h * 0.10f,
                    goalTop + ballRadius * 2f, goalBottom - ballRadius * 2f);
            kickBall(fieldRight + w * 0.07f, targetY, 1.25f);
        } else {
            float targetY = clamp(teamY[controlledIndex] + teamFacingY[controlledIndex] * h * 0.18f,
                    fieldTop + playerRadius * 2f, fieldBottom - playerRadius * 2f);
            kickBall(fieldRight - w * 0.12f, targetY, 1.08f);
        }
    }

    private void playerPass() {
        if (state != ScreenState.PLAYING || ballOwner != controlledIndex || teamSize <= 1) return;

        int target = choosePassTarget();
        if (target < 0) return;

        pendingPassTarget = target;
        float dx = teamX[target] - ballX;
        float dy = teamY[target] - ballY;
        float d = Math.max(1f, length(dx, dy));

        float scale = Math.min(w, h);
        float speed = clamp(d * 2.35f, scale * 0.62f, scale * 1.32f);

        float tx = teamX[target] + teamFacingX[target] * playerRadius * 0.75f;
        float ty = teamY[target] + teamFacingY[target] * playerRadius * 0.75f;
        releaseBallToward(tx, ty, speed);
    }

    private int choosePassTarget() {
        if (teamSize <= 1) return -1;
        int from = controlledIndex;
        float fx = teamFacingX[from];
        float fy = teamFacingY[from];
        float facingLen = length(fx, fy);
        if (facingLen < 0.1f) { fx = 1f; fy = 0f; facingLen = 1f; }
        fx /= facingLen;
        fy /= facingLen;

        int best = -1;
        float bestScore = -Float.MAX_VALUE;
        float fieldSpan = Math.max(1f, fieldRight - fieldLeft);

        for (int i = 0; i < teamSize; i++) {
            if (i == from) continue;
            float dx = teamX[i] - teamX[from];
            float dy = teamY[i] - teamY[from];
            float d = Math.max(1f, length(dx, dy));
            float dot = (dx / d) * fx + (dy / d) * fy;
            float forwardBonus = dx / fieldSpan;
            float distancePenalty = d / fieldSpan;
            float score = dot * 1.8f + forwardBonus * 1.25f - distancePenalty * 0.55f;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private void rivalWithBallBehavior() {
        if (ballOwner < RIVAL_OWNER_OFFSET || rivalKickCooldown > 0f) return;
        int i = ballOwner - RIVAL_OWNER_OFFSET;
        if (i < 0 || i >= teamSize) return;

        float midX = (fieldLeft + fieldRight) * 0.50f;
        boolean inOurHalf = rivalX[i] <= midX;
        float distanceToGoal = rivalX[i] - fieldLeft;

        if (inOurHalf && distanceToGoal < (fieldRight - fieldLeft) * 0.34f) {
            float targetY = clamp(h * 0.50f + (rivalY[i] - h * 0.50f) * 0.18f,
                    goalTop + ballRadius * 2f, goalBottom - ballRadius * 2f);
            kickBall(fieldLeft - w * 0.07f, targetY, 1.15f);
            rivalKickCooldown = 0.78f;
        }
    }

    private void kickBall(float tx, float ty, float power) {
        float speed = Math.min(w, h) * 1.08f * power;
        releaseBallToward(tx, ty, speed);
    }

    private void releaseBallToward(float tx, float ty, float speed) {
        float dx = tx - ballX;
        float dy = ty - ballY;
        float d = length(dx, dy);
        if (d < 0.001f) { dx = 1f; dy = 0f; d = 1f; }
        ballOwner = FREE_BALL;
        ballVX = dx / d * speed;
        ballVY = dy / d * speed;
        possessionGrace = 0f;
    }

    private void checkGoals() {
        if (ballOwner != FREE_BALL) return;

        if (ballX + ballRadius < fieldLeft && ballY >= goalTop && ballY <= goalBottom) {
            rivalScore++;
            if (goldenGoalPhase) {
                state = ScreenState.GAME_OVER;
            } else {
                resetPositions();
            }
            return;
        }

        if (ballX - ballRadius > fieldRight && ballY >= goalTop && ballY <= goalBottom) {
            playerScore++;
            if (goldenGoalPhase) {
                state = ScreenState.GAME_OVER;
            } else {
                resetPositions();
            }
        }
    }

    private void resolveKeeperCollision(float kx, float ky, boolean leftKeeper) {
        float dx = ballX - kx;
        float dy = ballY - ky;
        float d = length(dx, dy);
        float keeperRadius = playerRadius * 0.90f;
        float minDist = keeperRadius + ballRadius;
        if (d > 0f && d < minDist) {
            float nx = dx / d;
            float ny = dy / d;
            ballX = kx + nx * minDist;
            ballY = ky + ny * minDist;
            float speed = Math.min(w, h) * 0.78f;
            ballVX = (leftKeeper ? Math.abs(nx) : -Math.abs(nx)) * speed;
            ballVY = ny * speed * 0.60f;
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
        if (inCorner && speed < Math.min(w, h) * 0.13f) {
            float escapeSpeed = Math.min(w, h) * 0.25f;
            ballVX = (nearLeft ? 1f : -1f) * escapeSpeed;
            ballVY = (nearTop ? 1f : -1f) * escapeSpeed * 0.72f;
            float inset = ballRadius * 0.25f;
            ballX += nearLeft ? inset : -inset;
            ballY += nearTop ? inset : -inset;
        }
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
            float d = distance(rivalX[i], rivalY[i], x, y);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private boolean isClosestTeamToBall(int index) {
        float d = distance(teamX[index], teamY[index], ballX, ballY);
        for (int i = 0; i < teamSize; i++) {
            if (i == index) continue;
            if (distance(teamX[i], teamY[i], ballX, ballY) < d) return false;
        }
        return true;
    }

    private float[] teamFormationTarget(int i) {
        float[] lanes = {0.50f, 0.27f, 0.73f, 0.40f, 0.60f};
        float[] xs = {0.34f, 0.41f, 0.41f, 0.50f, 0.50f};
        float ballShift = clamp((ballX - w * 0.50f) * 0.18f, -w * 0.06f, w * 0.08f);
        return new float[]{w * xs[i] + ballShift, h * lanes[i]};
    }

    private float[] rivalFormationTarget(int i) {
        float[] lanes = {0.50f, 0.27f, 0.73f, 0.40f, 0.60f};
        float[] xs = {0.66f, 0.59f, 0.59f, 0.50f, 0.50f};
        float ballShift = clamp((ballX - w * 0.50f) * 0.18f, -w * 0.08f, w * 0.06f);
        return new float[]{w * xs[i] + ballShift, h * lanes[i]};
    }

    private void moveTeamPlayerToward(int i, float tx, float ty, float maxDelta) {
        float dx = tx - teamX[i];
        float dy = ty - teamY[i];
        float d = length(dx, dy);
        if (d <= 0.5f) return;
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
        if (d <= 0.5f) {
            if (ballOwner == RIVAL_OWNER_OFFSET + i) {
                rivalFacingX[i] = -1f;
                rivalFacingY[i] = 0f;
            }
            return;
        }
        float step = Math.min(maxDelta, d);
        float nx = dx / d;
        float ny = dy / d;
        rivalX[i] += nx * step;
        rivalY[i] += ny * step;
        rivalFacingX[i] = nx;
        rivalFacingY[i] = ny;
        clampRival(i);
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
            canvas.drawRect(fieldLeft + i * stripe, fieldTop, fieldLeft + (i + 1) * stripe, fieldBottom, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, h * 0.005f));
        paint.setColor(Color.WHITE);
        canvas.drawRect(fieldLeft, fieldTop, fieldRight, fieldBottom, paint);
        canvas.drawLine(w * 0.50f, fieldTop, w * 0.50f, fieldBottom, paint);
        canvas.drawCircle(w * 0.50f, h * 0.50f, Math.min(w, h) * 0.105f, paint);

        float areaW = (fieldRight - fieldLeft) * 0.16f;
        canvas.drawRect(fieldLeft, h * 0.29f, fieldLeft + areaW, h * 0.71f, paint);
        canvas.drawRect(fieldRight - areaW, h * 0.29f, fieldRight, h * 0.71f, paint);

        float goalDepth = w * 0.035f;
        canvas.drawRect(fieldLeft - goalDepth, goalTop, fieldLeft, goalBottom, paint);
        canvas.drawRect(fieldRight, goalTop, fieldRight + goalDepth, goalBottom, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(w * 0.50f, h * 0.50f, 5f, paint);
    }

    private void drawEntities(Canvas canvas) {
        for (int i = 0; i < teamSize; i++) {
            if (i == controlledIndex) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(3f, playerRadius * 0.16f));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(teamX[i], teamY[i], playerRadius * 1.23f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(Color.rgb(35, 110, 255));
            canvas.drawCircle(teamX[i], teamY[i], playerRadius, paint);
            drawNumber(canvas, String.valueOf(i + 1), teamX[i], teamY[i]);
        }

        for (int i = 0; i < teamSize; i++) {
            paint.setColor(Color.rgb(235, 65, 65));
            canvas.drawCircle(rivalX[i], rivalY[i], playerRadius, paint);
            drawNumber(canvas, String.valueOf(i + 1), rivalX[i], rivalY[i]);
        }

        float keeperRadius = playerRadius * 0.90f;
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
    }

    private void drawNumber(Canvas canvas, String number, float x, float y) {
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(playerRadius * 0.85f);
        canvas.drawText(number, x, y + playerRadius * 0.30f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawHud(Canvas canvas) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(24f, h * 0.052f));
        canvas.drawText(playerScore + "  -  " + rivalScore, w * 0.50f, h * 0.06f, paint);

        paint.setTextSize(Math.max(16f, h * 0.030f));
        String timeText = goldenGoalPhase ? "∞" : ((int) Math.ceil(matchTime)) + " s";
        canvas.drawText(phaseLabel + " · " + timeText, w * 0.50f, h * 0.105f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawControls(Canvas canvas) {
        paint.setColor(Color.argb(110, 255, 255, 255));
        canvas.drawCircle(joystickCX, joystickCY, joystickRadius, paint);
        paint.setColor(Color.argb(190, 255, 255, 255));
        canvas.drawCircle(
                joystickCX + joystickDX * joystickRadius * 0.55f,
                joystickCY + joystickDY * joystickRadius * 0.55f,
                joystickRadius * 0.40f,
                paint
        );

        boolean inRivalHalf = teamX[controlledIndex] >= (fieldLeft + fieldRight) * 0.50f;
        String shootText = inRivalHalf ? "TIRO" : "DESPEJE";
        drawActionButton(canvas, shootCX, shootCY, shootText, Color.rgb(235, 70, 65), 1f);
        drawActionButton(canvas, passCX, passCY, "PASE", Color.rgb(55, 125, 245),
                teamSize > 1 ? 1f : 0.42f);
    }

    private void drawActionButton(Canvas canvas, float cx, float cy, String text, int color, float alphaFactor) {
        int alpha = (int) (215 * alphaFactor);
        paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, actionRadius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(14f, actionRadius * 0.34f));
        canvas.drawText(text, cx, cy + actionRadius * 0.13f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawMenu(Canvas canvas) {
        paint.setColor(Color.rgb(18, 76, 36));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(34f, h * 0.075f));
        canvas.drawText("FÚTBOL OFFLINE", w * 0.50f, h * 0.16f, paint);
        paint.setFakeBoldText(false);

        drawSettingRow(canvas, "TIEMPO", configuredMatchSeconds + " s", timeMinusRect, timePlusRect, h * 0.30f);
        drawToggleRow(canvas, "PRÓRROGA", extraTimeEnabled, extraRect);
        drawToggleRow(canvas, "GOL DE ORO", goldenGoalEnabled, goldenRect);
        drawSettingRow(canvas, "JUGADORES", configuredTeamSize + " vs " + configuredTeamSize,
                playersMinusRect, playersPlusRect, h * 0.64f);

        paint.setColor(Color.rgb(35, 125, 255));
        canvas.drawRoundRect(startRect, 24f, 24f, paint);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(20f, h * 0.042f));
        canvas.drawText("JUGAR", startRect.centerX(), startRect.centerY() + h * 0.014f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawSettingRow(Canvas canvas, String label, String value, RectF minus, RectF plus, float y) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(18f, h * 0.035f));
        canvas.drawText(label + "   " + value, w * 0.50f, y - h * 0.018f, paint);
        drawSmallButton(canvas, minus, "−");
        drawSmallButton(canvas, plus, "+");
    }

    private void drawToggleRow(Canvas canvas, String label, boolean enabled, RectF rect) {
        paint.setColor(enabled ? Color.rgb(35, 145, 75) : Color.rgb(75, 85, 85));
        canvas.drawRoundRect(rect, 20f, 20f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(18f, h * 0.034f));
        canvas.drawText(label + "   " + (enabled ? "SÍ" : "NO"), rect.centerX(), rect.centerY() + h * 0.011f, paint);
    }

    private void drawSmallButton(Canvas canvas, RectF rect, String text) {
        paint.setColor(Color.rgb(55, 105, 175));
        canvas.drawRoundRect(rect, 16f, 16f, paint);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(22f, h * 0.045f));
        canvas.drawText(text, rect.centerX(), rect.centerY() + h * 0.014f, paint);
        paint.setFakeBoldText(false);
    }

    private void drawGameOver(Canvas canvas) {
        paint.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, w, h, paint);

        String result = playerScore > rivalScore ? "¡GANASTE!" : (playerScore < rivalScore ? "PERDISTE" : "EMPATE");
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(36f, h * 0.085f));
        canvas.drawText(result, w * 0.50f, h * 0.43f, paint);
        paint.setTextSize(Math.max(25f, h * 0.050f));
        canvas.drawText(playerScore + " - " + rivalScore, w * 0.50f, h * 0.52f, paint);

        paint.setColor(Color.rgb(35, 125, 255));
        canvas.drawRoundRect(restartRect, 20f, 20f, paint);
        paint.setColor(Color.rgb(70, 95, 105));
        canvas.drawRoundRect(menuRect, 20f, 20f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(16f, h * 0.032f));
        canvas.drawText("REINICIAR", restartRect.centerX(), restartRect.centerY() + h * 0.011f, paint);
        canvas.drawText("MENÚ", menuRect.centerX(), menuRect.centerY() + h * 0.011f, paint);
        paint.setFakeBoldText(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if (state == ScreenState.MENU) {
            if (action == MotionEvent.ACTION_DOWN) {
                handleMenuTap(event.getX(actionIndex), event.getY(actionIndex));
            }
            return true;
        }

        if (state == ScreenState.GAME_OVER) {
            if (action == MotionEvent.ACTION_DOWN) {
                float x = event.getX(actionIndex);
                float y = event.getY(actionIndex);
                if (restartRect.contains(x, y)) restartMatch();
                else if (menuRect.contains(x, y)) { state = ScreenState.MENU; invalidate(); }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = event.getX(actionIndex);
            float y = event.getY(actionIndex);
            int pointerId = event.getPointerId(actionIndex);

            if (distance(x, y, joystickCX, joystickCY) <= joystickRadius * 1.28f && joystickPointerId == -1) {
                joystickPointerId = pointerId;
                updateJoystick(x, y);
            } else if (distance(x, y, shootCX, shootCY) <= actionRadius * 1.32f) {
                playerShootOrClear();
            } else if (distance(x, y, passCX, passCY) <= actionRadius * 1.32f) {
                playerPass();
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

    private void handleMenuTap(float x, float y) {
        if (timeMinusRect.contains(x, y)) configuredMatchSeconds = Math.max(30, configuredMatchSeconds - 30);
        else if (timePlusRect.contains(x, y)) configuredMatchSeconds = Math.min(300, configuredMatchSeconds + 30);
        else if (playersMinusRect.contains(x, y)) configuredTeamSize = Math.max(1, configuredTeamSize - 1);
        else if (playersPlusRect.contains(x, y)) configuredTeamSize = Math.min(MAX_PLAYERS, configuredTeamSize + 1);
        else if (extraRect.contains(x, y)) extraTimeEnabled = !extraTimeEnabled;
        else if (goldenRect.contains(x, y)) goldenGoalEnabled = !goldenGoalEnabled;
        else if (startRect.contains(x, y)) { startMatch(); return; }
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
        joystickDX = dx / d * magnitude;
        joystickDY = dy / d * magnitude;
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
