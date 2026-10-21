// 지점/ATM 찾기 : /api/branches 검색 결과를 목록과 카카오맵 마커로 보여 준다
// 카카오맵 SDK가 없으면(키 미설정, 로딩 실패) 지도 영역에 안내만 띄우고 목록으로 사용

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('branchSearch');
    const list = document.getElementById('branchList');
    const count = document.getElementById('branchCount');
    const mapEl = document.getElementById('branchMap');
    const mapNotice = document.getElementById('mapNotice');

    const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };    // 서울시청

    let map = null;
    let markers = [];           // { id, overlay, pin }
    let infoOverlay = null;
    let branches = [];

    // ---------- 지도 ----------
    if (typeof kakao === 'undefined' || !kakao.maps) {
        mapEl.hidden = true;
        mapNotice.hidden = false;
    } else {
        kakao.maps.load(() => {
            map = new kakao.maps.Map(mapEl, {
                center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
                level: 8
            });
            map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
            kakao.maps.event.addListener(map, 'click', closeInfo);
            drawMarkers();      // 지도보다 검색 결과가 먼저 온 경우
        });
    }

    function drawMarkers() {
        if (!map) {
            return;
        }
        markers.forEach(m => m.overlay.setMap(null));
        closeInfo();

        markers = branches.map(b => {
            const pin = document.createElement('button');
            pin.type = 'button';
            pin.className = 'br-pin' + (b.branchType === 'ATM' ? ' br-pin--atm' : '');
            pin.title = b.name;
            const label = document.createElement('span');
            label.textContent = b.branchType === 'ATM' ? 'A' : '지';
            pin.appendChild(label);
            pin.addEventListener('click', () => select(b.branchId, true));

            const overlay = new kakao.maps.CustomOverlay({
                position: new kakao.maps.LatLng(b.latitude, b.longitude),
                content: pin,
                yAnchor: 1,
                clickable: true
            });
            overlay.setMap(map);
            return { id: b.branchId, overlay, pin };
        });

        // 결과가 모두 보이도록 지도 범위 맞추기
        if (branches.length === 1) {
            map.setLevel(4);
            map.setCenter(new kakao.maps.LatLng(branches[0].latitude, branches[0].longitude));
        } else if (branches.length > 1) {
            const bounds = new kakao.maps.LatLngBounds();
            branches.forEach(b => bounds.extend(new kakao.maps.LatLng(b.latitude, b.longitude)));
            map.setBounds(bounds);
        }
    }

    function openInfo(b) {
        closeInfo();
        const box = document.createElement('div');
        box.className = 'br-info';
        const title = document.createElement('strong');
        title.textContent = b.name;
        box.appendChild(title);
        box.appendChild(line(b.address));
        box.appendChild(line((b.businessHours || '영업시간 정보 없음') + (b.phone ? ' · ' + b.phone : '')));
        if (b.services.length) {
            box.appendChild(line(b.services.join(', ')));
        }

        infoOverlay = new kakao.maps.CustomOverlay({
            position: new kakao.maps.LatLng(b.latitude, b.longitude),
            content: box,
            yAnchor: 1,
            zIndex: 10
        });
        infoOverlay.setMap(map);
        map.panTo(new kakao.maps.LatLng(b.latitude, b.longitude));
    }

    function closeInfo() {
        if (infoOverlay) {
            infoOverlay.setMap(null);
            infoOverlay = null;
        }
    }

    function line(text) {
        const p = document.createElement('p');
        p.textContent = text;
        return p;
    }

    // ---------- 목록 ----------
    function drawList() {
        list.replaceChildren();
        count.innerHTML = '';
        const strong = document.createElement('strong');
        strong.textContent = branches.length;
        count.append('검색 결과 ', strong, '건');

        if (!branches.length) {
            const empty = document.createElement('li');
            empty.className = 'br-empty';
            empty.textContent = '조건에 맞는 지점/ATM이 없습니다.';
            list.appendChild(empty);
            return;
        }

        branches.forEach(b => {
            const li = document.createElement('li');
            li.className = 'br-item';
            li.tabIndex = 0;
            li.dataset.id = b.branchId;

            const head = document.createElement('div');
            head.className = 'br-item-head';
            const name = document.createElement('h3');
            name.textContent = b.name;
            head.append(name, badge(b.typeLabel, b.branchType === 'ATM' ? 'br-badge br-badge--atm' : 'br-badge'));
            if (b.open24h) {
                head.appendChild(badge('24시간', 'br-badge br-badge--24h'));
            }
            li.appendChild(head);
            li.appendChild(line(b.address));
            li.appendChild(line((b.businessHours || '영업시간 정보 없음') + (b.phone ? ' · ' + b.phone : '')));

            if (b.services.length) {
                const services = document.createElement('div');
                services.className = 'br-services';
                b.services.forEach(s => services.appendChild(badge(s, '')));
                li.appendChild(services);
            }

            li.addEventListener('click', () => select(b.branchId, false));
            li.addEventListener('keydown', e => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    select(b.branchId, false);
                }
            });
            list.appendChild(li);
        });
    }

    function badge(text, className) {
        const span = document.createElement('span');
        span.className = className;
        span.textContent = text;
        return span;
    }

    // 목록 항목이나 마커를 누르면 둘 다 선택 표시 + 정보창
    function select(id, fromMap) {
        list.querySelectorAll('.br-item').forEach(li => {
            li.classList.toggle('is-active', Number(li.dataset.id) === id);
        });
        markers.forEach(m => m.pin.classList.toggle('is-active', m.id === id));

        const b = branches.find(x => x.branchId === id);
        if (map && b) {
            openInfo(b);
        }
        if (fromMap) {
            const li = list.querySelector('.br-item.is-active');
            if (li) {
                li.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            }
        }
    }

    // ---------- 검색 ----------
    async function search() {
        const params = new URLSearchParams();
        const data = new FormData(form);
        ['type', 'keyword', 'service'].forEach(key => {
            const value = (data.get(key) || '').trim();
            if (value) {
                params.set(key, value);
            }
        });
        if (data.get('open24h')) {
            params.set('open24h', 'true');
        }

        try {
            const res = await fetch('/api/branches?' + params, { headers: { Accept: 'application/json' } });
            if (!res.ok) {
                throw new Error(res.status);
            }
            branches = await res.json();
        } catch (e) {
            branches = [];
            count.textContent = '지점/ATM 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
            list.replaceChildren();
            return;
        }
        drawList();
        drawMarkers();
    }

    form.addEventListener('submit', e => {
        e.preventDefault();
        search();
    });
    // 구분 탭, 업무, 24시간은 바꾸면 바로 검색
    form.querySelectorAll('input[type=radio], input[type=checkbox], select').forEach(el => {
        el.addEventListener('change', search);
    });

    search();
});
