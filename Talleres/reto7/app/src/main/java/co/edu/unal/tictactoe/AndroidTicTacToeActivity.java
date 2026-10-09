package co.edu.unal.tictactoe;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AndroidTicTacToeActivity extends AppCompatActivity {

    static final int DIALOG_ABOUT_ID = 1;

    private TicTacToeGame mGame;
    private BoardView mBoardView;
    private TextView mInfoTextView;

    private boolean mGameOver = false;

    MediaPlayer mHumanMediaPlayer;
    MediaPlayer mComputerMediaPlayer;
    
    private android.widget.Button mNewGameButton;

    // Firebase
    private String gameId;
    private String playerId;
    private boolean isHost;
    private DatabaseReference gameRef;
    private String turn;
    private String guestId;

    private TextView mHostScoreTextView;
    private TextView mGuestScoreTextView;
    private TextView mTieScoreTextView;

    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (mGameOver || !playerId.equals(turn)) return false;
            
            // Cannot play if guest hasn't joined
            if (guestId == null || guestId.isEmpty()) {
                Toast.makeText(AndroidTicTacToeActivity.this, "Waiting for another player...", Toast.LENGTH_SHORT).show();
                return false;
            }

            int col = (int) event.getX() / mBoardView.getBoardCellWidth();
            int row = (int) event.getY() / mBoardView.getBoardCellHeight();
            int pos = row * 3 + col;

            if (mGame.getBoardOccupant(pos) == TicTacToeGame.OPEN_SPOT) {
                // Simulate move locally to check for winner
                mGame.setMove(isHost ? TicTacToeGame.HUMAN_PLAYER : TicTacToeGame.COMPUTER_PLAYER, pos);
                int winner = mGame.checkForWinner();
                
                String opponentId = isHost ? guestId : getHostId();
                
                // Update Firebase
                if (winner != 0) {
                    gameRef.child("turn").setValue("game_over");
                    if (winner == 1) {
                        gameRef.child("tiesScore").setValue(com.google.firebase.database.ServerValue.increment(1));
                    } else if (winner == 2) {
                        gameRef.child("hostScore").setValue(com.google.firebase.database.ServerValue.increment(1));
                    } else if (winner == 3) {
                        gameRef.child("guestScore").setValue(com.google.firebase.database.ServerValue.increment(1));
                    }
                } else {
                    gameRef.child("turn").setValue(opponentId);
                }
                
                gameRef.child("board").child(String.valueOf(pos)).setValue(isHost ? 1 : 2);
            }
            return false;
        }
    };

    private String hostId;

    private String getHostId() {
        return hostId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Intent intent = getIntent();
        gameId = intent.getStringExtra("GAME_ID");
        playerId = intent.getStringExtra("PLAYER_ID");
        isHost = intent.getBooleanExtra("IS_HOST", true);

        mGame = new TicTacToeGame();
        
        mBoardView = (BoardView) findViewById(R.id.board);
        mBoardView.setGame(mGame);
        mBoardView.setOnTouchListener(mTouchListener);

        mInfoTextView = (TextView) findViewById(R.id.information);
        mHostScoreTextView = (TextView) findViewById(R.id.human_score);
        mTieScoreTextView = (TextView) findViewById(R.id.ties_score);
        mGuestScoreTextView = (TextView) findViewById(R.id.android_score);
        
        mNewGameButton = (android.widget.Button) findViewById(R.id.btn_new_game);
        mNewGameButton.setVisibility(View.GONE);
        mNewGameButton.setOnClickListener(v -> resetGame());

        gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        listenForGameChanges();
    }

    private void listenForGameChanges() {
        gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (!isFinishing()) {
                        Toast.makeText(AndroidTicTacToeActivity.this, "The opponent has left the game.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    return;
                }

                hostId = snapshot.child("hostId").getValue(String.class);
                guestId = snapshot.child("guestId").getValue(String.class);
                turn = snapshot.child("turn").getValue(String.class);
                
                String hostName = snapshot.child("hostName").getValue(String.class);
                String guestName = snapshot.child("guestName").getValue(String.class);
                if (hostName == null) hostName = "Host";
                if (guestName == null) guestName = "Guest";

                // Reconstruct board
                mGame.clearBoard();
                Object boardObj = snapshot.child("board").getValue();
                if (boardObj instanceof List) {
                    List<?> firebaseBoard = (List<?>) boardObj;
                    for (int i = 0; i < firebaseBoard.size(); i++) {
                        Object valObj = firebaseBoard.get(i);
                        if (valObj instanceof Number) {
                            int val = ((Number) valObj).intValue();
                            if (val == 1) {
                                mGame.setMove(TicTacToeGame.HUMAN_PLAYER, i); // X
                            } else if (val == 2) {
                                mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, i); // O
                            }
                        }
                    }
                }
                
                // Read scores
                Integer hScore = snapshot.child("hostScore").getValue(Integer.class);
                Integer gScore = snapshot.child("guestScore").getValue(Integer.class);
                Integer tScore = snapshot.child("tiesScore").getValue(Integer.class);
                
                int hostPoints = hScore != null ? hScore : 0;
                int guestPoints = gScore != null ? gScore : 0;
                int tiePoints = tScore != null ? tScore : 0;

                // Update UI based on who is playing
                if (isHost) {
                    mHostScoreTextView.setText(hostName + " (You): " + hostPoints);
                    mGuestScoreTextView.setText(guestName + ": " + guestPoints);
                } else {
                    mHostScoreTextView.setText(hostName + ": " + hostPoints);
                    mGuestScoreTextView.setText(guestName + " (You): " + guestPoints);
                }
                mTieScoreTextView.setText("Ties: " + tiePoints);
                
                mBoardView.invalidate();

                int winner = mGame.checkForWinner();
                if (winner == 0) {
                    mGameOver = false;
                    if (guestId == null) {
                        mInfoTextView.setText("Waiting for another player to join...");
                    } else if (playerId.equals(turn)) {
                        mInfoTextView.setText("It's your turn!");
                    } else {
                        mInfoTextView.setText("Waiting for opponent's turn...");
                    }
                } else {
                    handleWinner(winner);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void handleWinner(int winner) {
        mBoardView.clearAnimation();
        mGameOver = true;
        
        if (winner == 1) {
            mInfoTextView.setText(R.string.result_tie);
        } else if (winner == 2) { // X wins
            if (isHost) mInfoTextView.setText("You win!");
            else mInfoTextView.setText("You lose!");
        } else { // O wins
            if (!isHost) mInfoTextView.setText("You win!");
            else mInfoTextView.setText("You lose!");
        }

        if (isHost) {
            mNewGameButton.setVisibility(View.VISIBLE);
        }
    }

    private void resetGame() {
        List<Integer> newBoard = new ArrayList<>();
        for (int i = 0; i < 9; i++) newBoard.add(0);
        gameRef.child("board").setValue(newBoard);
        gameRef.child("turn").setValue(hostId);
        mNewGameButton.setVisibility(View.GONE);
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
        if (mHumanMediaPlayer != null) mHumanMediaPlayer.release();
        if (mComputerMediaPlayer != null) mComputerMediaPlayer.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && gameRef != null) {
            gameRef.removeValue();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        menu.findItem(R.id.ai_difficulty).setVisible(false); // Disable AI difficulty in online mode
        menu.findItem(R.id.new_game).setVisible(false);
        menu.findItem(R.id.reset_scores).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.about) {
            showDialog(DIALOG_ABOUT_ID);
            return true;
        }
        return false;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_ABOUT_ID) {
            Context context = getApplicationContext();
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.about_dialog, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(layout);
            builder.setPositiveButton("OK", null);
            return builder.create();
        }
        return null;
    }
}
