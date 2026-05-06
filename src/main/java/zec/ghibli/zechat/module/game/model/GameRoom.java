package zec.ghibli.zechat.module.game.model;

import org.springframework.web.socket.WebSocketSession;
import java.util.UUID;

public class GameRoom {

    public static final int SIZE = 15;
    public static final int EMPTY = 0, BLACK = 1, WHITE = 2;

    public final String roomId;
    public GameRoom(String roomId) { this.roomId = roomId; }
    public final WebSocketSession[] sessions = new WebSocketSession[2]; // 0=black, 1=white
    public final String[] playerIds = new String[2]; // userId per slot
    public final int[][] board = new int[SIZE][SIZE];
    public int turn = 0; // 0=black's turn, 1=white's turn
    public boolean finished = false;
    public long disconnectedAt = -1;

    public int colorOf(WebSocketSession s) {
        return s == sessions[0] ? BLACK : s == sessions[1] ? WHITE : -1;
    }

    public int slotOf(WebSocketSession s) {
        return s == sessions[0] ? 0 : s == sessions[1] ? 1 : -1;
    }

    public boolean applyMove(int x, int y, int color) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || board[y][x] != EMPTY) return false;
        board[y][x] = color;
        return true;
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
