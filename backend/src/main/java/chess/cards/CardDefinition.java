package chess.cards;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Регистрирует класс карты и количество её экземпляров в общей колоде.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CardDefinition {
    CardType type();

    int copies();

    /** Число действий владельца цели; 0 означает эффект без фиксированного срока. */
    int durationTurns() default 0;

    /** Длительность заморозки при входе в капкан. */
    int freezeTurns() default 0;
}
