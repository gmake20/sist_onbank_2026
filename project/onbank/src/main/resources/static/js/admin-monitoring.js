// MON-002 실시간 모니터링 (S-17, 와이어프레임 11장, D-84) : 3초마다 지표·거래 표·탐지 목록을 통째로 다시 그린다
// 통째로 받는 이유 : FDS 는 이체 커밋 뒤에 돌아서 점수가 조금 늦게 붙는다 → 다음 차례에 색이 맞춰짐
// 탭이 안 보이면 멈추고, 다시 보이면 바로 받는다. 로그인이 끊기거나(401) 권한이 없으면(403) 멈춘다

document.addEventListener('DOMContentLoaded', () => {
    const box = document.getElementById('admMonitoring');
    if (!box) {
        return;
    }
    const status = box.querySelector('.js-mon-status');
    const unchecked = box.querySelector('.js-mon-unchecked');
    const count = box.querySelector('.js-mon-count');
    const tbody = box.querySelector('.js-mon-rows');
    const alerts = box.querySelector('.js-mon-detections');

    const INTERVAL_MS = 3000;
    const TYPE_LABELS = { TRANSFER_OUT: '이체', WITHDRAWAL: 'ATM 출금' };

    let timer = null;
    let loading = false;    // 응답을 기다리는 중 → 탭이 다시 보여도 요청을 겹쳐 보내지 않음
    let stopped = false;

    const won = n => Number(n || 0).toLocaleString('ko-KR');

    // "2026-10-22T08:55:01.115" → 오늘이면 08:55:01, 아니면 10-22 08:55:01
    function time(value) {
        if (!value) {
            return '';
        }
        const today = new Date().toLocaleDateString('sv-SE');   // 2026-10-22 (sv-SE = YYYY-MM-DD 모양)
        const hms = value.substring(11, 19);
        return value.startsWith(today) ? hms : `${value.substring(5, 10)} ${hms}`;
    }

    // 셀 하나. 값은 textContent 로만 넣는다 (innerHTML 금지)
    function cell(text, note) {
        const td = document.createElement('td');
        td.textContent = text;
        if (note) {
            const div = document.createElement('div');
            div.className = 'adm-note';
            div.textContent = note;
            td.append(div);
        }
        return td;
    }

    function emptyRow(text) {
        const td = cell(text);
        td.colSpan = 5;
        td.className = 'adm-empty';
        const tr = document.createElement('tr');
        tr.append(td);
        return tr;
    }

    // AI 보조 판정 (D-72) 문구. 점수에 안 들어가는 참고 의견이라 규칙 판정과 따로 보라색으로 표시
    // ai = null 이면 아직 안 왔거나(다음 3초에 붙음) 서버를 다시 켜기 전 거래 → 아무것도 안 그림
    function aiParts(ai) {
        const parts = [];
        if (ai.pattern) {
            parts.push(`${ai.pattern} 수법과 닮음 ${ai.patternPercent}%`);
        }
        if (ai.memo) {
            parts.push(`문구 "${ai.memo}" 보이스피싱 표현 ${ai.memoPercent}%`);
        }
        return parts;
    }

    function aiChip(text, off) {
        const chip = document.createElement('span');
        chip.className = off ? 'mon-ai-chip is-off' : 'mon-ai-chip';
        chip.textContent = text;
        return chip;
    }

    // 표 판정 칸 아래 한 줄 (짧게)
    function aiNote(ai) {
        const div = document.createElement('div');
        div.className = 'mon-ai-note';
        if (!ai.ok) {
            div.append(aiChip('AI 판정 없음', true));
            return div;
        }
        const short = [];
        if (ai.pattern) {
            short.push(`${ai.pattern} ${ai.patternPercent}%`);
        }
        if (ai.memo) {
            short.push(`문구 ${ai.memoPercent}%`);
        }
        div.append(aiChip('AI'), document.createTextNode(` ${short.length ? short.join(' · ') : '특이 사항 없음'}`));
        return div;
    }

    // 오른쪽 탐지 카드 아래 보라 띠 (design-samples/fds-ai-assist-sample.html S-17)
    function aiBand(ai) {
        const div = document.createElement('div');
        div.className = ai.ok ? 'mon-ai' : 'mon-ai is-off';
        const text = document.createElement('span');
        if (!ai.ok) {
            div.append(aiChip('AI 판정 없음', true));
            text.textContent = `규칙 점수만 반영됨 (${ai.failReason})`;
            div.append(text);
            return div;
        }
        const parts = aiParts(ai);
        text.textContent = parts.length ? parts.join(' · ') : '닮은 수법·특이 문구 없음';
        const sub = document.createElement('small');
        sub.textContent = '참고용 · 점수에 안 들어감';
        div.append(aiChip('AI 보조'), text, sub);
        return div;
    }

    // 판정 칸 : 탐지는 상세(S-16) 링크, 주의는 글자만, 점수 없으면 -
    function judgeCell(item) {
        const td = document.createElement('td');
        if (item.level === 'DETECTED') {
            const a = document.createElement('a');
            a.className = 'mon-judge-detected';
            a.href = `${box.dataset.fdsUrl}/${item.detectionId}`;
            a.textContent = `탐지 ${item.riskScore}점`;
            td.append(a);
        } else if (item.level === 'CAUTION') {
            td.className = 'mon-judge-caution';
            td.textContent = `주의 ${item.riskScore}점`;
        } else {
            td.textContent = '-';
        }
        if (item.ai) {
            td.append(aiNote(item.ai));
        }
        return td;
    }

    function renderRows(items) {
        tbody.replaceChildren();
        if (items.length === 0) {
            tbody.append(emptyRow('최근 거래가 없습니다.'));
        }
        items.forEach(item => {
            const tr = document.createElement('tr');
            if (item.level === 'DETECTED') {
                tr.className = 'mon-detected';
            } else if (item.level === 'CAUTION') {
                tr.className = 'mon-caution';
            }
            // ATM 출금은 받는 계좌가 없어서 입금 계좌 칸에 거래 종류를 적는다
            const to = item.transactionType === 'TRANSFER_OUT'
                ? (item.counterpartAccountNumber || '-')
                : (TYPE_LABELS[item.transactionType] || item.transactionType);
            tr.append(
                cell(time(item.transactionAt)),
                cell(item.accountNumber, `${item.memberName} (${item.memberLoginId})`),
                cell(to),
                cell(`${won(item.amount)}원`),
                judgeCell(item));
            tbody.append(tr);
        });
        count.textContent = String(items.length);
    }

    // 오른쪽 탐지 목록 : 한 건 전체가 상세 링크. 미확인만 빨간 배지 (탐지 목록 S-15 와 같음)
    function renderDetections(detections) {
        alerts.replaceChildren();
        if (detections.length === 0) {
            const li = document.createElement('li');
            li.className = 'adm-empty';
            li.textContent = '최근 탐지가 없습니다.';
            alerts.append(li);
        }
        detections.forEach(d => {
            const a = document.createElement('a');
            a.href = `${box.dataset.fdsUrl}/${d.detectionId}`;

            const head = document.createElement('div');
            head.className = 'mon-alert-head';
            const rule = document.createElement('span');
            rule.textContent = d.ruleLabel;
            const score = document.createElement('b');
            score.textContent = `${d.riskScore}점`;
            head.append(rule, score);

            const body = document.createElement('div');
            body.className = 'mon-alert-body';
            body.textContent = `${d.accountNumber} · ${TYPE_LABELS[d.transactionType] || d.transactionType} ${won(d.amount)}원`;

            const meta = document.createElement('div');
            meta.className = 'mon-alert-meta';
            const at = document.createElement('span');
            at.textContent = time(d.detectedAt);
            const badge = document.createElement('span');
            badge.className = d.status === 'UNCHECKED' ? 'adm-badge adm-badge-alert' : 'adm-badge';
            badge.textContent = d.statusLabel;
            meta.append(at, badge);

            a.append(head, body, meta);
            if (d.ai) {
                a.append(aiBand(d.ai));
            }
            const li = document.createElement('li');
            li.append(a);
            alerts.append(li);
        });
    }

    function render(data) {
        unchecked.textContent = String(data.uncheckedCount);
        renderRows(data.items);
        renderDetections(data.detections);
    }

    function showStatus(text, state) {
        status.textContent = text;
        status.classList.remove('is-error', 'is-stopped');
        if (state) {
            status.classList.add(state);
        }
    }

    // setInterval 대신 응답을 받은 뒤 다음 요청을 건다 (admin-notification.js 와 같음)
    async function poll() {
        if (loading || stopped) {
            return;
        }
        loading = true;
        try {
            const response = await fetch(box.dataset.url, { headers: { Accept: 'application/json' } });
            if (response.status === 401 || response.status === 403) {
                stopped = true;
                showStatus('로그인이 끊겨 갱신을 멈췄습니다. 다시 로그인해 주세요.', 'is-stopped');
                return;
            }
            if (!response.ok) {
                throw new Error(String(response.status));
            }
            render(await response.json());
            showStatus(`실시간 갱신 중 · 마지막 ${new Date().toTimeString().substring(0, 8)}`);   // 11:10:46
        } catch (e) {
            // 서버 오류·네트워크 오류 : 화면은 그대로 두고 다음 차례에 다시
            showStatus('다시 연결 중... 표는 마지막으로 받은 내용입니다.', 'is-error');
        } finally {
            loading = false;
        }
        if (!stopped && !document.hidden) {
            timer = setTimeout(poll, INTERVAL_MS);
        }
    }

    // 탭이 숨으면 예약을 지우고, 다시 보이면 바로 받는다 → 반복이 2개로 늘지 않게 (FDS 세션 지적)
    document.addEventListener('visibilitychange', () => {
        clearTimeout(timer);
        timer = null;
        if (!document.hidden) {
            poll();
        }
    });

    poll();
});
