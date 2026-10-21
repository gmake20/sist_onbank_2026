// MON-001 탐지 실시간 알림 (D-71 폴링) : 관리자 공통 레이아웃에서 3초마다 내 새 알림을 조회
// 종 아이콘 배지 = 미확인 개수, 새 알림 = 오른쪽 위 토스트 (최대 3개, 닫기만 — 링크는 탐지 화면 생기면)
// 마지막으로 본 알림 번호와 안 닫은 토스트는 탭(sessionStorage)에 관리자별로 둔다 → 메뉴를 옮겨도 빠지거나 사라지지 않음
// 로그인이 끊기면(401) 멈춘다. 그 밖의 오류는 다음 3초에 다시 시도

document.addEventListener('DOMContentLoaded', () => {
    const box = document.getElementById('admToasts');
    const bell = document.querySelector('.adm-bell');
    const badge = document.querySelector('.js-bell-badge');
    if (!box || !bell || !badge) {
        return;
    }

    const INTERVAL_MS = 3000;
    const MAX_TOASTS = 3;
    const KIND_LABELS = { FDS_DETECTED: '이상거래 탐지' };
    const STORAGE_KEY = `admNotiLastId:${box.dataset.adminId}`;   // 같은 탭에서 다른 관리자로 로그인해도 안 섞이게
    const TOASTS_KEY = `admNotiToasts:${box.dataset.adminId}`;   // 안 닫은 토스트 → 메뉴를 옮겨도 다시 그림

    // 저장 못 하는 환경(사생활 보호 모드 등)이면 저장 없이 동작 = 페이지마다 기준 번호부터
    function loadLastId() {
        try {
            const saved = sessionStorage.getItem(STORAGE_KEY);
            return saved === null ? null : Number(saved);
        } catch (e) {
            return null;
        }
    }

    function saveLastId(id) {
        try {
            sessionStorage.setItem(STORAGE_KEY, String(id));
        } catch (e) {
            // 저장 실패 : 무시
        }
    }

    // 안 닫은 토스트 목록 저장 (최대 MAX_TOASTS 개). 저장 못 하면 이 화면에서만 보인다
    function loadToasts() {
        try {
            const saved = sessionStorage.getItem(TOASTS_KEY);
            return saved === null ? [] : JSON.parse(saved);
        } catch (e) {
            return [];
        }
    }

    function saveToasts() {
        try {
            sessionStorage.setItem(TOASTS_KEY, JSON.stringify(openToasts));
        } catch (e) {
            // 저장 실패 : 무시
        }
    }

    let lastId = loadLastId();   // null 이면 after 없이 요청 → 서버가 기준 번호만 준다 (로그인 직후)
    let openToasts = loadToasts();   // 아직 안 닫은 토스트 (알림 항목 그대로)

    function showBadge(count) {
        badge.hidden = count <= 0;
        badge.textContent = count > 99 ? '99+' : String(count);
        bell.setAttribute('aria-label', count > 0 ? `알림 목록 (미확인 ${count}건)` : '알림 목록');
    }

    // 메시지는 textContent 로만 넣는다 (innerHTML 금지)
    function renderToast(item) {
        const toast = document.createElement('div');
        toast.className = 'adm-toast';

        const body = document.createElement('div');
        body.className = 'adm-toast-body';
        const head = document.createElement('div');
        head.className = 'adm-toast-head';
        const kind = document.createElement('span');
        kind.className = 'adm-toast-kind';
        kind.textContent = KIND_LABELS[item.type] || '알림';
        const time = document.createElement('span');
        time.className = 'adm-toast-time';
        time.textContent = (item.createdAt || '').substring(11, 19);   // "2026-10-22T03:28:59" → 03:28:59
        head.append(kind, time);
        const msg = document.createElement('div');
        msg.className = 'adm-toast-msg';
        msg.textContent = item.message;
        body.append(head, msg);

        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'adm-toast-close';
        close.setAttribute('aria-label', '닫기');
        close.textContent = '×';
        close.addEventListener('click', () => {
            toast.remove();
            openToasts = openToasts.filter(t => t.notificationId !== item.notificationId);
            saveToasts();
        });

        toast.append(body, close);
        box.append(toast);
    }

    // 새 알림 : 목록에 넣고 그린다. 3개가 넘으면 가장 오래된 것부터 뺀다 (화면·저장 둘 다)
    function addToast(item) {
        openToasts.push(item);
        renderToast(item);
        while (openToasts.length > MAX_TOASTS) {
            openToasts.shift();
            box.firstElementChild.remove();
        }
        saveToasts();
    }

    // setInterval 대신 응답을 받은 뒤 다음 요청을 건다 (느린 응답이 겹치지 않게)
    async function poll() {
        try {
            const url = lastId === null ? box.dataset.url : `${box.dataset.url}?after=${lastId}`;
            const response = await fetch(url, { headers: { Accept: 'application/json' } });
            if (response.status === 401) {
                return;   // 로그아웃·세션 만료 → 멈춤
            }
            if (response.ok) {
                const data = await response.json();
                data.items.forEach(addToast);
                lastId = data.lastId;
                saveLastId(lastId);
                showBadge(data.unreadCount);
            }
        } catch (e) {
            // 네트워크 오류 등 : 다음 차례에 다시
        }
        setTimeout(poll, INTERVAL_MS);
    }

    openToasts.forEach(renderToast);   // 메뉴를 옮기기 전에 안 닫은 토스트 다시 그리기
    poll();
});
