package app.cardchess.mobile;

import android.app.Application;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps the game across Activity recreation, with no network listener or background service. */
public final class ChessApplication extends Application {
    final ExecutorService commands = Executors.newFixedThreadPool(2);
    private OfflineGame game;
    synchronized OfflineGame game() throws Exception {
        if (game == null) game = new OfflineGame(this);
        return game;
    }
}
