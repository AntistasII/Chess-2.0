/* Local asynchronous engine calls. No listening socket and no remote game server. */
(() => {
    const originalFetch = window.fetch.bind(window);
    const pending = new Map();
    let sequence = 0;
    window.__resolveAndroidGame = (id, result) => {
        const entry = pending.get(id);
        if (!entry) return;
        pending.delete(id);
        clearTimeout(entry.timer);
        entry.resolve(new Response(JSON.stringify(result.body), {
            status: result.status, headers: { 'Content-Type': 'application/json' },
        }));
    };
    window.fetch = (input, options = {}) => {
        const url = new URL(typeof input === 'string' ? input : input.url, location.href);
        if (url.origin !== location.origin || !url.pathname.startsWith('/api/')) return originalFetch(input, options);
        return new Promise((resolve, reject) => {
            const id = ++sequence;
            const timer = setTimeout(() => { pending.delete(id); reject(new Error('Не удалось выполнить действие. Повторите попытку.')); }, 120000);
            pending.set(id, {resolve, reject, timer});
            AndroidGame.request(JSON.stringify({id, path: url.pathname, body: options.body ? JSON.parse(options.body) : {}}));
        });
    };
})();
