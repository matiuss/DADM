# Android Application Programming
## Challenge: Menus and Dialog Boxes

---

## Introduction

In the previous tutorial, you created a simple tic-tac-toe game for Android. The goal of this tutorial is to improve your game by creating a more sophisticated menu that uses a few dialog boxes. In this tutorial, you will create an options menu with three options:

1. **New Game** – to start a new game
2. **Difficulty** – to set the AI difficulty level to Easy, Harder, or Expert
3. **Quit** – to quit the app

> **Note:** You should familiarize yourself with the official Menu Design Guidelines when designing your own menus. Android also supports context menus (floating lists of menu items that appear when holding your finger down on the screen via a long press), but they will not be covered in this tutorial.

---

## Changes to the Game Logic

We are going to create a menu option in this tutorial to change the difficulty level of the game. Right now the game only has one difficulty level which we'll call "Expert". You will need to modify the `TicTacToeGame` class so the difficulty level can be set:
- When set to **"Easy"**, the computer will always just make random moves.
- When set to **"Harder"**, the computer will make a winning move if possible; otherwise, it will make a random move.

### Step 1: Add Difficulty Enum and Variable
Open `TicTacToeGame.java`, and add an enumeration for the difficulty level and a variable for keeping track of the current difficulty level setting:

```java
public class TicTacToeGame {
    // The computer's difficulty levels
    public enum DifficultyLevel { Easy, Harder, Expert };

    // Current difficulty level
    private DifficultyLevel mDifficultyLevel = DifficultyLevel.Expert;
```

### Step 2: Add Getters and Setters
Create getters and setters for the difficulty level:

```java
public DifficultyLevel getDifficultyLevel() {
    return mDifficultyLevel;
}

public void setDifficultyLevel(DifficultyLevel difficultyLevel) {
    mDifficultyLevel = difficultyLevel;
}
```

### Step 3: Modify `getComputerMove()`
Modify the `getComputerMove()` method to call the appropriate function depending on the difficulty level. 

*Note:* It is left to you to implement `getRandomMove()`, `getWinningMove()`, and `getBlockingMove()` based on the pre-existing code. These functions might need to temporarily modify the board array, but they should leave the array in the same state it was in before they were called.

```java
public int getComputerMove() {
    int move = -1;

    if (mDifficultyLevel == DifficultyLevel.Easy) {
        move = getRandomMove();
    } else if (mDifficultyLevel == DifficultyLevel.Harder) {
        move = getWinningMove();
        if (move == -1) {
            move = getRandomMove();
        }
    } else if (mDifficultyLevel == DifficultyLevel.Expert) {
        // Try to win, but if that's not possible, block.
        // If that's not possible, move anywhere.
        move = getWinningMove();
        if (move == -1) {
            move = getBlockingMove();
        }
        if (move == -1) {
            move = getRandomMove();
        }
    }

    return move;
}
```

---

## Create the Menu in XML

Now we will add a menu to the Activity that will be used to start a new game, set the `TicTacToeGame` difficulty level, and quit. Android can display up to six menu options at once, and a *More* option makes an expanded menu available. We will only have three options in our menu.

1. Right-click on the `res` directory and select **New Folder**.
2. Name the folder `menu`.
3. Right-click on the new `menu` directory and select **New → File**.
4. Name the file `options_menu.xml`.
5. Enter the following XML into `res/menu/options_menu.xml`:

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/new_game"
          android:title="New Game"
          android:icon="@drawable/new_game" />
    <item android:id="@+id/ai_difficulty"
          android:title="Difficulty"
          android:icon="@drawable/difficulty_level" />
    <item android:id="@+id/quit"
          android:title="Quit"
          android:icon="@drawable/quit_game" />
</menu>
```

---

## Add Menu Items Images

1. Create `new_game.png`, `difficulty_level.png`, and `quit_game.png` images using your favorite image editor (or obtain icons from AndroidIcons.com or the Android SDK). Ensure the icons are **not taller than 42 pixels** to prevent them from obstructing menu item text.
2. Create a folder named `res/drawable` (images placed here will be used across standard device resolutions without DPI differentiation for now).
3. Copy or drag and drop the three PNG files into the `res/drawable` folder.
4. Verify that the file names match those referenced in `options_menu.xml`.

---

## Display the Menu and Respond to Menu Selections

### Step 1: Inflate Menu and Handle Selections
Modify your Activity to load the options menu and handle selections:

```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.options_menu, menu);
    return true;
}

@Override
public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
        case R.id.new_game:
            startNewGame();
            return true;
        case R.id.ai_difficulty:
            showDialog(DIALOG_DIFFICULTY_ID);
            return true;
        case R.id.quit:
            showDialog(DIALOG_QUIT_ID);
            return true;
    }
    return false;
}
```

### Step 2: Define Dialog Identifier Constants
Define the integer constants used to distinguish between dialog boxes:

```java
static final int DIALOG_DIFFICULTY_ID = 0;
static final int DIALOG_QUIT_ID = 1;
```

### Step 3: Implement `onCreateDialog()` for Difficulty Selection
Override `onCreateDialog(int id)` using `AlertDialog.Builder`. The dialog displays single-choice radio buttons and reports the choice with a `Toast`:

```java
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

            // TODO: Set selected, an integer (0 to n-1), for the Difficulty dialog.
            // selected is the radio button that should be selected.
            int selected = mGame.getDifficultyLevel().ordinal();

            builder.setSingleChoiceItems(levels, selected,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss(); // Close dialog

                        // TODO: Set the diff level of mGame based on which item was selected.
                        mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.values()[item]);

                        // Display the selected difficulty level
                        Toast.makeText(getApplicationContext(), levels[item],
                            Toast.LENGTH_SHORT).show();
                    }
                });
            dialog = builder.create();
            break;
    }
    return dialog;
}
```

### Step 4: Add Confirmation Quit Dialog
Append the quit confirmation logic to the `switch(id)` block inside `onCreateDialog()`:

```java
        case DIALOG_QUIT_ID:
            // Create the quit confirmation dialog
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
```

---

## Extra Challenge

### 1. Custom Application Launcher Icon
1. Create a custom icon named `icon.png` and place it in `res/drawable/`.
2. Update `AndroidManifest.xml`:

```xml
<application
    android:icon="@drawable/icon"
    android:label="@string/app_name" >
```

### 2. Custom "About" Dialog Box
Create an options menu item that displays an About dialog containing a custom layout:

1. Create `res/layout/about_dialog.xml` containing image and text views.
2. Inflate the layout using `LayoutInflater` and attach it to an `AlertDialog.Builder`:

```java
AlertDialog.Builder builder = new AlertDialog.Builder(this);
Context context = getApplicationContext();
LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
View layout = inflater.inflate(R.layout.about_dialog, null);

builder.setView(layout);
builder.setPositiveButton("OK", null);
Dialog dialog = builder.create();
```

---

*Content adapted from Frank McCown at Harding University under Creative Commons Attribution 3.0 License (CC BY-NC).*