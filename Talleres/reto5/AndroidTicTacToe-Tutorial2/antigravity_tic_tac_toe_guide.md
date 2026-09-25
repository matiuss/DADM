# Antigravity Development Guide: Android Tic-Tac-Toe App

This guide contains the step-by-step instructions, code logic, XML layouts, and prompt guidelines to develop the classic Android Tic-Tac-Toe application using Antigravity in the console/terminal.

---

## 1. Project Overview & Architecture

The objective is to build a classic Tic-Tac-Toe game where a human player (green $X$) plays against the computer/Android (red $O$).

### Architecture Goal
Separation of concerns between UI logic and Game logic:
* **`TicTacToeGame.java`**: Handles the underlying game board, player moves, simple AI logic, and win/tie checking.
* **`AndroidTicTacToeActivity.java`**: Handles button click events, updates text views, handles menu options, and manages board interactivity.
* **`main.xml`**: Layout containing a `TableLayout` grid of 9 buttons and a `TextView` status indicator.
* **`strings.xml`**: Centralized string resources for localization and maintainability.

---

## 2. Environment & Project Setup

When prompting Antigravity, initialize the Android project structure with the following parameters:

* **Application Name:** Android Tic-Tac-Toe
* **Project Name:** AndroidTicTacToe-Tutorial2
* **Package Name:** `co.edu.unal.tictactoe` (or `edu.harding.tictactoe`)
* **Activity Name:** `AndroidTicTacToeActivity`

---

## 3. Game Logic: `TicTacToeGame.java`

The game state is managed using a `char[]` array of size 9.

### Key Requirements
1. **Constants**:
   ```java
   public static final char HUMAN_PLAYER = 'X';
   public static final char COMPUTER_PLAYER = 'O';
   public static final char OPEN_SPOT = ' ';
   public static final int BOARD_SIZE = 9;
   ```
2. **Core Methods**:
   * `clearBoard()`: Resets all board indices to `OPEN_SPOT`.
   * `setMove(char player, int location)`: Sets the move for the specified player if the position is available.
   * `getComputerMove()`: Calculates the best move for the computer (Wins if possible, blocks human win, or chooses randomly).
   * `checkForWinner()`: Returns:
     * `0`: Game still in progress / No winner yet
     * `1`: Tie
     * `2`: Human ($X$) won
     * `3`: Computer ($O$) won

### Reference Implementation
```java
package co.edu.unal.tictactoe;

import java.util.Random;

public class TicTacToeGame {

    public static final char HUMAN_PLAYER = 'X';
    public static final char COMPUTER_PLAYER = 'O';
    public static final char OPEN_SPOT = ' ';
    public static final int BOARD_SIZE = 9;

    private char mBoard[] = new char[BOARD_SIZE];
    private Random mRand;

    public TicTacToeGame() {
        mRand = new Random();
    }

    /** Clear the board of all X's and O's by setting all spots to OPEN_SPOT. */
    public void clearBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            mBoard[i] = OPEN_SPOT;
        }
    }

    /** Set the given player at the given location on the game board. */
    public void setMove(char player, int location) {
        if (location >= 0 && location < BOARD_SIZE && mBoard[location] == OPEN_SPOT) {
            mBoard[location] = player;
        }
    }

    /** Check for a winner and return status:
     * 0 if no winner/tie yet
     * 1 if tie
     * 2 if X won
     * 3 if O won
     */
    public int checkForWinner() {
        // Check horizontal wins
        for (int i = 0; i <= 6; i += 3) {
            if (mBoard[i] == HUMAN_PLAYER &&
                mBoard[i+1] == HUMAN_PLAYER &&
                mBoard[i+2] == HUMAN_PLAYER)
                return 2;
            if (mBoard[i] == COMPUTER_PLAYER &&
                mBoard[i+1] == COMPUTER_PLAYER &&
                mBoard[i+2] == COMPUTER_PLAYER)
                return 3;
        }

        // Check vertical wins
        for (int i = 0; i <= 2; i++) {
            if (mBoard[i] == HUMAN_PLAYER &&
                mBoard[i+3] == HUMAN_PLAYER &&
                mBoard[i+6] == HUMAN_PLAYER)
                return 2;
            if (mBoard[i] == COMPUTER_PLAYER &&
                mBoard[i+3] == COMPUTER_PLAYER &&
                mBoard[i+6] == COMPUTER_PLAYER)
                return 3;
        }

        // Check diagonals
        if ((mBoard[0] == HUMAN_PLAYER && mBoard[4] == HUMAN_PLAYER && mBoard[8] == HUMAN_PLAYER) ||
            (mBoard[2] == HUMAN_PLAYER && mBoard[4] == HUMAN_PLAYER && mBoard[6] == HUMAN_PLAYER))
            return 2;
        if ((mBoard[0] == COMPUTER_PLAYER && mBoard[4] == COMPUTER_PLAYER && mBoard[8] == COMPUTER_PLAYER) ||
            (mBoard[2] == COMPUTER_PLAYER && mBoard[4] == COMPUTER_PLAYER && mBoard[6] == COMPUTER_PLAYER))
            return 3;

        // Check for open spots
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] != HUMAN_PLAYER && mBoard[i] != COMPUTER_PLAYER)
                return 0;
        }

        return 1; // Tie
    }

    /** Return the best move for the computer (0-8). */
    public int getComputerMove() {
        int move;

        // 1. See if there's a winning move for Computer
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                mBoard[i] = COMPUTER_PLAYER;
                if (checkForWinner() == 3) {
                    mBoard[i] = OPEN_SPOT;
                    return i;
                }
                mBoard[i] = OPEN_SPOT;
            }
        }

        // 2. See if there's a block for Human win
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                mBoard[i] = HUMAN_PLAYER;
                if (checkForWinner() == 2) {
                    mBoard[i] = OPEN_SPOT;
                    return i;
                }
                mBoard[i] = OPEN_SPOT;
            }
        }

        // 3. Otherwise, make a random move
        do {
            move = mRand.nextInt(BOARD_SIZE);
        } while (mBoard[move] != OPEN_SPOT);

        return move;
    }
}
```

