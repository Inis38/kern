(function () {
    const config = window.KERN || { refreshIntervalMs: 5000 };
    const apiUrl = '/api/v1/monitoring';

    function setBar(id, percent) {
        const el = document.getElementById(id);
        if (el) el.style.width = Math.min(100, Math.max(0, percent)) + '%';
    }

    function setText(id, text) {
        const el = document.getElementById(id);
        if (el) el.textContent = text;
    }

    function formatTime(iso) {
        try {
            return new Date(iso).toLocaleTimeString('ru-RU');
        } catch {
            return '—';
        }
    }

    function renderHealth(health) {
        const card = document.getElementById('health-card');
        if (card) {
            card.className = 'status-card status-card--' + health.level;
        }
        setText('health-message', health.message);

        const list = document.getElementById('health-checks');
        if (list) {
            list.innerHTML = health.checks
                .map(function (c) {
                    return '<li class="check check--' + c.level + '">' +
                        c.name + ': ' + c.message + '</li>';
                })
                .join('');
        }
    }

    function renderDisks(disks) {
        const container = document.getElementById('disk-list');
        if (!container) return;

        container.innerHTML = disks
            .map(function (d) {
                return (
                    '<article class="disk-card">' +
                    '<div class="disk-card__header">' +
                    '<span class="disk-card__name">' + escapeHtml(d.name) + '</span>' +
                    '<span class="disk-card__mount">' + escapeHtml(d.mount) + '</span>' +
                    '<span class="disk-card__percent">' + d.percent + '%</span>' +
                    '</div>' +
                    '<div class="progress progress--sm">' +
                    '<div class="progress__bar progress__bar--disk" style="width:' + d.percent + '%"></div>' +
                    '</div>' +
                    '<p class="disk-card__hint">' + escapeHtml(d.usedFormatted) + ' / ' + escapeHtml(d.totalFormatted) + '</p>' +
                    '</article>'
                );
            })
            .join('');
    }

    function renderLoad(load) {
        const card = document.getElementById('load-card');
        if (!load) {
            if (card) card.classList.add('metric-card--hidden');
            return;
        }
        if (card) card.classList.remove('metric-card--hidden');
        setText('load-1', load.one);
        setText('load-5', load.five);
        setText('load-15', load.fifteen);
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }

    function applyData(data) {
        setText('last-updated', 'Обновлено: ' + formatTime(data.timestamp));
        setText('hostname', data.host.hostname);
        setText('uptime', data.uptimeFormatted);
        setText('info-os', data.host.os);
        setText('info-arch', data.host.arch);

        renderHealth(data.health);
        setText('cpu-value', data.cpu.percent + '%');
        setBar('cpu-bar', data.cpu.percent);
        setText('memory-value', data.memory.percent + '%');
        setBar('memory-bar', data.memory.percent);
        setText('memory-hint', data.memory.usedFormatted + ' / ' + data.memory.totalFormatted);

        renderLoad(data.loadAverage);
        renderDisks(data.disks);
    }

    async function refresh() {
        try {
            const res = await fetch(apiUrl);
            if (!res.ok) return;
            const data = await res.json();
            applyData(data);
        } catch (e) {
            console.warn('Monitoring refresh failed', e);
        }
    }

    refresh();
    setInterval(refresh, config.refreshIntervalMs);
})();
