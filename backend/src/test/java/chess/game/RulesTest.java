package chess.game;

import chess.ai.Ai;
import chess.cards.Card;
import chess.cards.CardType;
import chess.json.Json;
import chess.pieces.Piece;
import java.util.*;

/** Проверки правил карт и шахмат, включая число позиций после обычных ходов. */
public final class RulesTest {
    static int checks;
    static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    static Game empty(){Game g=new Game("human",3,"normal");g.board.clear();g.board.set(4, new Piece(1,'K',true));g.board.set(60, new Piece(2,'K',false));g.deck.clear();g.discard.clear();g.wKingCastle=g.wQueenCastle=g.bKingCastle=g.bQueenCastle=false;return g;}
    static Card card(Game g,CardType t){Card c=new Card(1000+g.whiteHand.size()+g.blackHand.size(),t);g.hand(g.whiteTurn).add(c);return c;}
    static void move(Game g,int from,int to){g.play(g.legalActions().stream().filter(a->a.kind().equals("move")&&a.from()==from&&a.to()==to).findFirst().orElseThrow().id());}
    static void cast(Game g,Card c,int to){g.play(g.legalActions().stream().filter(a->a.cardId()==c.id()&&a.to()==to).findFirst().orElseThrow().id());}
    static boolean hasMove(Game g,int from,int to){return g.legalActions().stream().anyMatch(a->a.kind().equals("move")&&a.from()==from&&a.to()==to);}
    static long perft(Game g,int depth){if(depth==0)return 1;long n=0;for(var a:g.legalActions())if(a.kind().equals("move")){Game next=g.copy();next.applyUnchecked(a);n+=perft(next,depth-1);}return n;}
    public static void main(String[] args){
        Game mateThreat = empty();
        mateThreat.board.clear();
        mateThreat.board.set(56, new Piece(1, 'K', false));
        mateThreat.board.set(42, new Piece(2, 'K', true));
        mateThreat.board.set(41, new Piece(3, 'Q', true));
        mateThreat.board.set(63, new Piece(4, 'R', false));
        mateThreat.whiteTurn = false;
        var rescueCard = card(mateThreat, CardType.FREEZE);
        var mateHints = chess.ai.TacticalHints.forPosition(mateThreat);
        var waiting = mateHints.stream().filter(h -> h.get("actionId").equals("move:63:62:")).findFirst().orElseThrow();
        check(!waiting.get("mateInOneReplyId").equals(""), "Quiet queen move giving mate is detected");
        var escapeId = "card:" + rescueCard.id() + ":41:-1:";
        var escapeHint = mateHints.stream().filter(h -> h.get("actionId").equals(escapeId)).findFirst().orElseThrow();
        check(escapeHint.get("mateInOneReplyId").equals(""), "Freeze prevents the mating threat before check");
        var mateSafe = chess.ai.TacticalHints.saferActionIds(mateHints);
        check(!mateSafe.contains("move:63:62:") && mateSafe.contains(escapeId), "Card rescue retained and waiting mate excluded");
        // Ферзь d8-d5 подставляется под белого ферзя f3: регрессия действия №26.
        Game blunder = empty();
        blunder.whiteTurn = false;
        blunder.board.set(59, new Piece(3, 'Q', false));
        blunder.board.set(21, new Piece(4, 'Q', true));
        var tactical = chess.ai.TacticalHints.forPosition(blunder);
        var queenLoss = tactical.stream().filter(h -> h.get("actionId").equals("move:59:35:")).findFirst().orElseThrow();
        check(queenLoss.get("worstMaterialDeltaAfterOneReply").equals(-900), "Queen blunder detected before model selection");
        check(queenLoss.get("dangerousReplyId").equals("move:21:35:"), "Queen capture reply identified");
        check(blunder.board.get(59).type() == 'Q' && blunder.version == 0, "Tactical analysis does not mutate live game");
        Game knightBlunder = empty();
        knightBlunder.whiteTurn = false;
        knightBlunder.board.set(35, new Piece(3, 'N', false));
        knightBlunder.board.set(2, new Piece(4, 'B', true));
        var knightHints = chess.ai.TacticalHints.forPosition(knightBlunder);
        var knightLoss = knightHints.stream().filter(h -> h.get("actionId").equals("move:35:29:")).findFirst().orElseThrow();
        check(knightLoss.get("worstMaterialDeltaAfterOneReply").equals(-320), "Nd5-f4 exposes knight to Bc1xf4");
        check(knightLoss.get("dangerousReplyId").equals("move:2:29:"), "Bishop diagonal capture detected");
        var safer = chess.ai.TacticalHints.saferActionIds(knightHints);
        check(!safer.contains("move:35:29:") && !safer.isEmpty(), "Model shortlist excludes avoidable knight loss");
        check(!chess.ai.TacticalHints.saferActionIds(tactical).contains("move:59:35:"), "Model shortlist excludes avoidable queen loss");
        // Исходная позиция и регрессия обычных шахмат: 20, 400 и 8902 позиции.
        Game start=new Game("human",3,"normal");
        check(start.whiteHand.isEmpty()&&start.blackHand.isEmpty(),"Start without cards");
        check(start.deck.size()==30,"Shared 30-card deck");
        check(perft(start,1)==20,"Orthodox perft 1");check(perft(start,2)==400,"Orthodox perft 2");check(perft(start,3)==8902,"Orthodox perft 3");
        start.play("draw");check(!start.whiteTurn&&start.whiteHand.size()==1,"Drawing consumes a turn");

        Game g=empty();g.board.set(8, new Piece(3,'P',true));var boost=card(g,CardType.PAWN_BOOST);cast(g,boost,8);
        check(g.board.get(8)!=null&&g.board.get(8).boost()&&!g.whiteTurn,"Applying boost does not move pawn");
        move(g,60,59);check(hasMove(g,8,32),"Fresh boosted pawn can advance three");
        move(g,8,16);check(!g.board.get(16).boost(),"Even a one-square move consumes boost");
        g=empty();g.board.set(8, new Piece(3,'P',true));g.board.get(8).enableBoost();g.board.set(16, new Piece(4,'N',true));
        check(!hasMove(g,8,24)&&!hasMove(g,8,32),"Pawn cannot jump");
        g=empty();g.board.set(24, new Piece(3,'P',true));g.board.get(24).enableBoost();g.board.set(33, new Piece(4,'R',false));g.board.set(41, new Piece(5,'R',false));
        check(hasMove(g,24,33)&&!hasMove(g,24,41),"Pawn captures only adjacent diagonally");

        g=empty();g.board.get(4).enableBoost();g.board.set(19, new Piece(3,'R',false));
        check(!hasMove(g,4,2),"Boosted king cannot cross attacked intermediate square");
        g.board.set(19, null);check(hasMove(g,4,2),"Boosted king moves two safely");

        g=empty();g.board.set(56, new Piece(3,'R',false));var shield=card(g,CardType.SHIELD);cast(g,shield,4);
        card(g,CardType.ASTRAL);card(g,CardType.FREEZE);card(g,CardType.CLEANSE);
        check(g.legalActions().stream().noneMatch(a->a.kind().equals("card")&&a.to()==4),"Enemy spells cannot target shielded king");
        move(g,60,59);move(g,4,5);check(!g.board.get(5).shield(),"Shield expires on own move");

        g=empty();g.board.set(56, new Piece(3,'R',false));var freeze=card(g,CardType.FREEZE);cast(g,freeze,56);
        check(!hasMove(g,56,48),"Frozen rook cannot move");check(g.attacked(0,false),"Frozen rook still attacks");
        move(g,60,59);check(!g.board.get(56).frozen(),"Freeze ends after owner's next action");

        g=empty();g.board.set(12, new Piece(3,'R',false));var astral=card(g,CardType.ASTRAL);
        check(g.inCheck(true),"Rook gives check");check(g.legalActions().stream().noneMatch(a->a.kind().equals("draw")),"No drawing in check");
        cast(g,astral,12);check(!g.inCheck(true)&&g.board.get(12).astral(),"Astral immediately saves from check");
        var clean=card(g,CardType.CLEANSE);check(g.legalActions().stream().noneMatch(a->a.cardId()==clean.id()&&a.to()==12),"Cannot cleanse astral piece");
        check(((Number)((Map<?, ?>)((List<?>)GameView.of(g).get("board")).stream()
                .filter(p -> ((Map<?, ?>)p).get("square").equals(12)).findFirst().orElseThrow())
                .get("astralTurns")).intValue()==2,"Astral displays two owner turns");
        move(g,60,59);check(g.board.get(12).astral(),"Astral persists after first owner action");
        move(g,4,5);check(g.board.get(12).astral(),"Astral persists during intervening opponent action");
        check(!hasMove(g,12,20),"Astral piece cannot move before second owner action");
        move(g,59,58);check(!g.board.get(12).astral(),"Astral returns after second owner action");

        g=empty();g.board.set(12, new Piece(3,'R',false));g.board.get(12).sendToAstralUntil(1);g.whiteTurn=true;
        check(!hasMove(g,4,5)==false,"Safe king escape remains available");
        var pawn=new Piece(4,'P',true);g.board.set(8, pawn);
        check(!hasMove(g,8,16),"Cannot leave king checked after astral return");

        g=empty();g.board.set(24, new Piece(3,'P',false));var convert=card(g,CardType.CONVERT);cast(g,convert,24);
        check(g.board.get(24).white()&&g.board.get(24).moved(),"Converted pawn changes owner and has moved");move(g,60,59);check(hasMove(g,24,32),"Converted pawn moves in new direction");

        g=empty();g.captured.add(new Piece(3,'P',true));var revive=card(g,CardType.REVIVE_PAWN);cast(g,revive,8);
        check(!g.board.get(8).boost()&&!g.board.get(8).shield()&&!g.board.get(8).moved(),"Revived pawn is ordinary fresh pawn");move(g,60,59);check(hasMove(g,8,24)&&!hasMove(g,8,32),"Revived pawn ordinary double-step only");

        g=empty();g.board.set(8, new Piece(3,'P',true));g.board.get(8).enableBoost();g.board.get(8).protect();g.board.get(8).freezeUntil(10);
        var cleanse=card(g,CardType.CLEANSE);cast(g,cleanse,8);check(!g.board.get(8).boost()&&!g.board.get(8).shield()&&!g.board.get(8).frozen(),"Cleanse removes all effects");

        g=empty();g.board.set(1, new Piece(3,'N',true));g.board.set(2, new Piece(4,'B',true));g.board.get(1).protect();
        var swap=card(g,CardType.SWAP);String swapId=g.legalActions().stream().filter(a->a.cardId()==swap.id()&&a.to()==1&&a.other()==2).findFirst().orElseThrow().id();g.play(swapId);
        check(g.board.get(1).type()=='B'&&g.board.get(2).type()=='N'&&!g.board.get(2).shield(),"Swap relocates and consumes move effects");

        g=new Game("human",1,"normal");card(g,CardType.SHIELD);check(g.legalActions().stream().noneMatch(a->a.id().equals("draw")),"Full hand prevents drawing");
        g=empty();g.discard.add(new Card(8,CardType.SHIELD));g.play("draw");check(g.whiteHand.size()==1&&g.discard.isEmpty(),"Used cards reshuffle when deck is empty");

        g=empty();g.deck.add(new Card(1,CardType.SHIELD));g.deck.add(new Card(2,CardType.FREEZE));var scout=card(g,CardType.SCOUT);g.startScout(scout.id());
        check(g.scouting==scout.id()&&g.whiteHand.isEmpty()&&g.whiteTurn,"Scout commits before revealing");
        check(g.legalActions().size()==2,"Only ordering actions available during scouting");
        g.play(g.legalActions().stream().filter(a->a.option().equals("reverse")).findFirst().orElseThrow().id());
        check(g.deck.get(0).type()==CardType.FREEZE&&!g.whiteTurn,"Scout can reverse order and consumes one turn");

        g=empty();var own=card(g,CardType.SHIELD);var exchange=card(g,CardType.EXCHANGE);g.blackHand.add(new Card(44,CardType.ASTRAL));
        g.play(g.legalActions().stream().filter(a->a.cardId()==exchange.id()).findFirst().orElseThrow().id());
        check(g.whiteHand.get(0).type()==CardType.ASTRAL&&g.blackHand.get(0).id()==own.id()&&g.discard.size()==1,"Blind exchange consumes spell and swaps one card");

        g=empty();g.board.set(0, new Piece(3,'R',true));g.board.set(7, new Piece(4,'R',true));g.wKingCastle=g.wQueenCastle=true;
        check(g.legalActions().stream().filter(a->a.option().equals("castle")).count()==2,"Both castles generated");
        g.play(g.legalActions().stream().filter(a->a.option().equals("castle")&&a.to()==6).findFirst().orElseThrow().id());check(g.board.get(6).type()=='K'&&g.board.get(5).type()=='R',"Castling moves rook and king");

        g=empty();g.board.set(36, new Piece(3,'P',true));g.board.set(51, new Piece(4,'P',false));g.whiteTurn=false;move(g,51,35);check(hasMove(g,36,43),"En passant available");move(g,36,43);check(g.board.get(35)==null&&g.captured.size()==1,"En passant removes passed pawn");
        g=empty();g.board.set(48, new Piece(3,'P',true));check(g.legalActions().stream().filter(a->a.from()==48&&a.to()==56).count()==4,"Four promotion choices");

        g=empty();g.board.clear();g.board.set(0, new Piece(1,'K',true));g.board.set(18, new Piece(2,'K',false));g.board.set(9, new Piece(3,'Q',false));var rescue=card(g,CardType.ASTRAL);
        check(g.inCheck(true)&&g.legalActions().stream().noneMatch(a->a.kind().equals("move")),"Orthodox mate has no piece escape");g.refreshResult();check(g.result.isEmpty(),"Held rescue card prevents mate");cast(g,rescue,9);check(!g.inCheck(true),"Card escapes orthodox mate");
        g=empty();g.board.clear();g.board.set(0, new Piece(1,'K',true));g.board.set(18, new Piece(2,'K',false));g.board.set(9, new Piece(3,'Q',false));g.refreshResult();check(g.result.equals("black"),"Without rescue card mate is final");

        Game ai=new Game("ai",3,"normal");move(ai,12,28);String chosen=Ai.choose(ai);check(ai.legalActions().stream().anyMatch(a->a.id().equals(chosen)),"AI returns legal action");
        Game hidden=ai.copy();hidden.whiteHand.add(new Card(400,CardType.CONVERT));Game hidden2=hidden.copy();hidden2.whiteHand.set(0,new Card(400,CardType.SHIELD));Collections.reverse(hidden2.deck);
        check(Ai.choose(hidden).equals(Ai.choose(hidden2)),"AI choice independent of hidden card identities and deck order");
        Object roundtrip=Json.object(Json.stringify(Map.of("text","Шах\n\"мат\"","list",List.of(1,true)))).get("text");check(roundtrip.equals("Шах\n\"мат\""),"JSON Unicode and escaping");
        System.out.println("Passed "+checks+" checks, including perft 20 / 400 / 8902.");
    }
}