---

## 4. UI Layout & Resources

### String Resources (`res/values/strings.xml`)
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Tic-Tac-Toe</string>
    <string name="first_human">You go first.</string>
    <string name="turn_human">Your turn.</string>
    <string name="turn_computer">Android\'s turn.</string>
    <string name="result_tie">It\'s a tie.</string>
    <string name="result_human_wins">You won!</string>
    <string name="result_computer_wins">Android won!</string>
</resources>
```

### Layout File (`res/layout/main.xml`)
Combines `LinearLayout` and `TableLayout` to arrange 9 buttons in a 3x3 grid.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="fill_parent"
    android:layout_height="fill_parent"
    android:gravity="center_horizontal" >

    <TableLayout
        android:id="@+id/play_grid"
        android:layout_height="wrap_content"
        android:layout_width="fill_parent"
        android:layout_marginTop="5dp" >

        <TableRow android:gravity="center_horizontal">
            <Button android:id="@+id/one"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="1" />
            <Button android:id="@+id/two"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="2" />
            <Button android:id="@+id/three"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="3" />
        </TableRow>

        <TableRow android:gravity="center_horizontal">
            <Button android:id="@+id/four"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="4" />
            <Button android:id="@+id/five"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="5" />
            <Button android:id="@+id/six"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="6" />
        </TableRow>

        <TableRow android:gravity="center_horizontal">
            <Button android:id="@+id/seven"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="7" />
            <Button android:id="@+id/eight"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="8" />
            <Button android:id="@+id/nine"
                android:layout_width="100dp"
                android:layout_height="100dp"
                android:textSize="70dp"
                android:text="9" />
        </TableRow>
    </TableLayout>

    <TextView 
        android:id="@+id/information"
        android:layout_width="fill_parent"
        android:layout_height="wrap_content"
        android:gravity="center_horizontal"
        android:textSize="20dp"
        android:text="info"
        android:layout_marginTop="20dp" />

</LinearLayout>
```

---

## 5. UI Controller: `AndroidTicTacToeActivity.java`

Handles user input, calls the `TicTacToeGame` backend, updates the UI, and manages game state flow.

