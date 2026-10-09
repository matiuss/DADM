package co.edu.unal.tictactoe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameListActivity extends AppCompatActivity {

    private ListView listGames;
    private Button btnCreateGame;
    private EditText etPlayerName;
    private DatabaseReference gamesRef;
    private String playerId;
    private SharedPreferences prefs;
    
    private List<String> gameIds = new ArrayList<>();
    private List<String> gameTitles = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        listGames = findViewById(R.id.list_games);
        btnCreateGame = findViewById(R.id.btn_create_game);
        etPlayerName = findViewById(R.id.et_player_name);

        prefs = getSharedPreferences("tic_tac_toe_prefs", MODE_PRIVATE);
        playerId = prefs.getString("player_id", null);
        if (playerId == null) {
            playerId = UUID.randomUUID().toString();
            prefs.edit().putString("player_id", playerId).apply();
        }
        
        String savedName = prefs.getString("player_name", "");
        etPlayerName.setText(savedName);

        gamesRef = FirebaseDatabase.getInstance().getReference("games");

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, gameTitles);
        listGames.setAdapter(adapter);

        btnCreateGame.setOnClickListener(v -> createGame());

        listGames.setOnItemClickListener((parent, view, position, id) -> {
            joinGame(gameIds.get(position));
        });

        loadGames();
    }
    
    private String getPlayerName() {
        String name = etPlayerName.getText().toString().trim();
        if (name.isEmpty()) name = "Player";
        prefs.edit().putString("player_name", name).apply();
        return name;
    }

    private void createGame() {
        String gameId = gamesRef.push().getKey();
        if (gameId == null) return;

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("hostId", playerId);
        gameData.put("hostName", getPlayerName());
        gameData.put("status", "waiting");
        
        List<Integer> board = new ArrayList<>();
        for (int i = 0; i < 9; i++) board.add(0);
        gameData.put("board", board);
        gameData.put("turn", playerId);
        gameData.put("hostScore", 0);
        gameData.put("guestScore", 0);
        gameData.put("tiesScore", 0);

        gamesRef.child(gameId).setValue(gameData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                startGameActivity(gameId, true);
            } else {
                Toast.makeText(this, "Failed to create game", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void joinGame(String gameId) {
        gamesRef.child(gameId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                String hostId = snapshot.child("hostId").getValue(String.class);
                String guestId = snapshot.child("guestId").getValue(String.class);
                String status = snapshot.child("status").getValue(String.class);

                if (playerId.equals(hostId)) {
                    // Rejoining own game
                    gamesRef.child(gameId).child("hostName").setValue(getPlayerName());
                    startGameActivity(gameId, true);
                } else if (guestId != null && playerId.equals(guestId)) {
                    // Rejoining as guest
                    gamesRef.child(gameId).child("guestName").setValue(getPlayerName());
                    startGameActivity(gameId, false);
                } else if ("waiting".equals(status)) {
                    // Joining new game
                    gamesRef.child(gameId).child("guestId").setValue(playerId);
                    gamesRef.child(gameId).child("guestName").setValue(getPlayerName());
                    gamesRef.child(gameId).child("status").setValue("playing");
                    startGameActivity(gameId, false);
                } else {
                    Toast.makeText(this, "Game is already full or playing", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void startGameActivity(String gameId, boolean isHost) {
        Intent intent = new Intent(this, AndroidTicTacToeActivity.class);
        intent.putExtra("GAME_ID", gameId);
        intent.putExtra("PLAYER_ID", playerId);
        intent.putExtra("IS_HOST", isHost);
        startActivity(intent);
    }

    private void loadGames() {
        gamesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                gameIds.clear();
                gameTitles.clear();
                for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                    String status = gameSnapshot.child("status").getValue(String.class);
                    if ("waiting".equals(status)) {
                        String id = gameSnapshot.getKey();
                        String hostName = gameSnapshot.child("hostName").getValue(String.class);
                        if (hostName == null) hostName = "Unknown";
                        gameIds.add(id);
                        gameTitles.add("Host: " + hostName);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(GameListActivity.this, "Failed to load games", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
