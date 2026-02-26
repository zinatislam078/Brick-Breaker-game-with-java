package com.example.brickbreaker;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends Application {

    private double WIDTH;
    private double HEIGHT;

    // Paddle
    private double paddleX;
    private double paddleY;
    private double paddleWidth = 100;
    private double paddleHeight = 15;
    private double paddleSpeed = 6;

    // Ball
    private double ballX;
    private double ballY;
    private double ballRadius = 10;
    private double ballDX = 3;
    private double ballDY = -3;

    // Game state
    private boolean leftPressed = false;
    private boolean rightPressed = false;
    private boolean inMainMenu = true;
    private boolean paused = true;
    private boolean gameOver = false;
    private boolean gameWon = false;
    private int score = 0;
    private int lives = 3;
    private int level = 1;
    private final int maxLevel = 3;
    private boolean muted = false;

    // Bricks
    private int rows, cols;
    private boolean[][] bricks;
    private double brickWidth = 70;
    private double brickHeight = 20;
    private double brickRotationAngle = 0;

    private Random random = new Random();

    // Pause menu buttons
    private double btnWidth = 200;
    private double btnHeight = 40;
    private double resumeX, resumeY, mainMenuX, mainMenuY, quitX, quitY;
    private boolean hoverResume = false;
    private boolean hoverMainMenu = false;
    private boolean hoverQuit = false;

    // Gradient animation
    private double gradientOffset = 0;

    // Particle effects
    private class Particle {
        double x, y, dx, dy;
        Color color;
        double life = 1.0;

        Particle(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            this.dx = (random.nextDouble() - 0.5) * 4;
            this.dy = (random.nextDouble() - 0.5) * 4;
            this.color = color;
        }

        void update() {
            x += dx;
            y += dy;
            life -= 0.03;
            if (life < 0) life = 0;
        }

        boolean isAlive() { return life > 0; }
    }
    private final List<Particle> particles = new ArrayList<>();

    // Ball trail
    private class BallTrail {
        double x, y;
        double radius;
        Color color;
        double alpha = 1.0;

        BallTrail(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            this.radius = ballRadius + random.nextDouble() * 6;
            this.color = color;
        }

        void update() {
            alpha -= 0.05;
            if (alpha < 0) alpha = 0;
        }

        boolean isAlive() { return alpha > 0; }
    }
    private final List<BallTrail> ballTrail = new ArrayList<>();

    // Score popup particle
    private class ScoreParticle {
        double x, y;
        int value;
        double alpha = 1.0;

        ScoreParticle(double x, double y, int value) {
            this.x = x;
            this.y = y;
            this.value = value;
        }

        void update() {
            y -= 0.5;
            alpha -= 0.03;
            if (alpha < 0) alpha = 0;
        }

        boolean isAlive() { return alpha > 0; }
    }
    private final List<ScoreParticle> scoreParticles = new ArrayList<>();

    // Stars for background animation
    private class Star {
        double x, y;
        double radius;
        double speed;
        double brightness;

        Star(double x, double y, double radius, double speed) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.speed = speed;
            this.brightness = Math.random();
        }

        void update() {
            y += speed;
            if (y > HEIGHT) {
                y = 0;
                x = Math.random() * WIDTH;
            }
            brightness += (Math.random() - 0.5) * 0.05;
            if (brightness < 0) brightness = 0;
            if (brightness > 1) brightness = 1;
        }
    }
    private final List<Star> stars = new ArrayList<>();

    // MediaPlayers
    private MediaPlayer backgroundPlayer;
    private MediaPlayer bouncePlayer;
    private MediaPlayer winPlayer;
    private MediaPlayer gameOverPlayer;

    @Override
    public void start(Stage stage) {
        WIDTH = Screen.getPrimary().getBounds().getWidth();
        HEIGHT = Screen.getPrimary().getBounds().getHeight();

        paddleX = WIDTH / 2;
        paddleY = HEIGHT - 50;
        ballX = WIDTH / 2;
        ballY = HEIGHT - 100;

        resumeX = WIDTH / 2 - btnWidth / 2;
        resumeY = HEIGHT / 2 - 70;
        mainMenuX = WIDTH / 2 - btnWidth / 2;
        mainMenuY = HEIGHT / 2 - 10;
        quitX = WIDTH / 2 - btnWidth / 2;
        quitY = HEIGHT / 2 + 50;

        // Initialize stars
        for (int i = 0; i < 150; i++) {
            stars.add(new Star(Math.random() * WIDTH, Math.random() * HEIGHT, Math.random() * 2 + 1, Math.random() * 1 + 0.5));
        }

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Scene scene = new Scene(new StackPane(canvas));
        stage.setScene(scene);
        stage.setTitle("3D Brick Breaker - JavaFX");
        stage.setFullScreen(true);
        stage.show();

        // Initialize sound
        initSounds();

        initBricks();

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT) leftPressed = true;
            if (e.getCode() == KeyCode.RIGHT) rightPressed = true;
            if (e.getCode() == KeyCode.P && !inMainMenu && !gameOver && !gameWon) paused = !paused;
            if (e.getCode() == KeyCode.M) {
                muted = !muted;
                if (muted) backgroundPlayer.pause();
                else if (!inMainMenu) backgroundPlayer.play();
            }
            if (e.getCode() == KeyCode.ENTER) {
                if (inMainMenu) {
                    inMainMenu = false;
                    paused = false;
                    resetBall();
                    if (!muted) backgroundPlayer.play();
                } else if (gameOver || gameWon) {
                    resetGame();
                    if (!muted) backgroundPlayer.play();
                }
            }
            if (e.getCode() == KeyCode.ESCAPE) System.exit(0);
        });

        scene.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.LEFT) leftPressed = false;
            if (e.getCode() == KeyCode.RIGHT) rightPressed = false;
        });

        canvas.setOnMouseMoved(this::handleMouseMove);
        canvas.setOnMouseClicked(this::handleMouseClick);

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                gradientOffset += 0.003;
                if (!paused && !inMainMenu) update();
                draw(gc);
            }
        };
        timer.start();
    }

    private void initSounds() {
        try {
            backgroundPlayer = new MediaPlayer(new Media(new File("src/main/resources/Sounds/background.mp3").toURI().toString()));
            backgroundPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            bouncePlayer = new MediaPlayer(new Media(new File("src/main/resources/Sounds/bounce.mp3").toURI().toString()));
            winPlayer = new MediaPlayer(new Media(new File("src/main/resources/Sounds/win.mp3").toURI().toString()));
            gameOverPlayer = new MediaPlayer(new Media(new File("src/main/resources/Sounds/gameover.mp3").toURI().toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playBounce() { if (!muted) { bouncePlayer.stop(); bouncePlayer.play(); } }
    private void playWin() { if (!muted) { backgroundPlayer.stop(); winPlayer.stop(); winPlayer.play(); } }
    private void playGameOver() { if (!muted) { backgroundPlayer.stop(); gameOverPlayer.stop(); gameOverPlayer.play(); } }

    private void handleMouseMove(MouseEvent e) {
        double mx = e.getX();
        double my = e.getY();
        hoverResume = mx >= resumeX && mx <= resumeX + btnWidth && my >= resumeY && my <= resumeY + btnHeight;
        hoverMainMenu = mx >= mainMenuX && mx <= mainMenuX + btnWidth && my >= mainMenuY && my <= mainMenuY + btnHeight;
        hoverQuit = mx >= quitX && mx <= quitX + btnWidth && my >= quitY && my <= quitY + btnHeight;

        if (!inMainMenu && !gameOver && !gameWon && !paused) {
            double distance = mx - paddleX;
            paddleX += distance * 0.2;
            if (paddleX - paddleWidth / 2 < 0) paddleX = paddleWidth / 2;
            if (paddleX + paddleWidth / 2 > WIDTH) paddleX = WIDTH - paddleWidth / 2;
        }
    }

    private void handleMouseClick(MouseEvent e) {
        if (paused && !inMainMenu && !gameOver && !gameWon) {
            if (hoverResume) paused = false;
            else if (hoverMainMenu) resetGame();
            else if (hoverQuit) System.exit(0);
        }
    }

    private void initBricks() {
        switch (level) {
            case 1 -> setBirdShape();
            case 2 -> setCarShape();
            case 3 -> setGunShape();
            default -> setBirdShape();
        }
    }

    private void setBirdShape() {
        rows = 5; cols = 7;
        bricks = new boolean[rows][cols];
        bricks[2][0]=true; bricks[2][6]=true;
        bricks[1][1]=true; bricks[1][5]=true;
        bricks[0][2]=true; bricks[0][4]=true;
        for(int i=1;i<=3;i++) bricks[i][3]=true;
        bricks[4][2]=true; bricks[4][3]=true; bricks[4][4]=true;
    }

    private void setCarShape() {
        rows = 5; cols = 7;
        bricks = new boolean[rows][cols];
        bricks[4][1]=true; bricks[4][5]=true;
        for(int j=1;j<=5;j++) bricks[3][j]=true;
        for(int j=2;j<=4;j++) bricks[2][j]=true;
        bricks[1][3]=true; bricks[2][1]=true; bricks[2][5]=true;
    }

    private void setGunShape() {
        rows=7; cols=11;
        bricks=new boolean[rows][cols];
        for(int i=0;i<6;i++) bricks[i][5]=true;
        bricks[6][3]=true; bricks[6][4]=true; bricks[6][5]=true; bricks[6][6]=true; bricks[6][7]=true;
        bricks[4][3]=true; bricks[5][3]=true; bricks[5][4]=true;
        bricks[2][4]=true; bricks[3][4]=true; bricks[3][6]=true; bricks[2][6]=true;
    }

    private void update() {
        if(paused) return;

        if(leftPressed) paddleX -= paddleSpeed;
        if(rightPressed) paddleX += paddleSpeed;
        if(paddleX-paddleWidth/2<0) paddleX=paddleWidth/2;
        if(paddleX+paddleWidth/2>WIDTH) paddleX=WIDTH-paddleWidth/2;

        ballX += ballDX; ballY += ballDY;

        if(ballX-ballRadius<0 || ballX+ballRadius>WIDTH) ballDX*=-1;
        if(ballY-ballRadius<0) ballDY*=-1;

        // Paddle collision
        if(ballY+ballRadius>=paddleY && ballY+ballRadius<=paddleY+paddleHeight &&
                ballX>=paddleX-paddleWidth/2 && ballX<=paddleX+paddleWidth/2) {
            ballDY=-Math.abs(ballDY);
            double hitPos=(ballX-paddleX)/(paddleWidth/2);
            ballDX=hitPos*5;
            playBounce();
        }

        // Brick collision
        double startX=(WIDTH-(cols*brickWidth+(cols-1)*5))/2.0;
        double startY=50;
        outer:
        for(int i=0;i<rows;i++){
            for(int j=0;j<cols;j++){
                if(bricks[i][j]){
                    double bx=startX+j*(brickWidth+5);
                    double by=startY+i*(brickHeight+5);
                    if(ballX+ballRadius>bx && ballX-ballRadius<bx+brickWidth &&
                            ballY+ballRadius>by && ballY-ballRadius<by+brickHeight){
                        bricks[i][j]=false;
                        ballDY*=-1;
                        score+=10;
                        playBounce();

                        for(int k=0;k<15;k++) particles.add(new Particle(bx+brickWidth/2,by+brickHeight/2, Color.hsb(random.nextDouble()*360,1,1)));
                        scoreParticles.add(new ScoreParticle(bx+brickWidth/2, by+brickHeight/2,10));
                        break outer;
                    }
                }
            }
        }

        // Ball trail update
        ballTrail.add(new BallTrail(ballX,ballY,Color.hsb(random.nextDouble()*360,1,1)));
        ballTrail.removeIf(trail->{trail.update(); return !trail.isAlive();});

        // Particle update
        particles.removeIf(p->{p.update(); return !p.isAlive();});
        scoreParticles.removeIf(s->{s.update(); return !s.isAlive();});

        if(ballY-ballRadius>HEIGHT){
            lives--;
            if(lives<=0){ gameOver=true; playGameOver(); }
            else resetBall();
        }

        boolean allDestroyed=true;
        for(int i=0;i<rows && allDestroyed;i++)
            for(int j=0;j<cols && allDestroyed;j++)
                if(bricks[i][j]) allDestroyed=false;
        if(allDestroyed){
            level++;
            if(level>maxLevel){ gameWon=true; playWin(); }
            else { initBricks(); resetBall(); }
        }

        brickRotationAngle+=1;

        // Update stars
        for (Star s : stars) s.update();
    }

    private void draw(GraphicsContext gc){
        // Draw animated stars background
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        for (Star s : stars) {
            gc.setFill(Color.hsb(60, 0, s.brightness));
            gc.fillOval(s.x, s.y, s.radius, s.radius);
        }

        if(inMainMenu){
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(40));
            gc.fillText("3D Brick Breaker", WIDTH/2-180, HEIGHT/2-40);
            gc.setFont(Font.font(20));
            gc.fillText("Press ENTER to Start | M to Mute", WIDTH/2-130, HEIGHT/2+10);
            return;
        }

        if(gameOver){
            gc.setFill(Color.RED);
            gc.setFont(Font.font(40));
            gc.fillText("GAME OVER", WIDTH/2-120, HEIGHT/2-20);
            gc.setFont(Font.font(20));
            gc.fillText("Press ENTER to Restart", WIDTH/2-120, HEIGHT/2+20);
            return;
        }

        if(gameWon){
            gc.setFill(Color.LIME);
            gc.setFont(Font.font(40));
            gc.fillText("YOU WIN!", WIDTH/2-100, HEIGHT/2-20);
            gc.setFont(Font.font(20));
            gc.fillText("Press ENTER to Restart", WIDTH/2-120, HEIGHT/2+20);
            return;
        }

        drawBricks(gc);

        // Ball trail
        for(BallTrail trail: ballTrail){
            gc.setGlobalAlpha(trail.alpha);
            gc.setFill(trail.color);
            gc.fillOval(trail.x-trail.radius/2, trail.y-trail.radius/2, trail.radius, trail.radius);
        }
        gc.setGlobalAlpha(1.0);

        // Paddle glow
        DropShadow ds = new DropShadow(20, Color.CYAN);
        gc.applyEffect(ds);
        gc.setFill(Color.BLUE);
        gc.fillRect(paddleX-paddleWidth/2,paddleY,paddleWidth,paddleHeight);
        gc.applyEffect(null);

        // Ball
        gc.setFill(Color.ORANGE);
        gc.fillOval(ballX-ballRadius, ballY-ballRadius, ballRadius*2, ballRadius*2);

        // Particle effects
        for(Particle p: particles){
            gc.setGlobalAlpha(p.life);
            gc.setFill(p.color);
            gc.fillOval(p.x,p.y,5,5);
        }
        gc.setGlobalAlpha(1.0);

        // Score popups
        for(ScoreParticle s: scoreParticles){
            gc.setGlobalAlpha(s.alpha);
            gc.setFill(Color.YELLOW);
            gc.setFont(Font.font(14));
            gc.fillText("+"+s.value, s.x, s.y);
        }
        gc.setGlobalAlpha(1.0);

        // HUD
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(16));
        gc.fillText("Score: "+score+" | Lives: "+lives+" | Level: "+level+" | "+(muted?"Muted":"Sound On"), 10,20);

        if(paused) drawPauseMenu(gc);
    }

    private void drawBricks(GraphicsContext gc){
        double startX=(WIDTH-(cols*brickWidth+(cols-1)*5))/2.0;
        double startY=50;
        for(int i=0;i<rows;i++){
            for(int j=0;j<cols;j++){
                if(bricks[i][j]){
                    double bx=startX+j*(brickWidth+5);
                    double by=startY+i*(brickHeight+5);
                    Color color = Color.hsb((brickRotationAngle+j*30+i*20)%360,1,1);
                    LinearGradient lg = new LinearGradient(0,by,0,by+brickHeight,false,CycleMethod.NO_CYCLE,new Stop[]{
                            new Stop(0,color.brighter()), new Stop(1,color.darker())
                    });
                    gc.setFill(lg);
                    gc.fillRect(bx,by,brickWidth,brickHeight);
                }
            }
        }
    }

    private void drawPauseMenu(GraphicsContext gc){
        Stop[] stops = new Stop[]{
                new Stop((0+gradientOffset)%1, Color.rgb(0,0,0,0.6)),
                new Stop((0.5+gradientOffset)%1, Color.rgb(50,50,100,0.6)),
                new Stop((1+gradientOffset)%1, Color.rgb(0,0,0,0.6))
        };
        LinearGradient lg = new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,stops);
        gc.setFill(lg);
        gc.fillRect(0,0,WIDTH,HEIGHT);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(30));
        gc.fillText("PAUSED", WIDTH/2-60, HEIGHT/2-120);

        gc.setFill(hoverResume?Color.LIGHTGREEN:Color.GRAY);
        gc.fillRect(resumeX,resumeY,btnWidth,btnHeight);

        gc.setFill(hoverMainMenu?Color.LIGHTBLUE:Color.GRAY);
        gc.fillRect(mainMenuX,mainMenuY,btnWidth,btnHeight);

        gc.setFill(hoverQuit?Color.SALMON:Color.GRAY);
        gc.fillRect(quitX,quitY,btnWidth,btnHeight);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(20));
        gc.fillText("Resume", resumeX+60, resumeY+25);
        gc.fillText("Main Menu", mainMenuX+45, mainMenuY+25);
        gc.fillText("Quit", quitX+80, quitY+25);
    }

    private void resetBall(){
        ballX=WIDTH/2;
        ballY=HEIGHT-100;
        ballDX=3*(random.nextBoolean()?1:-1);
        ballDY=-3;
    }

    private void resetGame(){
        lives=3; score=0; level=1; paddleWidth=100;
        gameOver=false; gameWon=false; inMainMenu=true; paused=true;
        particles.clear(); ballTrail.clear(); scoreParticles.clear();
        backgroundPlayer.stop();
        initBricks();
        resetBall();
    }

    public static void main(String[] args){
        launch();
    }
}
