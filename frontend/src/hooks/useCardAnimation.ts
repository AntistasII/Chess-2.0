import { useEffect, useLayoutEffect, useRef } from 'react';
import type { State } from '../game/types';

type VisibleHand = { gameId?: string; white: boolean; cards: Map<number, { image: HTMLElement; rect: DOMRect }> };

export function useCardAnimation(game: State, covered: boolean) {
    const root = useRef<HTMLElement>(null);
    const previous = useRef<VisibleHand | null>(null);
    const running = useRef(new Set<Animation>());
    useEffect(() => () => { running.current.forEach(a => a.cancel()); running.current.clear(); }, []);
    useLayoutEffect(() => {
        const element = root.current;
        if (!element || covered) {
            running.current.forEach(animation => animation.cancel()); running.current.clear();
            previous.current = null; return;
        }
        const before = previous.current;
        if (before && (before.gameId !== game.gameId || before.white !== game.handWhite)) {
            running.current.forEach(animation => animation.cancel()); running.current.clear();
        }
        const cards = new Map<number, { image: HTMLElement; rect: DOMRect }>();
        const enabled = before && before.gameId === game.gameId && before.white === game.handWhite
            && !window.matchMedia('(prefers-reduced-motion: reduce)').matches && typeof element.animate === 'function';
        const play = (node: HTMLElement, frames: Keyframe[], duration: number, remove = false) => {
            const animation = node.animate(frames, { duration, easing: 'cubic-bezier(.2,.7,.2,1)' });
            running.current.add(animation);
            const finish = () => { running.current.delete(animation); if (remove) node.remove(); };
            animation.finished.then(finish, finish);
        };
        for (const card of game.hand) {
            const node = element.querySelector<HTMLElement>(`[data-card-id="${card.id}"]`);
            if (!node) continue;
            if (enabled && !before.cards.has(card.id)) play(node, [
                { opacity: 0, transform: 'perspective(600px) translateY(20px) rotateY(-55deg) scale(.85)' },
                { opacity: 1, transform: 'perspective(600px) translateY(0) rotateY(0) scale(1)' },
            ], 440);
            cards.set(card.id, { image: node.cloneNode(true) as HTMLElement, rect: node.getBoundingClientRect() });
        }
        if (enabled) for (const [id, card] of before.cards) {
            if (cards.has(id)) continue;
            const ghost = card.image;
            ghost.classList.add('card-departure');
            ghost.setAttribute('aria-hidden', 'true'); ghost.setAttribute('inert', ''); ghost.removeAttribute('data-card-id');
            Object.assign(ghost.style, { left: `${card.rect.left}px`, top: `${card.rect.top}px`, width: `${card.rect.width}px`, height: `${card.rect.height}px` });
            document.body.appendChild(ghost);
            play(ghost, [{ opacity: .95, transform: 'translateY(0) scale(1)' }, { opacity: 0, transform: 'translateY(-40px) scale(.7)', filter: 'blur(3px)' }], 420, true);
        }
        previous.current = { gameId: game.gameId, white: game.handWhite, cards };
    }, [game.hand, game.gameId, game.handWhite, covered]);
    return root;
}
