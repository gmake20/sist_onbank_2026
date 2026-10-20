// 이체 화면 스크립트 : 잔액·오류횟수·한도 조회, 받는 분 확인 → 확인 단계 표시
// 금액 바로입력 버튼과 중복 제출 방지는 account.js 가 처리한다

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('transferForm');
    if (!form) {
        return;
    }

    const fromSelect = form.querySelector('.js-from-account');
    const amountInput = form.querySelector('.js-amount');
    const passwordInput = form.querySelector('.js-password');
    const toAccountInput = form.querySelector('.js-to-account');
    const allAmountButton = form.querySelector('.js-all-amount');
    const confirmBox = form.querySelector('.js-confirm-box');
    const inputAction = form.querySelector('.js-input-action');
    const confirmError = form.querySelector('.js-confirm-error');

    const confirmButton = form.querySelector('.js-confirm');
    const confirmOtpLink = form.querySelector('.js-confirm-otp-link');
    const otpRow = form.querySelector('.js-otp-row');
    const otpInput = form.querySelector('.js-otp-code');

    // OTP 기준 금액·등록 여부 : 안내 문구용. 실제 판단은 이체할 때 서버가 다시 한다
    const otpRequiredAmount = Number(form.dataset.otpRequiredAmount) || 0;
    const otpReady = form.dataset.otpReady === 'true';
    let onceLimit = null;	// 1회 이체한도 (화면을 열 때 한 번 조회)

    // 내 계좌번호 : 본인 계좌끼리 이체는 OTP를 생략한다
    const myAccountNumbers = new Set(Array.from(document.querySelectorAll('.js-mine-row .js-pick'))
        .map((button) => button.dataset.accountNumber));

    const won = (value) => Number(value).toLocaleString('ko-KR') + '원';
    const selectedOption = () => fromSelect && fromSelect.selectedOptions[0];
    const needsOtp = () => Number(amountInput.value) > otpRequiredAmount
        && !myAccountNumbers.has(toAccountInput.value.trim());

    // 한글 금액 : 1234500 → "123만 4,500원"
    const koreanAmount = (value) => {
        let rest = Math.floor(Number(value));
        if (!(rest >= 1) || rest >= 1e16) {
            return '';
        }
        const parts = [];
        ['', '만', '억', '조'].forEach((unit) => {
            const chunk = rest % 10000;
            if (chunk > 0) {
                parts.unshift(chunk.toLocaleString('ko-KR') + unit);
            }
            rest = Math.floor(rest / 10000);
        });
        return parts.join(' ') + '원';
    };

    // 선택한 출금계좌의 잔액을 "전액" 버튼에 넣는다
    const syncAllAmount = () => {
        const option = selectedOption();
        if (allAmountButton) {
            allAmountButton.dataset.all = option ? option.dataset.balance : 0;
        }
    };
    if (fromSelect) {
        fromSelect.addEventListener('change', () => {
            syncAllAmount();
            form.querySelector('.js-balance-text').textContent = '';
            form.querySelector('.js-fail-count-text').textContent = '';
            form.querySelector('.js-limit-text').textContent = '';
            updateAmountGuide();
        });
    }
    syncAllAmount();

    // 금액 입력 즉시 안내 : 한글 금액, 잔액 초과 / 1회 한도 초과 / OTP 미등록 (실제 차단은 서버에서)
    // 입금계좌번호와 금액을 모두 넣어야 [확인] 버튼을 누를 수 있다
    function updateAmountGuide() {
        const amount = Number(amountInput.value);
        const option = selectedOption();
        form.querySelector('.js-amount-korean').textContent = koreanAmount(amount);

        let message = '';
        let otpLink = false;
        if (amount >= 1 && option && amount > Number(option.dataset.balance)) {
            message = '출금 가능 금액을 초과했어요';
        } else if (amount >= 1 && onceLimit !== null && amount > onceLimit) {
            message = '1회 이체 한도(' + won(onceLimit) + ')를 초과했어요';
        } else if (needsOtp() && !otpReady) {
            message = won(otpRequiredAmount) + '을 넘는 이체는 OTP 등록이 필요해요';
            otpLink = true;
        }
        form.querySelector('.js-amount-warning-text').textContent = message;
        form.querySelector('.js-amount-warning').hidden = !message;
        form.querySelector('.js-otp-register-link').hidden = !otpLink;

        confirmButton.disabled = !(toAccountInput.value.trim() && amount >= 1);
    }
    // 이체실행일자 [즉시] [예약] [정기] : 고른 쪽 입력칸만 열린다
    // 고르지 않은 쪽 입력칸은 disabled 라 전송되지 않는다 → 서버는 받은 값으로 즉시·예약·정기를 구분
    const modeRadios = form.querySelectorAll('.js-schedule-mode');
    const scheduleFields = form.querySelector('.js-schedule-fields');
    const executeDateInput = form.querySelector('.js-execute-date');
    const executeTimeInput = form.querySelector('.js-execute-time');
    const recurringFields = form.querySelector('.js-recurring-fields');
    const transferDaySelect = form.querySelector('.js-transfer-day');
    const recurringTimeInput = form.querySelector('.js-recurring-time');
    const endDateInput = form.querySelector('.js-end-date');
    const isScheduled = () => !executeDateInput.disabled;
    const isRecurring = () => !recurringTimeInput.disabled;
    const pad = (number) => String(number).padStart(2, '0');
    const toDateText = (date) => date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());

    // mode : 'now' | 'scheduled' | 'recurring'
    function setScheduleMode(mode) {
        const scheduled = mode === 'scheduled';
        const recurring = mode === 'recurring';
        modeRadios.forEach((radio) => {
            radio.checked = radio.value === mode;
        });
        scheduleFields.hidden = !scheduled;
        executeDateInput.disabled = !scheduled;
        executeTimeInput.disabled = !scheduled;
        recurringFields.hidden = !recurring;
        transferDaySelect.disabled = !recurring;
        recurringTimeInput.disabled = !recurring;
        endDateInput.disabled = !recurring;
        form.querySelector('.js-now-text').hidden = mode !== 'now';
        form.querySelector('.js-schedule-guide').hidden = !scheduled;
        form.querySelector('.js-recurring-guide').hidden = !recurring;
        // 예약을 처음 열면 기본값 : 지금부터 10분 뒤를 10분 단위로 올림 (예: 15:23 → 15:40)
        if (scheduled && !executeDateInput.value) {
            const at = new Date(Date.now() + 10 * 60 * 1000);
            at.setMinutes(Math.ceil(at.getMinutes() / 10) * 10, 0, 0);
            executeDateInput.value = toDateText(at);
            executeTimeInput.value = pad(at.getHours()) + ':' + pad(at.getMinutes());
        }
        // 정기를 처음 열면 기본값 : 매월 오늘 날짜 09:00
        if (recurring && !recurringTimeInput.value) {
            transferDaySelect.value = String(new Date().getDate());
            recurringTimeInput.value = '09:00';
        }
    }
    modeRadios.forEach((radio) => {
        radio.addEventListener('change', () => {
            setScheduleMode(radio.value);
            showStep(1);	// 확인 단계였다면 다시 확인받도록
        });
    });
    // 등록이 실패해 서버가 다시 그린 화면 : 입력했던 값이 남아 있으면 그 모드로 연다
    // (매월 날짜 select 는 항상 값이 있으므로 시각으로 판단)
    if (recurringTimeInput.value) {
        setScheduleMode('recurring');
    } else if (executeDateInput.value || executeTimeInput.value) {
        setScheduleMode('scheduled');
    } else {
        setScheduleMode('now');
    }

    // 정기이체 첫 회차 : 지금 이후 가장 가까운 "매월 N일 시:분" (그 달에 없는 날이면 말일). 서버 계산과 같은 규칙
    function firstRecurringAt() {
        const [hour, minute] = recurringTimeInput.value.split(':').map(Number);
        const day = Number(transferDaySelect.value);
        const now = new Date();
        const occurrence = (year, month) => {
            const lastDay = new Date(year, month + 1, 0).getDate();
            return new Date(year, month, Math.min(day, lastDay), hour, minute);
        };
        const thisMonth = occurrence(now.getFullYear(), now.getMonth());
        return thisMonth > now ? thisMonth : occurrence(now.getFullYear(), now.getMonth() + 1);
    }

    // 입력값 임시 저장 : [OTP 등록하기]로 OTP 화면에 갔다가 돌아오면 입력하던 값을 다시 채운다
    // 비밀번호·OTP 번호는 저장하지 않는다. 이 탭에서만 유지(sessionStorage)되고, 30분이 지나거나 한 번 복원하면 지운다
    const DRAFT_KEY = 'onbank.transferDraft';
    const DRAFT_MINUTES = 30;
    const memoInputs = ['senderMemo', 'receiverMemo'].map((name) => form.elements[name]).filter(Boolean);

    document.querySelectorAll('.js-otp-register').forEach((link) => {
        link.addEventListener('click', () => {
            const draft = {
                savedAt: Date.now(),
                fromAccountId: fromSelect ? fromSelect.value : '',
                toAccountNumber: toAccountInput.value,
                amount: amountInput.value,
                memos: Object.fromEntries(memoInputs.map((input) => [input.name, input.value])),
                executeDate: isScheduled() ? executeDateInput.value : '',
                executeTime: isScheduled() ? executeTimeInput.value : '',
                transferDay: isRecurring() ? transferDaySelect.value : '',
                recurringTime: isRecurring() ? recurringTimeInput.value : '',
                endDate: isRecurring() ? endDateInput.value : ''
            };
            try {
                sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
            } catch (e) {
                // 저장할 수 없는 환경이면 복원 없이 이동한다
            }
        });
    });

    (function restoreDraft() {
        let draft = null;
        try {
            draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY));
            sessionStorage.removeItem(DRAFT_KEY);
        } catch (e) {
            return;
        }
        // 오래된 값이거나, 서버가 이미 값을 채워 그린 화면(이체 실패 후 다시 그림 등)이면 복원하지 않는다
        if (!draft || Date.now() - draft.savedAt > DRAFT_MINUTES * 60 * 1000
                || amountInput.value || toAccountInput.value) {
            return;
        }
        if (fromSelect && fromSelect.querySelector('option[value="' + draft.fromAccountId + '"]')) {
            fromSelect.value = draft.fromAccountId;
            fromSelect.dispatchEvent(new Event('change'));
        }
        toAccountInput.value = draft.toAccountNumber || '';
        amountInput.value = draft.amount || '';
        memoInputs.forEach((input) => {
            input.value = (draft.memos && draft.memos[input.name]) || '';
        });
        if (draft.recurringTime) {
            transferDaySelect.value = draft.transferDay;
            recurringTimeInput.value = draft.recurringTime;
            endDateInput.value = draft.endDate || '';
            setScheduleMode('recurring');
        } else if (draft.executeDate) {
            executeDateInput.value = draft.executeDate;
            executeTimeInput.value = draft.executeTime || '';
            setScheduleMode('scheduled');
        }
    })();

    amountInput.addEventListener('input', updateAmountGuide);
    toAccountInput.addEventListener('input', updateAmountGuide);
    updateAmountGuide();

    // 1회 이체한도를 미리 받아 둔다 (못 받으면 한도 안내만 빠지고 서버가 다시 검사)
    fetch(form.dataset.limitUrl)
        .then((response) => (response.ok ? response.json() : null))
        .then((data) => {
            if (data) {
                onceLimit = data.onceLimit;
                updateAmountGuide();
            }
        })
        .catch(() => {});

    // 잔액조회
    form.querySelector('.js-show-balance').addEventListener('click', () => {
        const option = selectedOption();
        form.querySelector('.js-balance-text').textContent = option ? '출금가능금액 ' + won(option.dataset.balance) : '';
    });

    // 비밀번호오류횟수조회
    form.querySelector('.js-show-fail-count').addEventListener('click', () => {
        const option = selectedOption();
        form.querySelector('.js-fail-count-text').textContent =
            option ? '비밀번호 오류 ' + option.dataset.failCount + '회 (5회 오류 시 이체 제한)' : '';
    });

    // 이체한도조회 (선택한 출금계좌 기준)
    form.querySelector('.js-show-limit').addEventListener('click', async () => {
        const text = form.querySelector('.js-limit-text');
        const option = selectedOption();
        const query = option ? '?accountId=' + encodeURIComponent(option.value) : '';
        try {
            const response = await fetch(form.dataset.limitUrl + query);
            const data = await response.json();
            text.textContent = response.ok
                ? '1회 한도 ' + won(data.onceLimit) + ' / 1일 한도 ' + won(data.dailyLimit)
                    + ' (오늘 이체 ' + won(data.todayUsed) + ', 남은 한도 ' + won(data.remaining) + ')'
                : data.message;
        } catch (e) {
            text.textContent = '이체한도를 조회하지 못했습니다.';
        }
    });

    // 확인 : 입력값 확인 → 받는 분 이름 조회 → 확인 영역 표시 (2단계)
    confirmButton.addEventListener('click', async () => {
        confirmError.textContent = '';
        confirmOtpLink.hidden = true;
        const message = checkInput();
        if (message) {
            confirmError.textContent = message;
            return;
        }
        // 기준 금액을 넘는 타인 계좌 이체인데 OTP가 없으면 확인 단계로 가지 않는다
        if (needsOtp() && !otpReady) {
            confirmError.textContent = won(otpRequiredAmount) + '을 넘는 이체는 OTP 등록이 필요해요.';
            confirmOtpLink.hidden = false;
            return;
        }

        try {
            const url = form.dataset.recipientUrl + '?accountNumber=' + encodeURIComponent(toAccountInput.value.trim());
            const response = await fetch(url);
            const data = await response.json();
            if (!response.ok) {
                confirmError.textContent = data.message;
                return;
            }
            form.querySelector('.js-confirm-from').textContent = selectedOption().textContent;
            form.querySelector('.js-confirm-to').textContent = data.accountNumber;
            form.querySelector('.js-confirm-name').textContent = data.name;
            form.querySelector('.js-confirm-amount').textContent = won(amountInput.value);

            // 즉시 / 예약 / 정기에 따라 실행일자, 안내 문구, 버튼 이름을 바꾼다
            const scheduled = isScheduled();
            const recurring = isRecurring();
            let when = '즉시';
            let guide = '받는 분 이름을 확인한 뒤 [이체실행]을 누르세요. 이체 후에는 취소할 수 없습니다.';
            let submitText = '이체실행';
            if (scheduled) {
                when = executeDateInput.value.replaceAll('-', '.') + ' ' + executeTimeInput.value + ' 예약 (그때 자동 이체)';
                guide = '받는 분 이름을 확인한 뒤 [예약등록]을 누르세요. 실행 전까지 예약·정기이체 조회 화면에서 취소할 수 있습니다.';
                submitText = '예약등록';
            } else if (recurring) {
                when = '매월 ' + transferDaySelect.value + '일 ' + recurringTimeInput.value
                    + ' (첫 이체 ' + toDateText(firstRecurringAt()).replaceAll('-', '.')
                    + (endDateInput.value ? ', ' + endDateInput.value.replaceAll('-', '.') + '까지' : ', 해지할 때까지') + ')';
                guide = '받는 분 이름을 확인한 뒤 [정기이체등록]을 누르세요. 예약·정기이체 조회 화면에서 언제든 해지할 수 있습니다.';
                submitText = '정기이체등록';
            }
            form.querySelector('.js-confirm-when').textContent = when;
            form.querySelector('.js-confirm-guide').textContent = guide;
            form.querySelector('.js-submit').textContent = submitText;

            // OTP 입력칸 : 필요할 때만 보이고 전송된다
            const otp = needsOtp();
            otpRow.hidden = !otp;
            otpInput.disabled = !otp;
            otpInput.value = '';
            form.querySelector('.js-otp-guide').textContent =
                otp ? won(otpRequiredAmount) + '을 넘는 이체는 OTP 앱의 인증번호 6자리가 필요해요' : '';
            showStep(2);
            if (otp) {
                otpInput.focus();
            }
        } catch (e) {
            confirmError.textContent = '입금계좌를 확인하지 못했습니다. 잠시 후 다시 시도하세요.';
        }
    });

    // 입력칸에서 Enter 를 누르면 확인 단계를 건너뛰고 바로 제출되므로 막는다
    form.querySelectorAll('input').forEach((input) => {
        input.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
            }
        });
    });

    // 이체실행 : OTP가 필요한데 6자리가 아니면 보내지 않는다
    // (submit 이벤트에서 막으면 account.js 의 중복 제출 방지가 먼저 버튼을 잠그므로 click 에서 막는다)
    confirmBox.querySelector('button[type="submit"]').addEventListener('click', (event) => {
        if (!otpInput.disabled && !/^\d{6}$/.test(otpInput.value)) {
            event.preventDefault();
            form.querySelector('.js-otp-guide').textContent = 'OTP 인증번호 숫자 6자리를 입력하세요';
            otpInput.focus();
        }
    });

    // 수정 : 확인 영역을 닫고 입력 단계로
    form.querySelector('.js-edit').addEventListener('click', () => showStep(1));

    // 확인 단계에서 입력값을 바꾸면 다시 확인받도록 입력 단계로 되돌린다
    form.querySelectorAll('input, select').forEach((field) => {
        field.addEventListener('input', () => {
            if (!confirmBox.hidden && field !== passwordInput && field !== otpInput) {
                showStep(1);
            }
        });
    });

    function checkInput() {
        if (!selectedOption()) {
            return '출금계좌를 선택하세요.';
        }
        if (!/^\d{4}$/.test(passwordInput.value)) {
            return '계좌 비밀번호 숫자 4자리를 입력하세요.';
        }
        if (!(Number(amountInput.value) >= 1)) {
            return '이체금액을 입력하세요.';
        }
        if (!toAccountInput.value.trim()) {
            return '입금계좌번호를 입력하세요.';
        }
        // 예약 일시 : 비었거나 지난 시각이면 확인 단계로 가지 않는다 (서버도 다시 검사)
        if (isScheduled()) {
            if (!executeDateInput.value || !executeTimeInput.value) {
                return '예약 일시를 선택하세요.';
            }
            if (new Date(executeDateInput.value + 'T' + executeTimeInput.value) <= new Date()) {
                return '예약 일시는 지금 이후로 선택하세요.';
            }
        }
        // 정기 : 시각은 필수, 종료일은 선택이지만 넣었으면 첫 이체일 이후여야 한다 (서버도 다시 검사)
        if (isRecurring()) {
            if (!recurringTimeInput.value) {
                return '정기이체 날짜와 시각을 선택하세요.';
            }
            const firstDate = toDateText(firstRecurringAt());
            if (endDateInput.value && endDateInput.value < firstDate) {
                return '종료일은 첫 이체일(' + firstDate + ') 이후로 선택하세요.';
            }
        }
        return '';
    }

    function showStep(step) {
        confirmBox.hidden = step !== 2;
        inputAction.hidden = step === 2;
        document.querySelectorAll('.js-step').forEach((element, index) => {
            const active = index + 1 === step;
            element.classList.toggle('active', active);
            element.innerHTML = '<span class="num">' + (index + 1) + '</span>' + (active ? ' ' + element.dataset.label : '');
        });
        if (step === 2) {
            confirmBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    // ---------- 계좌 선택 창 (내 계좌 즐겨찾기 → 출금계좌 / 상대계좌 즐겨찾기·최근입금계좌·본인계좌 → 입금계좌) ----------

    const pickerLayer = document.querySelector('.js-picker-layer');
    let fillFrom = false;	// "즐겨찾는이체정보"로 열면 기본 출금계좌도 채운다
    let lastOpener = null;

    const openPicker = (opener) => {
        const type = opener.dataset.picker;
        fillFrom = opener.dataset.fillFrom === 'true';
        lastOpener = opener;
        pickerLayer.querySelectorAll('.js-picker-list').forEach((list) => {
            list.hidden = list.dataset.picker !== type;
            if (!list.hidden) {
                pickerLayer.querySelector('.js-picker-title').textContent =
                    fillFrom ? '즐겨찾는이체정보' : list.dataset.title;
            }
        });
        if (type === 'mine') {
            // 지금 고른 출금계좌는 본인계좌 목록에서 뺀다 (같은 계좌로는 이체할 수 없음)
            const fromId = fromSelect ? fromSelect.value : '';
            let visible = 0;
            pickerLayer.querySelectorAll('.js-mine-row').forEach((row) => {
                row.hidden = row.dataset.accountId === fromId;
                visible += row.hidden ? 0 : 1;
            });
            pickerLayer.querySelector('.js-mine-empty').hidden = visible > 0;
        }
        pickerLayer.hidden = false;
        pickerLayer.querySelector('.js-picker-close').focus();
    };

    const closePicker = () => {
        pickerLayer.hidden = true;
        if (lastOpener) {
            lastOpener.focus();
        }
    };

    if (pickerLayer) {
        document.querySelectorAll('.js-open-picker').forEach((button) => {
            button.addEventListener('click', () => openPicker(button));
        });
        pickerLayer.querySelector('.js-picker-close').addEventListener('click', closePicker);
        // 바깥(어두운 배경)을 누르면 닫는다
        pickerLayer.addEventListener('click', (event) => {
            if (event.target === pickerLayer) {
                closePicker();
            }
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !pickerLayer.hidden) {
                closePicker();
            }
        });

        // 선택 : 입금계좌번호를 채우고 (즐겨찾는이체정보면 출금계좌도) 창을 닫는다
        pickerLayer.querySelectorAll('.js-pick').forEach((button) => {
            button.addEventListener('click', () => {
                toAccountInput.value = button.dataset.accountNumber;
                toAccountInput.dispatchEvent(new Event('input'));	// 확인 단계였다면 입력 단계로 되돌림

                // 본인 계좌 즐겨찾기는 기본 출금계좌가 그 계좌 자신일 수 있다 → 같은 계좌면 출금계좌는 바꾸지 않는다
                const fromId = button.dataset.fromId;
                const fromOption = fromId && fromSelect
                        ? fromSelect.querySelector('option[value="' + fromId + '"]') : null;
                if (fillFrom && fromOption && !fromOption.textContent.includes(button.dataset.accountNumber)) {
                    fromSelect.value = fromId;
                    fromSelect.dispatchEvent(new Event('change'));
                }
                lastOpener = null;
                pickerLayer.hidden = true;
                amountInput.focus();
            });
        });

        // 내 계좌 즐겨찾기 선택 : 출금계좌만 바꾸고 창을 닫는다
        pickerLayer.querySelectorAll('.js-pick-from').forEach((button) => {
            button.addEventListener('click', () => {
                const option = fromSelect
                        ? fromSelect.querySelector('option[data-account-number="' + button.dataset.accountNumber + '"]') : null;
                if (option && fromSelect.value !== option.value) {
                    fromSelect.value = option.value;
                    fromSelect.dispatchEvent(new Event('change'));	// 전액·조회 문구 갱신
                    fromSelect.dispatchEvent(new Event('input'));	// 확인 단계였다면 입력 단계로 되돌림
                }
                lastOpener = null;
                pickerLayer.hidden = true;
                passwordInput.focus();
            });
        });
    }
});
