package co.edu.unal.tictactoe;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TicTacToeGameTest {

    private TicTacToeGame mGame;

    @Before
    public void setUp() {
        mGame = new TicTacToeGame();
        mGame.clearBoard();
    }

    @Test
    public void testInitialStateNoWinner() {
        assertEquals(0, mGame.checkForWinner());
    }

    @Test
    public void testHumanHorizontalWin() {
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 0);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 1);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 2);
        assertEquals(2, mGame.checkForWinner());
    }

    @Test
    public void testComputerVerticalWin() {
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 1);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 4);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 7);
        assertEquals(3, mGame.checkForWinner());
    }

    @Test
    public void testDiagonalWin() {
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 0);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 4);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 8);
        assertEquals(2, mGame.checkForWinner());

        mGame.clearBoard();
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 2);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 4);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 6);
        assertEquals(3, mGame.checkForWinner());
    }

    @Test
    public void testTieCondition() {
        // Board:
        // X O X
        // X O O
        // O X X
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 0);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 1);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 2);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 3);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 4);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 5);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 6);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 7);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 8);

        assertEquals(1, mGame.checkForWinner());
    }

    @Test
    public void testAIWinningMovePriority() {
        // Computer has spots 0 and 1, spot 2 is open -> AI must take 2 to win
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 0);
        mGame.setMove(TicTacToeGame.COMPUTER_PLAYER, 1);
        // Human also has spots 3 and 4 -> AI could block or win, but WIN takes priority!
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 3);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 4);

        int computerMove = mGame.getComputerMove();
        assertEquals(2, computerMove);
    }

    @Test
    public void testAIBlockMovePriority() {
        // Human has spots 0 and 1, spot 2 is open -> AI must block by taking 2
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 0);
        mGame.setMove(TicTacToeGame.HUMAN_PLAYER, 1);

        int computerMove = mGame.getComputerMove();
        assertEquals(2, computerMove);
    }
}
