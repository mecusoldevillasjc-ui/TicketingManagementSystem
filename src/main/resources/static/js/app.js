/* Ticketing Management System: theme, page transitions, count-up, tilt, ripple, toasts, live search, charts, kiosk idle sign-out */
(() => {
    const $ = (s, r = document) => r.querySelector(s);
    const $$ = (s, r = document) => [...r.querySelectorAll(s)];
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
    const page = $('.page, .pub-main');
    const TMS = window.TMS = {};
    const frame = fn => requestAnimationFrame(() => requestAnimationFrame(fn));

    /* ---- theme (dark / light) ---- */
    const toggle = $('#themeToggle');
    const setTheme = t => {
        document.documentElement.dataset.theme = t;
        try { localStorage.setItem('tms-theme', t); } catch (e) { /* private mode */ }
        if (toggle) toggle.innerHTML = t === 'dark' ? '<i class="fa-solid fa-moon"></i>' : '<i class="fa-solid fa-sun"></i>';
    };
    if (toggle) {
        setTheme(document.documentElement.dataset.theme || 'dark');
        toggle.addEventListener('click', () => setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'));
    }

    /* ---- modals: <button data-modal="id"> opens <dialog id="id">; the X, Esc and the dim background close it ---- */
    document.addEventListener('click', e => {
        const m = e.target.closest('[data-modal]');
        if (m) { const d = document.getElementById(m.dataset.modal); if (d && !d.open) d.showModal(); return; }
        const x = e.target.closest('[data-close]');
        if (x) { x.closest('dialog').close(); return; }
        if (e.target instanceof HTMLDialogElement) e.target.close();
    });
    if (new URLSearchParams(location.search).get('open') === 'submit') { // set by the server after a failed submit
        const d = document.getElementById('submitModal');
        if (d) { d.showModal(); history.replaceState(null, '', location.pathname); }
    }

    /* ---- sidebar: the menu button collapses it on desktop and opens it as a drawer on phones ---- */
    const navBtn = $('#navToggle');
    if (navBtn) {
        const small = matchMedia('(max-width: 900px)');
        try { if (!small.matches && localStorage.getItem('tms-nav') === 'collapsed') document.body.classList.add('nav-collapsed'); } catch (e) { /* ignore */ }
        navBtn.addEventListener('click', () => {
            if (small.matches) { document.body.classList.toggle('nav-open'); return; }
            const c = document.body.classList.toggle('nav-collapsed');
            try { localStorage.setItem('tms-nav', c ? 'collapsed' : 'open'); } catch (e) { /* ignore */ }
        });
        document.addEventListener('click', e => {
            if (document.body.classList.contains('nav-open') && !e.target.closest('.sidebar') && !e.target.closest('#navToggle')) document.body.classList.remove('nav-open');
        });
    }

    /* ---- dashboard: a status box shows its tickets in the table right below it ---- */
    $$('[data-inline]').forEach(root => {
        const boxes = $$('[data-show]'), latest = root.dataset.latest === 'true';
        const empty = $('[data-inline-empty]', root), panel = $('[data-inline-panel]', root);
        const title = $('[data-inline-title]', root), count = $('[data-inline-count]', root), chip = $('[data-inline-chip]', root);
        const all = $('[data-inline-all]', root), none = $('[data-inline-none]', root);
        const foot = $('[data-inline-foot]', root), info = $('[data-inline-info]', root), more = $('[data-inline-more]', root);
        const rows = [...$('tbody', root).rows];
        const NAME = { OPEN: 'Open Tickets', IN_PROGRESS: 'In Progress Tickets', RESOLVED: 'Resolved Tickets', CLOSED: 'Closed Tickets' };
        const LABEL = { OPEN: 'Open', IN_PROGRESS: 'In Progress', RESOLVED: 'Resolved', CLOSED: 'Closed' };
        const CLS = { OPEN: 'st-open', IN_PROGRESS: 'st-in-progress', RESOLVED: 'st-resolved', CLOSED: 'st-closed' };
        let active = '';

        const show = st => {
            active = st;
            boxes.forEach(b => b.classList.toggle('active', b.dataset.show === st));
            if (!st && !latest) { empty.hidden = false; panel.hidden = true; return; }
            empty.hidden = true; panel.hidden = false;
            const list = rows.filter(r => !st || r.dataset.status === st);
            const shown = list.slice(0, st ? 10 : 5);
            rows.forEach(r => { r.hidden = !shown.includes(r); });
            title.textContent = st ? NAME[st] : 'Latest tickets';
            count.textContent = st ? `${list.length} ${list.length === 1 ? 'ticket' : 'tickets'} found` : `${rows.length} ${rows.length === 1 ? 'ticket' : 'tickets'}`;
            chip.hidden = !st;
            if (st) { chip.className = 'chip ' + CLS[st]; chip.textContent = LABEL[st]; }
            all.hidden = !!st || !latest;
            none.hidden = list.length > 0;
            const extra = list.length - shown.length;
            foot.hidden = extra <= 0 || !st;
            if (st && extra > 0) { info.textContent = `Showing ${shown.length} of ${list.length}`; more.href = '/tickets?status=' + st; }
            if (st && !reduce) panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        };
        // clicking a box shows its tickets; clicking the same box again closes it
        boxes.forEach(b => b.addEventListener('click', e => { e.preventDefault(); show(active === b.dataset.show ? '' : b.dataset.show); }));
        show('');
    });

    /* ---- ticket data table: search, status filter, sort and paging, done in the browser on the rows the server sent ---- */
    $$('[data-dt]').forEach(root => {
        const tbody = $('tbody', root), rows = [...tbody.rows], size = +(root.dataset.pageSize || 10);
        const search = $('[data-dt-search]', root), btns = $$('[data-dt-status]', root);
        const title = $('[data-dt-title]', root), count = $('[data-dt-count]', root), info = $('[data-dt-info]', root);
        const pager = $('[data-dt-pager]', root), none = $('[data-dt-empty]', root), heads = $$('th', root);
        const names = { '': 'All tickets', OPEN: 'Open tickets', IN_PROGRESS: 'In progress tickets', RESOLVED: 'Resolved tickets', CLOSED: 'Closed tickets' };
        let status = root.dataset.status || '', q = '', page = 1, col = -1, dir = 1;

        const key = (r, i) => { const c = r.cells[i], v = c.dataset.v !== undefined ? c.dataset.v : c.textContent.trim().toLowerCase(); return isNaN(v) || v === '' ? v : +v; };
        const render = () => {
            let list = rows.filter(r => (!status || r.dataset.status === status) && (!q || r.dataset.search.includes(q)));
            if (col >= 0) list.sort((a, b) => { const x = key(a, col), y = key(b, col); return (x > y ? 1 : x < y ? -1 : 0) * dir; });
            const pages = Math.max(1, Math.ceil(list.length / size));
            page = Math.min(page, pages);
            const from = (page - 1) * size, shown = list.slice(from, from + size);
            list.forEach(r => tbody.appendChild(r));
            rows.forEach(r => { r.hidden = !shown.includes(r); });
            btns.forEach(b => { const on = b.dataset.dtStatus === status; b.classList.toggle('active', on); b.setAttribute('aria-pressed', on); });
            if (title) title.textContent = names[status] || 'Tickets';
            if (count) count.textContent = list.length + (list.length === 1 ? ' ticket' : ' tickets');
            if (none) none.hidden = list.length > 0;
            if (info) info.textContent = list.length ? `Showing ${from + 1} to ${from + shown.length} of ${list.length}` : '';
            if (pager) {
                pager.innerHTML = '';
                if (pages > 1) {
                    const add = (label, to, opts = {}) => { const b = document.createElement('button'); b.type = 'button'; b.textContent = label; b.disabled = !!opts.off; if (opts.on) b.classList.add('active'); b.addEventListener('click', () => { page = to; render(); }); pager.appendChild(b); };
                    add('Prev', page - 1, { off: page === 1 });
                    for (let i = Math.max(1, page - 2); i <= Math.min(pages, page + 2); i++) add(i, i, { on: i === page });
                    add('Next', page + 1, { off: page === pages });
                }
            }
        };
        btns.forEach(b => b.addEventListener('click', () => {
            status = b.dataset.dtStatus; page = 1;
            history.replaceState(null, '', location.pathname + '?status=' + (status || 'ALL'));
            render();
        }));
        if (search) search.addEventListener('input', () => { q = search.value.trim().toLowerCase(); page = 1; render(); });
        heads.forEach((h, i) => {
            if (!h.hasAttribute('data-sort')) return;
            h.addEventListener('click', () => {
                dir = col === i ? -dir : 1; col = i;
                heads.forEach(x => x.removeAttribute('aria-sort'));
                h.setAttribute('aria-sort', dir === 1 ? 'ascending' : 'descending');
                render();
            });
        });
        render();
    });

    /* ---- smooth page transitions ---- */
    const leave = go => { if (!page || reduce) return go(); page.classList.add('leaving'); setTimeout(go, 190); };
    document.addEventListener('click', e => {
        const a = e.target.closest('a[href]');
        if (!a || e.defaultPrevented || e.button || e.metaKey || e.ctrlKey || e.shiftKey || a.target || a.hasAttribute('download')) return;
        const u = new URL(a.href, location.href);
        if (u.origin !== location.origin || (u.pathname === location.pathname && u.search === location.search)) return;
        e.preventDefault();
        leave(() => { location.href = a.href; });
    });
    document.addEventListener('submit', e => {
        const f = e.target;
        if (e.defaultPrevented) return;
        if (f.dataset.confirm && !confirm(f.dataset.confirm)) { e.preventDefault(); return; }
        if (page && !reduce) page.classList.add('leaving');
    });
    addEventListener('pageshow', () => page && page.classList.remove('leaving')); // back button

    /* ---- count-up numbers ---- */
    $$('[data-count]').forEach(el => {
        const target = parseFloat(el.dataset.count) || 0, dec = +(el.dataset.dec || 0);
        const t0 = performance.now(), dur = reduce ? 1 : 1200;
        (function tick(now) {
            const p = Math.min((now - t0) / dur, 1);
            el.textContent = (target * (1 - Math.pow(1 - p, 3))).toFixed(dec);
            if (p < 1) requestAnimationFrame(tick);
        })(t0);
    });

    /* ---- tilt on KPI cards ---- */
    if (!reduce) $$('.tilt').forEach(el => {
        el.addEventListener('mousemove', e => {
            const r = el.getBoundingClientRect(), x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
            el.style.transform = `perspective(800px) rotateX(${-y * 7}deg) rotateY(${x * 9}deg) translateY(-4px)`;
        });
        el.addEventListener('mouseleave', () => { el.style.transform = ''; });
    });

    /* ---- button ripple ---- */
    document.addEventListener('pointerdown', e => {
        const b = e.target.closest('.btn');
        if (!b || reduce) return;
        const r = b.getBoundingClientRect(), s = Math.max(r.width, r.height), d = document.createElement('span');
        d.className = 'ripple';
        d.style.cssText = `width:${s}px;height:${s}px;left:${e.clientX - r.left - s / 2}px;top:${e.clientY - r.top - s / 2}px`;
        b.appendChild(d);
        setTimeout(() => d.remove(), 650);
    });

    /* ---- toasts ---- */
    const dismiss = (t, ms = 4500) => setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 450); }, ms);
    TMS.toast = (msg, type = 'ok') => {
        const t = document.createElement('div');
        t.className = 'toast ' + type;
        t.innerHTML = `<i class="fa-solid ${type === 'err' ? 'fa-circle-exclamation' : 'fa-circle-check'}"></i><span></span>`;
        t.lastChild.textContent = msg;
        document.body.appendChild(t);
        dismiss(t);
    };
    $$('.toast').forEach(t => dismiss(t));

    /* ---- filters: auto-submit and instant search ---- */
    $$('[data-autosubmit]').forEach(el => el.addEventListener('change', () => el.form.requestSubmit()));
    const ls = $('#liveSearch');
    if (ls) {
        const rows = $$('.tk-row[data-search]'), none = $('#noMatch'), count = $('#resultCount');
        const run = () => {
            const q = ls.value.trim().toLowerCase();
            let n = 0;
            rows.forEach(r => { const ok = !q || r.dataset.search.includes(q); r.style.display = ok ? '' : 'none'; if (ok) n++; });
            if (none) none.hidden = n > 0 || !rows.length;
            if (count) count.textContent = n + (n === 1 ? ' ticket' : ' tickets');
        };
        ls.addEventListener('input', run);
        run();
    }

    /* ---- charts (pure SVG / CSS, no library) ---- */
    TMS.animate = () => frame(() => {
        $$('.hb-track i[data-w]').forEach((b, i) => setTimeout(() => { b.style.width = b.dataset.w + '%'; }, i * 80));
        $$('.vbar .col-bar[data-h]').forEach((c, i) => setTimeout(() => { c.style.height = c.dataset.h + 'px'; }, i * 70));
        $$('.donut .seg[data-len]').forEach(s => { s.style.strokeDasharray = `${Math.max(+s.dataset.len - 3, 0)} ${s.dataset.c}`; });
    });

    TMS.donut = (el, data, colors, unit = 'tickets') => {
        if (!el) return;
        const entries = Object.entries(data), total = entries.reduce((a, [, v]) => a + v, 0), R = 62, C = 2 * Math.PI * R;
        let acc = 0, segs = '';
        entries.forEach(([k, v], i) => {
            const len = total ? v / total * C : 0;
            segs += `<circle class="seg" r="${R}" cx="80" cy="80" stroke="${colors[i % colors.length]}" style="stroke-dasharray:0 ${C}" stroke-dashoffset="${-acc}" data-len="${len}" data-c="${C}"><title>${k}: ${v}</title></circle>`;
            acc += len;
        });
        el.innerHTML = `<div class="donut-wrap"><div class="donut"><svg viewBox="0 0 160 160"><circle class="track-c" r="${R}" cx="80" cy="80"/>${segs}</svg>
            <div class="center"><b>${total}</b><small>${unit}</small></div></div>
            <div class="legend">${entries.map(([k, v], i) => `<div><i style="background:${colors[i % colors.length]}"></i><span>${k}</span><span>${v}</span></div>`).join('')}</div></div>`;
        TMS.animate();
    };

    TMS.bars = (el, data) => {
        if (!el) return;
        const e = Object.entries(data), max = Math.max(1, ...e.map(([, v]) => v));
        el.innerHTML = e.map(([k, v]) => `<div class="vbar"><div class="col-bar" data-h="${v ? Math.max(8, v / max * 130) : 3}"><em>${v}</em></div><small>${k}</small></div>`).join('');
        TMS.animate();
    };

    TMS.hbars = (el, data, colors) => {
        if (!el) return;
        const e = Object.entries(data), max = Math.max(1, ...e.map(([, v]) => v));
        el.innerHTML = e.map(([k, v], i) => `<div class="hb-row" style="--c:${colors[i % colors.length]}"><span>${k}</span><div class="hb-track"><i data-w="${v / max * 100}"></i></div><b>${v}</b></div>`).join('');
        TMS.animate();
    };
    TMS.animate(); // server-rendered bars

    /* ---- kiosk safety: sign out after inactivity (set data-idle="seconds" on <body>) ---- */
    const idle = +(document.body.dataset.idle || 0), out = $('#logoutForm');
    if (idle && out) {
        let warn, kill;
        const arm = () => {
            clearTimeout(warn); clearTimeout(kill);
            warn = setTimeout(() => TMS.toast('Still there? You will be signed out in 20 seconds.', 'err'), Math.max(idle - 20, 1) * 1000);
            kill = setTimeout(() => out.requestSubmit(), idle * 1000);
        };
        ['pointerdown', 'keydown', 'touchstart', 'scroll'].forEach(ev => addEventListener(ev, arm, { passive: true }));
        arm();
    }

    /* ---- copy button (temporary password) ---- */
    $$('[data-copy]').forEach(b => b.addEventListener('click', () => {
        navigator.clipboard.writeText(b.dataset.copy).then(() => TMS.toast('Copied to clipboard.'));
    }));

    /* ---- reveal-on-scroll (homepage) ---- */
    const rv = $$('.reveal');
    if (rv.length) {
        if (!('IntersectionObserver' in window) || reduce) rv.forEach(e => e.classList.add('in'));
        else {
            const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: .15 });
            rv.forEach(e => io.observe(e));
        }
    }

    /* ---- kiosk: one question per screen, big targets, resets itself for the next person ---- */
    const kf = $('#kioskForm');
    if (kf) {
        const steps = $$('.k-step', kf), dots = $$('.k-dots i'), err = $('#kErr');
        const msgs = ['Please choose what kind of problem it is.', 'Please choose how urgent it is.',
                      'Please add a short title and describe what happened.', 'Please tell us your name so we know who to help.'];
        let i = 0;
        const show = n => {
            i = n;
            steps.forEach((s, k) => s.classList.toggle('active', k === n));
            dots.forEach((d, k) => d.classList.toggle('done', k <= n));
            err.textContent = '';
            window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' });
        };
        const valid = n => {
            if (n === 0) return !!kf.querySelector('[name=category]:checked');
            if (n === 1) return !!kf.querySelector('[name=priority]:checked');
            if (n === 2) return kf.elements['subject'].value.trim() && kf.elements['description'].value.trim();
            if (n === 3) return kf.elements['visitorName'].value.trim();
            return true;
        };
        $$('[data-next]', kf).forEach(b => b.addEventListener('click', () => { if (!valid(i)) { err.textContent = msgs[i]; return; } show(i + 1); }));
        $$('[data-back]', kf).forEach(b => b.addEventListener('click', () => show(i - 1)));
        // picking a tile moves on by itself: fewer taps for a walk-up user
        $$('.k-tiles input', kf).forEach(r => r.addEventListener('change', () => setTimeout(() => { if (i < 2) show(i + 1); }, 260)));
        kf.addEventListener('submit', e => { if (!valid(3)) { e.preventDefault(); err.textContent = msgs[3]; } });
        show(0);
        let t;
        const rearm = () => { clearTimeout(t); t = setTimeout(() => location.reload(), 120000); };
        ['pointerdown', 'keydown', 'touchstart'].forEach(ev => addEventListener(ev, rearm, { passive: true }));
        rearm();
    }

    /* ---- kiosk "done" screen: go back to the start by itself ---- */
    const cd = $('#countdown');
    if (cd) {
        let n = +cd.textContent || 30;
        const tick = setInterval(() => { n--; cd.textContent = n; if (n <= 0) { clearInterval(tick); location.href = '/kiosk'; } }, 1000);
    }

})();
