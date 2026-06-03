(function () {
    const config = window.KERN_FILES || { apiBase: '/api/v1/files', defaultPath: '/', fetchTimeoutMs: 30000 };

    let currentPath = config.defaultPath || '/';

    function setHidden(id, hidden) {
        const el = document.getElementById(id);
        if (el) el.hidden = hidden;
    }

    function setNotice(id, message) {
        const el = document.getElementById(id);
        if (!el) return;
        if (message) {
            el.textContent = message;
            el.hidden = false;
        } else {
            el.hidden = true;
        }
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }

    function formatModified(iso) {
        if (!iso) return '—';
        try {
            return new Date(iso).toLocaleString('ru-RU', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
            });
        } catch {
            return iso;
        }
    }

    function apiUrl(suffix, params) {
        const url = new URL(config.apiBase + suffix, window.location.origin);
        if (params) {
            Object.keys(params).forEach(function (key) {
                if (params[key] != null) url.searchParams.set(key, params[key]);
            });
        }
        return url.toString();
    }

    async function fetchJson(url, options) {
        const controller = new AbortController();
        const timeoutId = setTimeout(function () {
            controller.abort();
        }, config.fetchTimeoutMs);

        try {
            const res = await fetch(url, Object.assign({}, options || {}, { signal: controller.signal }));
            const body = await res.json().catch(function () {
                return null;
            });
            if (!res.ok) {
                const msg = (body && body.message) || 'Ошибка ' + res.status;
                throw new Error(msg);
            }
            return body;
        } catch (e) {
            if (e.name === 'AbortError') {
                throw new Error('Превышено время ожидания ответа');
            }
            throw e;
        } finally {
            clearTimeout(timeoutId);
        }
    }

    function renderBreadcrumb(listing) {
        const nav = document.getElementById('files-breadcrumb');
        if (!nav) return;

        const parts = [];
        parts.push('<a href="#" data-path="' + escapeHtml(listing.path) + '" class="files-breadcrumb__current">' + escapeHtml(listing.path) + '</a>');

        if (listing.parentPath) {
            parts.unshift(
                '<a href="#" data-path="' + escapeHtml(listing.parentPath) + '" class="files-breadcrumb__up">↑ На уровень выше</a>'
            );
        }

        nav.innerHTML = parts.join('<span class="files-breadcrumb__sep"> · </span>');
    }

    function rowActions(entry) {
        if (entry.type === 'directory') {
            return '<button type="button" class="text-action files-open-dir" data-path="' + escapeHtml(entry.path) + '">Открыть</button>';
        }
        if (entry.type !== 'file') {
            return '';
        }
        return (
            '<button type="button" class="text-action files-view" data-path="' + escapeHtml(entry.path) + '">Просмотр</button> ' +
            '<a class="text-action" href="' + escapeHtml(apiUrl('/download', { path: entry.path })) + '" download>Скачать</a>'
        );
    }

    function renderTable(listing) {
        const tbody = document.getElementById('files-table-body');
        if (!tbody) return;

        if (!listing.entries.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="files-table__empty">Каталог пуст</td></tr>';
            return;
        }

        tbody.innerHTML = listing.entries
            .map(function (entry) {
                const nameClass = entry.type === 'directory' ? 'files-name files-name--dir' : 'files-name';
                const nameInner =
                    entry.type === 'directory'
                        ? '<button type="button" class="files-name-btn files-open-dir" data-path="' + escapeHtml(entry.path) + '">' + escapeHtml(entry.name) + '</button>'
                        : escapeHtml(entry.name);

                return (
                    '<tr data-path="' + escapeHtml(entry.path) + '" data-type="' + escapeHtml(entry.type) + '">' +
                    '<td class="files-table__check"><input type="checkbox" class="files-row-check" value="' + escapeHtml(entry.path) + '"/></td>' +
                    '<td class="' + nameClass + '">' + nameInner + '</td>' +
                    '<td>' + escapeHtml(entry.typeLabel) + '</td>' +
                    '<td>' + escapeHtml(entry.sizeLabel) + '</td>' +
                    '<td>' + escapeHtml(formatModified(entry.modifiedAt)) + '</td>' +
                    '<td class="files-table__actions">' + rowActions(entry) + '</td>' +
                    '</tr>'
                );
            })
            .join('');

        updateDeleteButton();
    }

    function selectedPaths() {
        return Array.from(document.querySelectorAll('.files-row-check:checked')).map(function (el) {
            return el.value;
        });
    }

    function updateDeleteButton() {
        const btn = document.getElementById('files-delete-btn');
        if (btn) btn.disabled = selectedPaths().length === 0;
    }

    function hidePreview() {
        setHidden('files-preview', true);
    }

    async function loadDirectory(path) {
        setNotice('files-error', null);
        setNotice('files-success', null);
        hidePreview();

        const tbody = document.getElementById('files-table-body');
        if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="files-table__empty">Загрузка…</td></tr>';

        const listing = await fetchJson(apiUrl('', { path: path }));
        currentPath = listing.path;
        const main = document.getElementById('files-main');
        if (main) main.classList.remove('main--loading');

        renderBreadcrumb(listing);
        renderTable(listing);

        const selectAll = document.getElementById('files-select-all');
        if (selectAll) selectAll.checked = false;
    }

    async function viewFile(path) {
        setNotice('files-error', null);
        const data = await fetchJson(apiUrl('/content', { path: path }));

        setHidden('files-preview', false);
        const title = document.getElementById('files-preview-title');
        const meta = document.getElementById('files-preview-meta');
        const content = document.getElementById('files-preview-content');

        if (title) title.textContent = data.path;
        if (meta) {
            meta.textContent = 'Размер: ' + data.sizeLabel + (data.truncated ? ' · показана только часть файла' : '');
        }
        if (content) content.textContent = data.content;

        document.getElementById('files-preview')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    async function deleteSelected() {
        const paths = selectedPaths();
        if (!paths.length) return;
        if (!window.confirm('Удалить выбранные элементы (' + paths.length + ')?')) return;

        setNotice('files-error', null);
        const result = await fetchJson(apiUrl(''), {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ paths: paths }),
        });

        if (result.success) {
            setNotice('files-success', result.message);
        } else {
            setNotice('files-error', result.message);
        }
        await loadDirectory(currentPath);
    }

    async function uploadFiles(fileList) {
        if (!fileList || !fileList.length) return;

        setNotice('files-error', null);
        setNotice('files-success', null);

        let ok = 0;
        const errors = [];

        for (let i = 0; i < fileList.length; i++) {
            const file = fileList[i];
            const form = new FormData();
            form.append('file', file);

            try {
                const res = await fetch(apiUrl('/upload', { path: currentPath }), {
                    method: 'POST',
                    body: form,
                });
                const body = await res.json().catch(function () {
                    return null;
                });
                if (!res.ok) {
                    errors.push((body && body.message) || file.name + ': ошибка ' + res.status);
                } else if (body && body.success) {
                    ok++;
                } else {
                    errors.push((body && body.message) || file.name);
                }
            } catch (e) {
                errors.push(file.name + ': ' + e.message);
            }
        }

        if (ok) {
            setNotice('files-success', 'Загружено файлов: ' + ok);
        }
        if (errors.length) {
            setNotice('files-error', errors.join('; '));
        }
        await loadDirectory(currentPath);
    }

    function bindEvents() {
        document.getElementById('files-breadcrumb')?.addEventListener('click', function (e) {
            const link = e.target.closest('[data-path]');
            if (!link) return;
            e.preventDefault();
            loadDirectory(link.getAttribute('data-path')).catch(showError);
        });

        document.getElementById('files-table-body')?.addEventListener('click', function (e) {
            const openDir = e.target.closest('.files-open-dir');
            if (openDir) {
                e.preventDefault();
                loadDirectory(openDir.getAttribute('data-path')).catch(showError);
                return;
            }
            const viewBtn = e.target.closest('.files-view');
            if (viewBtn) {
                e.preventDefault();
                viewFile(viewBtn.getAttribute('data-path')).catch(showError);
            }
        });

        document.getElementById('files-table-body')?.addEventListener('change', function (e) {
            if (e.target.classList.contains('files-row-check')) {
                updateDeleteButton();
            }
        });

        document.getElementById('files-select-all')?.addEventListener('change', function (e) {
            const checked = e.target.checked;
            document.querySelectorAll('.files-row-check').forEach(function (cb) {
                cb.checked = checked;
            });
            updateDeleteButton();
        });

        document.getElementById('files-refresh-btn')?.addEventListener('click', function () {
            loadDirectory(currentPath).catch(showError);
        });

        document.getElementById('files-delete-btn')?.addEventListener('click', function () {
            deleteSelected().catch(showError);
        });

        document.getElementById('files-upload-btn')?.addEventListener('click', function () {
            document.getElementById('files-upload-input')?.click();
        });

        document.getElementById('files-upload-input')?.addEventListener('change', function (e) {
            uploadFiles(e.target.files).catch(showError);
            e.target.value = '';
        });

        document.getElementById('files-preview-close')?.addEventListener('click', hidePreview);
    }

    function showError(err) {
        setNotice('files-error', err.message || 'Не удалось выполнить операцию');
        const main = document.getElementById('files-main');
        if (main) main.classList.remove('main--loading');
    }

    bindEvents();
    loadDirectory(currentPath).catch(showError);
})();
