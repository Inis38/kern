(function () {
    const config = window.KERN_SECURITY || { apiUrl: '/api/v1/security', fetchTimeoutMs: 30000 };

    function setText(id, text) {
        const el = document.getElementById(id);
        if (el) el.textContent = text;
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }

    function formatEventTime(iso) {
        try {
            const d = new Date(iso);
            return d.toLocaleString('ru-RU', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
            });
        } catch {
            return iso;
        }
    }

    function severityTagClass(severity) {
        if (severity === 'critical') return 'tag--critical';
        if (severity === 'warning') return 'tag--warn';
        return '';
    }

    function renderStatus(data) {
        const card = document.getElementById('security-status-card');
        if (card) {
            card.className = 'status-card status-card--' + data.statusLevel;
        }
        setText('security-status-message', data.statusMessage);
        setText('security-rec-count', String(data.recommendations.length));

        const subtitle = document.getElementById('security-status-subtitle');
        if (!subtitle) return;
        if (data.auth.available && data.auth.logPath) {
            subtitle.textContent = 'Источник: ' + data.auth.logPath;
            subtitle.hidden = false;
        } else if (!data.auth.available && data.auth.unavailableReason) {
            subtitle.textContent = data.auth.unavailableReason;
            subtitle.hidden = false;
        } else {
            subtitle.hidden = true;
        }
    }

    function renderMetrics(auth) {
        if (!auth.available) {
            setText('ssh-24-hint', 'Статистика недоступна');
            setText('password-24-hint', 'Статистика недоступна');
            return;
        }

        const h24 = auth.last24Hours;
        const h7 = auth.last7Days;

        setText('ssh-24-total', String(h24.sshTotal));
        setText('ssh-24-hint', 'Ключ: ' + h24.sshPublicKey + ', пароль: ' + h24.sshPassword);
        setText('password-24-total', String(h24.passwordTotal));
        setText('password-24-hint', 'Успешные входы по паролю (включая SSH)');

        setText('ssh-7-total', String(h7.sshTotal));
        setText('ssh-7-key', String(h7.sshPublicKey));
        setText('password-7-total', String(h7.passwordTotal));
    }

    function renderRecentEvents(events) {
        const section = document.getElementById('recent-events-section');
        const list = document.getElementById('recent-events-list');
        if (!section || !list) return;

        if (!events.length) {
            section.hidden = true;
            return;
        }

        section.hidden = false;
        list.innerHTML = events
            .map(function (e) {
                return (
                    '<article class="disk-card">' +
                    '<div class="disk-card__header">' +
                    '<span class="disk-card__name">' + escapeHtml(e.user) + '</span>' +
                    '<span class="disk-card__mount">' + escapeHtml(e.source) + '</span>' +
                    '<span class="tag tag--ok">' + escapeHtml(e.methodLabel) + '</span>' +
                    '</div>' +
                    '<p class="disk-card__hint">' + escapeHtml(formatEventTime(e.timestamp)) + '</p>' +
                    '</article>'
                );
            })
            .join('');
    }

    function renderRecommendations(recommendations) {
        const list = document.getElementById('recommendations-list');
        const empty = document.getElementById('recommendations-empty');
        if (!list) return;

        if (!recommendations.length) {
            list.innerHTML = '';
            if (empty) empty.hidden = false;
            return;
        }

        if (empty) empty.hidden = true;
        list.innerHTML = recommendations
            .map(function (rec) {
                const tagClass = severityTagClass(rec.severity);
                return (
                    '<article class="disk-card recommendation-card recommendation-card--' +
                    escapeHtml(rec.severity) +
                    '">' +
                    '<div class="disk-card__header">' +
                    '<span class="disk-card__name">' + escapeHtml(rec.title) + '</span>' +
                    '<span class="tag ' + tagClass + '">' + escapeHtml(rec.severityLabel) + '</span>' +
                    '</div>' +
                    '<p class="disk-card__hint">' + escapeHtml(rec.message) + '</p>' +
                    '</article>'
                );
            })
            .join('');
    }

    function showError(message) {
        const el = document.getElementById('security-load-error');
        if (el) {
            el.textContent = message;
            el.hidden = false;
        }
        setText('security-status-message', 'Не удалось загрузить данные');
        const subtitle = document.getElementById('security-status-subtitle');
        if (subtitle) {
            subtitle.textContent = 'Проверьте доступ к журналам и повторите попытку';
            subtitle.hidden = false;
        }
    }

    function applyData(data) {
        const main = document.getElementById('security-main');
        if (main) main.classList.remove('main--loading');

        const hostnameEl = document.getElementById('hostname');
        if (hostnameEl) hostnameEl.textContent = data.hostname;

        const errorEl = document.getElementById('security-load-error');
        if (errorEl) errorEl.hidden = true;

        renderStatus(data);
        renderMetrics(data.auth);
        renderRecentEvents(data.auth.recentEvents);
        renderRecommendations(data.recommendations);
    }

    async function load() {
        const controller = new AbortController();
        const timeoutId = setTimeout(function () {
            controller.abort();
        }, config.fetchTimeoutMs);

        try {
            const res = await fetch(config.apiUrl, {
                signal: controller.signal,
                headers: { Accept: 'application/json' },
            });
            if (!res.ok) {
                showError('Сервер вернул ошибку ' + res.status);
                return;
            }
            applyData(await res.json());
        } catch (e) {
            if (e.name === 'AbortError') {
                showError('Превышено время ожидания ответа (' + (config.fetchTimeoutMs / 1000) + ' с)');
            } else {
                showError('Ошибка сети при загрузке данных безопасности');
            }
        } finally {
            clearTimeout(timeoutId);
        }
    }

    load();
})();