```java
package co.edu.unal.tictactoe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AndroidTicTacToeActivity extends Activity {

    // Internal game representation
    private TicTacToeGame mGame;

    // Board buttons
    private Button mBoardButtons[];

    // Text view for game state status
    private TextView mInfoTextView;

    // Game state check flag
    private boolean mGameOver = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mBoardButtons = new Button[TicTacToeGame.BOARD_SIZE];
        mBoardButtons[0] = (Button) findViewById(R.id.one);
        mBoardButtons[1] = (Button) findViewById(R.id.two);
        mBoardButtons[2] = (Button) findViewById(R.id.three);
        mBoardButtons[3] = (Button) findViewById(R.id.four);
        mBoardButtons[4] = (Button) findViewById(R.id.five);
        mBoardButtons[5] = (Button) findViewById(R.id.six);
        mBoardButtons[6] = (Button) findViewById(R.id.seven);
        mBoardButtons[7] = (Button) findViewById(R.id.eight);
        mBoardButtons[8] = (Button) findViewById(R.id.nine);

        mInfoTextView = (TextView) findViewById(R.id.information);

        mGame = new TicTacToeGame();

        startNewGame();
    }

    private void startNewGame() {
        mGame.clearBoard();
        mGameOver = false;

        // Reset all buttons
        for (int i = 0; i < mBoardButtons.length; i++) {
            mBoardButtons[i].setText("");
            mBoardButtons[i].setEnabled(true);
            mBoardButtons[i].setOnClickListener(new ButtonClickListener(i));
        }

        // Human goes first
        mInfoTextView.setText(R.string.first_human);
    }

    private void setMove(char player, int location) {
        mGame.setMove(player, location);
        mBoardButtons[location].setEnabled(false);
        mBoardButtons[location].setText(String.valueOf(player));
        if (player == TicTacToeGame.HUMAN_PLAYER) {
            mBoardButtons[location].setTextColor(Color.rgb(0, 200, 0));
        } else {
            mBoardButtons[location].setTextColor(Color.rgb(200, 0, 0));
        }
    }

    // Handles clicks on board buttons
    private class ButtonClickListener implements View.OnClickListener {
        int location;

        public ButtonClickListener(int location) {
            this.location = location;
        }

        public void onClick(View view) {
            if (!mGameOver && mBoardButtons[location].isEnabled()) {
                setMove(TicTacToeGame.HUMAN_PLAYER, location);

                // Check for winner
                int winner = mGame.checkForWinner();
                if (winner == 0) {
                    mInfoTextView.setText(R.string.turn_computer);
                    int move = mGame.getComputerMove();
                    setMove(TicTacToeGame.COMPUTER_PLAYER, move);
                    winner = mGame.checkForWinner();
                }

                if (winner == 0) {
                    mInfoTextView.setText(R.string.turn_human);
                } else if (winner == 1) {
                    mInfoTextView.setText(R.string.result_tie);
                    mGameOver = true;
                } else if (winner == 2) {
                    mInfoTextView.setText(R.string.result_human_wins);
                    mGameOver = true;
                } else {
                    mInfoTextView.setText(R.string.result_computer_wins);
                    mGameOver = true;
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add("New Game");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        startNewGame();
        return true;
    }
}
```

---

## 6. Extra Challenges (Extensions)

To extend the application, implement the following features:

1. **Alternating First Player**:
   * Add a flag variable `mHumanFirst` to alternate who goes first on each reset.
   * If `!mHumanFirst`, call `mGame.getComputerMove()` immediately during `startNewGame()`.

2. **Score Counter**:
   * Maintain counters for wins (`mHumanWins`), ties (`mTies`), and computer wins (`mAndroidWins`).
   * Add three `TextView` controls at the bottom of the layout inside a `RelativeLayout` to show updated metrics:
     ```
     Human: 0 | Ties: 1 | Android: 0
     ```

---

## 7. Direct Console Prompts for Antigravity

When executing build steps via Antigravity console, use the following prompts:

### Prompt 1: Project & Architecture Generation
> "Create an Android project structure for `co.edu.unal.tictactoe`. Generate `TicTacToeGame.java` containing full logic for board management (9 positions), standard move checking, tie checking, and computer AI logic (win > block > random)."

### Prompt 2: Layout Definition
> "Generate `res/layout/main.xml` using a vertical `LinearLayout` containing a 3x3 grid `TableLayout` with 9 buttons (`@+id/one` through `@+id/nine`) and a `TextView` status indicator (`@+id/information`)."

### Prompt 3: UI Activity Implementation
> "Implement `AndroidTicTacToeActivity.java`. Connect UI components, add click handlers using a custom `ButtonClickListener` class, set green color for human 'X' and red color for computer 'O', implement menu handling for starting a new game, and use string resources for game status labels."