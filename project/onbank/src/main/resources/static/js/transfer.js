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
