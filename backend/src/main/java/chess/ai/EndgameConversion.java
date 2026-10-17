package chess.ai;

import chess.game.Game;

/** Реализация перевеса тяжёлыми фигурами: ограничивать короля, подводить своего, не ждать карт. */
final class EndgameConversion {
    private EndgameConversion() {}

    static boolean active(Game game, boolean side) {
        int own = 0, enemy = 0;
        boolean heavy = false;
        for (int s = 0; s < game.boardGeometry().size(); s++) {
            var p = game.pieceAt(s);
            if (p == null) continue;
            if (p.white() == side) {
                own += PositionEvaluator.materialValue(p.type());
                if (!p.astral() && (p.type() == 'Q' || p.type() == 'R')) heavy = true;
            } else enemy += PositionEvaluator.materialValue(p.type());
        }
        return heavy && enemy <= 500 && own - enemy >= 450;
    }

    static double evaluate(Game game, boolean side) {
        if (!active(game, side)) return 0;
        int ownKing = -1, enemyKing = -1;
        for (int s = 0; s < game.boardGeometry().size(); s++) {
            var p = game.pieceAt(s);
            if (p != null && p.type() == 'K') {
                if (p.white() == side) ownKing = s; else enemyKing = s;
            }
        }
        if (ownKing < 0 || enemyKing < 0) return 0;
        var geo = game.boardGeometry();
        int king = enemyKing;
        long exits = game.movementOptions(king).stream()
                .filter(a -> !game.isSquareAttacked(a.to(), side)).count();
        int edge = Math.min(Math.min(geo.file(king), geo.width()-1-geo.file(king)),
                Math.min(geo.rank(king), geo.height()-1-geo.rank(king)));
        int distance = Math.max(Math.abs(geo.file(ownKing)-geo.file(king)),Math.abs(geo.rank(ownKing)-geo.rank(king)));
        // Геометрическая оценка выхода консервативна: точную законность и пат проверяет поиск.
        return (8-Math.min(8,exits))*12 - edge*18 - distance*8 - reachableArea(game, king, side)*6
                + rookSupport(game, side, ownKing, king);
    }
    /** При ладейном матовании король подходит к отсекающей линии, вместо вечного следования в оппозиции. */
    private static double rookSupport(Game game, boolean side, int ownKing, int enemyKing) {
        var geo=game.boardGeometry();
        int rook=-1;
        for(int s=0;s<geo.size();s++) {
            var p=game.pieceAt(s);
            if(p==null || p.white()!=side)continue;
            if(p.type()=='Q')return 0;
            if(p.type()=='R' && !p.astral())rook=s;
        }
        if(rook<0)return 0;
        int dr=Math.abs(geo.rank(rook)-geo.rank(enemyKing)),df=Math.abs(geo.file(rook)-geo.file(enemyKing));
        if(dr<=df && (geo.rank(ownKing)-geo.rank(rook))*(geo.rank(enemyKing)-geo.rank(rook))>0)
            return (geo.height()-Math.abs(geo.rank(rook)-geo.rank(ownKing)))*70;
        if(df<dr && (geo.file(ownKing)-geo.file(rook))*(geo.file(enemyKing)-geo.file(rook))>0)
            return (geo.width()-Math.abs(geo.file(rook)-geo.file(ownKing)))*70;
        return 0;
    }

    /** Размер области, по которой король может ходить при неподвижных атакующих фигурах. */
    private static int reachableArea(Game game, int king, boolean attacker) {
        var geometry=game.boardGeometry();
        var fields=game.squareEffects();
        boolean[] visited=new boolean[geometry.size()];
        int[] queue=new int[geometry.size()];
        int start=0,end=1; queue[0]=king; visited[king]=true;
        while(start<end) {
            int square=queue[start++];
            for(int dr=-1;dr<=1;dr++) for(int df=-1;df<=1;df++) {
                if(dr==0 && df==0)continue;
                int rank=geometry.rank(square)+dr,file=geometry.file(square)+df;
                if(!geometry.inside(rank,file))continue;
                int to=geometry.index(rank,file);
                if(visited[to] || game.pieceAt(to)!=null)continue;
                var field=fields.get(to);
                if(field!=null && field.blocksEntry())continue;
                if(game.isSquareAttacked(to,attacker))continue;
                visited[to]=true;queue[end++]=to;
            }
        }
        return end;
    }

}
