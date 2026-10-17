package chess.cards;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Неизменяемый реестр: Spring находит аннотированные классы, каждая карта создаётся заново по id.
 */
public final class CardRegistry {
    private record Entry(Constructor<? extends Card> constructor, int copies) {
    }

    private final Map<CardType, Entry> entries;

    public CardRegistry(Collection<Class<? extends Card>> classes) {

        var found = new EnumMap<CardType, Entry>(CardType.class);

        for (var cardClass : classes) {
            CardDefinition definition = cardClass.getDeclaredAnnotation(CardDefinition.class);

            if (definition == null || Modifier.isAbstract(cardClass.getModifiers()))
                throw new IllegalStateException("Нужен конкретный класс с @CardDefinition: " + cardClass.getName());

            if (definition.freezeTurns() < 0 || definition.freezeTurns() > 100 || definition.durationTurns() < 0 || definition.durationTurns() > Integer.MAX_VALUE / 2
                    || definition.copies() < 0 || definition.type() == CardType.UNKNOWN && definition.copies() != 0)
                throw new IllegalStateException("Некорректные параметры карты: " + cardClass.getName());

            try {
                var entry = new Entry(cardClass.getConstructor(int.class), definition.copies());

                if (found.putIfAbsent(definition.type(), entry) != null)
                    throw new IllegalStateException("Повторная регистрация карты: " + definition.type());

                Card sample = instantiate(entry, 0);

                if (sample.type() != definition.type() || sample.id() != 0 || sample.name().isBlank())
                    throw new IllegalStateException("Аннотация и конструктор карты не согласованы: " + cardClass.getName());

            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Карта должна иметь public-конструктор (int id): " + cardClass.getName(), e);
            }
        }

        var missing = EnumSet.allOf(CardType.class);
        missing.removeAll(found.keySet());

        if (!missing.isEmpty())
            throw new IllegalStateException("Не зарегистрированы карты: " + missing);

        entries = Collections.unmodifiableMap(found);
    }

    private static final class DefaultHolder {
        private static final CardRegistry INSTANCE = scan("chess.cards.impl");
    }

    public static CardRegistry defaultRegistry() {
        return DefaultHolder.INSTANCE;
    }

    public static CardRegistry scan(String basePackage) {

        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent();
            }
        };

        scanner.addIncludeFilter(new AnnotationTypeFilter(CardDefinition.class));

        var classes = new ArrayList<Class<? extends Card>>();

        for (var candidate : scanner.findCandidateComponents(basePackage)) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), ClassUtils.getDefaultClassLoader());
            if (!Card.class.isAssignableFrom(type))
                throw new IllegalStateException("@CardDefinition допустима только для Card: " + type.getName());
            classes.add(type.asSubclass(Card.class));
        }
        return new CardRegistry(classes);
    }

    public Card create(int id, CardType type) {
        return instantiate(Objects.requireNonNull(entries.get(Objects.requireNonNull(type))), id);
    }

    public List<Card> newDeck() {
        var cards = new ArrayList<Card>();
        int id = 1;
        for (var entry : entries.values())
            for (int n = 0; n < entry.copies(); n++)
                cards.add(instantiate(entry, id++));
        Collections.shuffle(cards);
        return cards;
    }

    private static Card instantiate(Entry entry, int id) {
        try {
            return entry.constructor().newInstance(id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось создать карту: " + entry.constructor().getDeclaringClass().getName(), e);
        }
    }
}
