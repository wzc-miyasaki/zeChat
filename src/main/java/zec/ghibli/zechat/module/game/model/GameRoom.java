package zec.ghibli.zechat.module.game.model;

import org.springframework.web.socket.WebSocketSession;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class GameRoom {

    public static final int SIZE = 15;
    public static final int EMPTY = 0, BLACK = 1, WHITE = 2;

    public final String roomId;
    public GameRoom(String roomId) { this.roomId = roomId; }
    public final WebSocketSession[] sessions = new WebSocketSession[2];
    public final String[] playerIds = new String[2];
    public final int[][] board = new int[SIZE][SIZE];
    public int turn = 0;
    public boolean finished = false;
    public long disconnectedAt = -1;

    public int restartRequestSlot = -1;
    public int undoRequestSlot    = -1;
    public final Deque<int[]> moveHistory = new ArrayDeque<>(); // [x, y, color, prevTurn]

    public int colorOf(WebSocketSession s) {
        return s == sessions[0] ? BLACK : s == sessions[1] ? WHITE : -1;
    }

    public int slotOf(WebSocketSession s) {
        return s == sessions[0] ? 0 : s == sessions[1] ? 1 : -1;
    }

    public boolean applyMove(int x, int y, int color) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || board[y][x] != EMPTY) return false;
        moveHistory.push(new int[]{x, y, color, turn});
        board[y][x] = color;
        return true;
    }

    public int[] undoLastMove() {
        if (moveHistory.isEmpty()) return null;
        int[] last = moveHistory.pop();
        board[last[1]][last[0]] = EMPTY;
        turn = last[3];
        return last;
    }

    public void resetBoard() {
        for (int[] row : board) Arrays.fill(row, EMPTY);
        moveHistory.clear();
        turn = 0;
        finished = false;
        restartRequestSlot = -1;
        undoRequestSlot = -1;
    }

    public boolean checkWin(int x, int y) {
        int color = board[y][x];
        int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int count = 1 + countDir(x, y, d[0], d[1], color) + countDir(x, y, -d[0], -d[1], color);
            if (count >= 5) return true;
        }
        return false;
    }

    private int countDir(int x, int y, int dx, int dy, int color) {
        int count = 0;
        for (int i = 1; i < 5; i++) {
            int nx = x + dx * i, ny = y + dy * i;
            if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE || board[ny][nx] != color) break;
            count++;
        }
        return count;
    }

    public boolean isFull() {
        for (int[] row : board) for (int c : row) if (c == EMPTY) return false;
        return true;
    }
}
