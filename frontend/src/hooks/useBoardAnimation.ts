import { useEffect, useLayoutEffect, useRef } from 'react';
import type { BoardSize, Piece } from '../game/types';

type Snapshot = { layout: string; gameId?: string; pieces: Map<number, { piece: Piece; image: HTMLElement }> };

/** Animate visual copies only; legal targets and authoritative positions update immediately. */
export function useBoardAnimation(board: Piece[], size: BoardSize, flipped: boolean, gameId?: string) {
    const root = useRef<HTMLDivElement>(null);
    const previous = useRef<Snapshot | null>(null);
    const running = useRef(new Set<Animation>());
    useEffect(() => () => { running.current.forEach(animation => animation.cancel()); running.current.clear(); }, []);
    useLayoutEffect(() => {
        const element = root.current;
        if (!element) return;
        const layout = `${size.width}:${size.height}:${size.originFile}:${size.originRank}:${flipped}`;
        const old = previous.current;
        if (old && (old.layout !== layout || old.gameId !== gameId)) {
            running.current.forEach(animation => animation.cancel()); running.current.clear();
        }
        const current = new Map<number, { piece: Piece; image: HTMLElement }>();
        const animate = (node: HTMLElement, frames: Keyframe[], duration: number, remove = false) => {
            const animation = node.animate(frames, { duration, easing: 'cubic-bezier(.2,.7,.2,1)' });
            running.current.add(animation);
            const finish = () => { running.current.delete(animation); if (remove) node.remove(); };
            animation.finished.then(finish, finish);
        };
        const cell = (square: number) => element.querySelector<HTMLElement>(`[data-square="${square}"]`);
        const bounds = element.getBoundingClientRect();
        const overlay = (square: number, image?: HTMLElement) => {
            const rect = cell(square)?.getBoundingClientRect();
            if (!rect) return null;
            const node = image ? image.cloneNode(true) as HTMLElement : document.createElement('span');
            node.className = 'board-effect'; node.setAttribute('aria-hidden', 'true');
            Object.assign(node.style, { left: `${rect.left - bounds.left}px`, top: `${rect.top - bounds.top}px`, width: `${rect.width}px`, height: `${rect.height}px` });
            element.appendChild(node);
            return node;
        };
        const burst = (square: number, trap: boolean) => {
            const ring = overlay(square);
            if (!ring) return;
            ring.classList.add(trap ? 'trap-snap' : 'frost-burst');
            animate(ring, [{ opacity: 1, transform: 'scale(.35)' }, { opacity: 0, transform: 'scale(1.25)' }], 650, true);
            for (let i = 0; i < 9; i++) {
                const particle = overlay(square);
                if (!particle) continue;
                particle.classList.add('spell-particle', trap ? 'trap-particle' : 'ice-particle');
                const angle = i * Math.PI * 2 / 9;
                const distance = 20 + i % 3 * 12;
                animate(particle, [
                    { opacity: 0, transform: 'translate(0, 0) scale(.2)' },
                    { opacity: 1, offset: .2 },
                    { opacity: 0, transform: `translate(${Math.cos(angle) * distance}px, ${Math.sin(angle) * distance}px) rotate(${i * 43}deg) scale(.5)` },
                ], 600 + i % 3 * 90, true);
            }
        };
        const enabled = old && old.layout === layout && old.gameId === gameId
            && !window.matchMedia('(prefers-reduced-motion: reduce)').matches && typeof element.animate === 'function';
        for (const piece of board) {
            const node = element.querySelector<HTMLElement>(`[data-piece-id="${piece.id}"]`);
            if (!node) continue;
            const before = old?.pieces.get(piece.id);
            if (enabled) {
                if (before && before.piece.square !== piece.square) {
                    const from = cell(before.piece.square)?.getBoundingClientRect();
                    const to = node.getBoundingClientRect();
                    if (from) {
                        node.classList.add('piece-travelling');
                        animate(node, [{ transform: `translate(${from.left - to.left}px, ${from.top - to.top}px)` }, { transform: 'translate(0, 0)' }], 300);
                        node.getAnimations().at(-1)?.finished.then(() => node.classList.remove('piece-travelling'), () => node.classList.remove('piece-travelling'));
                    }
                } else if (!before) {
                    animate(node, [{ opacity: 0, transform: 'scale(.65)' }, { opacity: 1, transform: 'scale(1)' }], 380);
                }
                if (before && !before.piece.frozen && piece.frozen) burst(piece.square, before.piece.square !== piece.square);
                if (before && !before.piece.shield && piece.shield) {
                    const ring = overlay(piece.square);
                    if (ring) { ring.classList.add('shield-arrival'); animate(ring, [{ opacity: 0, transform: 'scale(.5)' }, { opacity: 1, offset: .3 }, { opacity: 0, transform: 'scale(1.25)' }], 650, true); }
                }
                if (before && !before.piece.astral && piece.astral) {
                    const solid = overlay(piece.square, before.image);
                    if (solid) animate(solid, [{ opacity: 1, filter: 'blur(0)', transform: 'scale(1)' }, { opacity: 0, filter: 'blur(7px)', transform: 'scale(1.18) translateY(-8px)' }], 700, true);
                }
                if (before?.piece.astral && !piece.astral) animate(node, [{ opacity: .15, filter: 'blur(6px)' }, { opacity: 1, filter: 'blur(0)' }], 450);
            }
            current.set(piece.id, { piece: { ...piece }, image: node.cloneNode(true) as HTMLElement });
        }
        if (enabled) for (const [id, before] of old.pieces) {
            if (current.has(id)) continue;
            const ghost = overlay(before.piece.square, before.image);
            if (ghost) animate(ghost, [{ opacity: .9, transform: 'scale(1)' }, { opacity: 0, transform: 'scale(.55)', filter: 'blur(5px)' }], 340, true);
        }
        previous.current = { layout, gameId, pieces: current };
    }, [board, size.width, size.height, size.originFile, size.originRank, flipped, gameId]);
    return root;
}
