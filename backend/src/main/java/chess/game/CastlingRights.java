package chess.game;

/**
 * Сохранённые права на рокировку. Право теряется навсегда после хода короля
 * или ладьи либо взятия стартовой ладьи; временная невозможность (шах, занятая
 * клетка) проверяется правилами хода.
 */
final class CastlingRights {
    private boolean whiteShort = true, whiteLong = true, blackShort = true, blackLong = true;

    boolean allowed(boolean white, boolean shortSide) {
        return white ? (shortSide ? whiteShort : whiteLong) : (shortSide ? blackShort : blackLong);
    }

    void set(boolean white, boolean shortSide, boolean allowed) {
        if (white && shortSide) whiteShort = allowed;
        else if (white) whiteLong = allowed;
        else if (shortSide) blackShort = allowed;
        else blackLong = allowed;
    }

    void revokeAll(boolean white) {
        set(white, true, false);
        set(white, false, false);
    }

    CastlingRights copy() {
        var copy = new CastlingRights();
        copy.whiteShort = whiteShort;
        copy.whiteLong = whiteLong;
        copy.blackShort = blackShort;
        copy.blackLong = blackLong;
        return copy;
    }

    /** Порядок прежних полей сохранения: белые короткая, белые длинная, чёрные короткая, чёрные длинная. */
    @Override public String toString() {
        return "" + whiteShort + whiteLong + blackShort + blackLong;
    }
}
