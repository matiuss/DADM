package co.edu.unal.tictactoe;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AndroidTicTacToeActivity extends AppCompatActivity {

    static final int DIALOG_DIFFICULTY_ID = 0;
    static final int DIALOG_QUIT_ID = 1;
    static final int DIALOG_ABOUT_ID = 2;

    // Internal game representation
    private TicTacToeGame mGame;

    // Board View
    private BoardView mBoardView;

    // Text view for game state status
    private TextView mInfoTextView;

    // Score counters
    private int mHumanWins = 0;
    private int mAndroidWins = 0;
    private int mTies = 0;

    // Score TextViews
    private TextView mHumanScoreTextView;
    private TextView mAndroidScoreTextView;
    private TextView mTieScoreTextView;

    // Game state check flag
    private boolean mGameOver = false;
    private boolean mComputerTurn = false;

    // Sounds
    MediaPlayer mHumanMediaPlayer;
    MediaPlayer mComputerMediaPlayer;
    
    // New game button
    private android.widget.Button mNewGameButton;

    // Listen for touches on the board
    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            // Determine which cell was touched
            int col = (int) event.getX() / mBoardView.getBoardCellWidth();
            int row = (int) event.getY() / mBoardView.getBoardCellHeight();
            int pos = row * 3 + col;

            if (!mGameOver && !mComputerTurn && mGame.getBoardOccupant(pos) == TicTacToeGame.OPEN_SPOT) {
                setMove(TicTacToeGame.HUMAN_PLAYER, pos);

                // Check for winner
                int winner = mGame.checkForWinner();
                if (winner == 0) {
                    mInfoTextView.setText(R.string.turn_computer);
                    mComputerTurn = true;
                    
                    // Extra Challenge: Delay computer move
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            if (!mGameOver) {
                                int move = mGame.getComputerMove();
                                setMove(TicTacToeGame.COMPUTER_PLAYER, move);
                                int winner = mGame.checkForWinner();
                                handleWinner(winner);
                                mComputerTurn = false;
                            }
                        }
                    }, 1000);
                } else {
                    handleWinner(winner);
                }
            }

            // So we aren't notified of continued events when finger is moved
            return false;
        }
    };

    private void handleWinner(int winner) {
        if (winner == 0) {
            mInfoTextView.setText(R.string.turn_human);
        } else {
            mBoardView.clearAnimation();
            if (winner == 1) {
                mInfoTextView.setText(R.string.result_tie);
                mTies++;
                
                // Tie: Fade out and fade in
                android.animation.ObjectAnimator fade = android.animation.ObjectAnimator.ofFloat(mBoardView, "alpha", 1f, 0.3f, 1f);
                fade.setDuration(1000);
                fade.start();
            } else if (winner == 2) {
                mInfoTextView.setText(R.string.result_human_wins);
                mHumanWins++;
                
                // Win: Rotation and Pulse
                android.animation.ObjectAnimator rotate = android.animation.ObjectAnimator.ofFloat(mBoardView, "rotation", 0f, 360f);
                android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(mBoardView, "scaleX", 1f, 1.2f, 1f);
                android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(mBoardView, "scaleY", 1f, 1.2f, 1f);
                android.animation.AnimatorSet animSet = new android.animation.AnimatorSet();
                animSet.playTogether(rotate, scaleX, scaleY);
                animSet.setDuration(1000);
                animSet.start();
            } else {
                mInfoTextView.setText(R.string.result_computer_wins);
                mAndroidWins++;
                
                // Lose: Shake side to side
                android.animation.ObjectAnimator shake = android.animation.ObjectAnimator.ofFloat(mBoardView, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f);
                shake.setDuration(800);
                shake.start();
            }
            displayScores();
            mGameOver = true;
            mNewGameButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mGame = new TicTacToeGame();
        
        mBoardView = (BoardView) findViewById(R.id.board);
        mBoardView.setGame(mGame);
        mBoardView.setOnTouchListener(mTouchListener);

        mInfoTextView = (TextView) findViewById(R.id.information);
        mHumanScoreTextView = (TextView) findViewById(R.id.human_score);
        mTieScoreTextView = (TextView) findViewById(R.id.ties_score);
        mAndroidScoreTextView = (TextView) findViewById(R.id.android_score);
        
        mNewGameButton = (android.widget.Button) findViewById(R.id.btn_new_game);
        mNewGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewGame();
            }
        });

        displayScores();
        startNewGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHumanMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.sword);
        mComputerMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.swish);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mHumanMediaPlayer != null) {
            mHumanMediaPlayer.release();
        }
        if (mComputerMediaPlayer != null) {
            mComputerMediaPlayer.release();
        }
    }

    private void displayScores() {
        mHumanScoreTextView.setText(getString(R.string.human_score, mHumanWins));
        mTieScoreTextView.setText(getString(R.string.ties_score, mTies));
        mAndroidScoreTextView.setText(getString(R.string.android_score, mAndroidWins));
    }

    private void startNewGame() {
        mGame.clearBoard();
        mBoardView.invalidate(); // Redraw the board
        mBoardView.clearAnimation();
        mBoardView.setRotation(0f);
        mBoardView.setScaleX(1f);
        mBoardView.setScaleY(1f);
        mBoardView.setTranslationX(0f);
        mBoardView.setAlpha(1f);
        
        mGameOver = false;
        mComputerTurn = false;
        
        mNewGameButton.setVisibility(View.GONE);

        // Human goes first
        mInfoTextView.setText(R.string.first_human);
    }

    private boolean setMove(char player, int location) {
        if (mGame.setMove(player, location)) {
            mBoardView.invalidate(); // Redraw the board
            
            // Play sound effect
            if (player == TicTacToeGame.HUMAN_PLAYER && mHumanMediaPlayer != null) {
                mHumanMediaPlayer.start();
            } else if (player == TicTacToeGame.COMPUTER_PLAYER && mComputerMediaPlayer != null) {
                mComputerMediaPlayer.start();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.new_game) {
            startNewGame();
            return true;
        } else if (id == R.id.ai_difficulty) {
            showDialog(DIALOG_DIFFICULTY_ID);
            return true;
        } else if (id == R.id.quit) {
            showDialog(DIALOG_QUIT_ID);
            return true;
        } else if (id == R.id.about) {
            showDialog(DIALOG_ABOUT_ID);
            return true;
        }
        return false;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        switch(id) {
            case DIALOG_DIFFICULTY_ID:
                builder.setTitle(R.string.difficulty_choose);

                final CharSequence[] levels = {
                    getResources().getString(R.string.difficulty_easy),
                    getResources().getString(R.string.difficulty_harder),
                    getResources().getString(R.string.difficulty_expert)
                };

                int selected = mGame.getDifficultyLevel().ordinal();

                builder.setSingleChoiceItems(levels, selected,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            dialog.dismiss(); // Close dialog

                            mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.values()[item]);

                            // Display the selected difficulty level
                            Toast.makeText(getApplicationContext(), levels[item],
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                dialog = builder.create();
                break;
            case DIALOG_QUIT_ID:
                builder.setMessage(R.string.quit_question)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            AndroidTicTacToeActivity.this.finish();
                        }
                    })
                    .setNegativeButton(R.string.no, null);
                dialog = builder.create();
                break;
            case DIALOG_ABOUT_ID:
                Context context = getApplicationContext();
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.about_dialog, null);

                builder.setView(layout);
                builder.setPositiveButton("OK", null);
                dialog = builder.create();
                break;
        }
        return dialog;
    }
}
