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

    const won = (value) => Number(value).toLocaleString('ko-KR') + '원';
    const selectedOption = () => fromSelect && fromSelect.selectedOptions[0];

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
        });
    }
    syncAllAmount();

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
    form.querySelector('.js-confirm').addEventListener('click', async () => {
        confirmError.textContent = '';
        const message = checkInput();
        if (message) {
            confirmError.textContent = message;
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
            showStep(2);
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

    // 수정 : 확인 영역을 닫고 입력 단계로
    form.querySelector('.js-edit').addEventListener('click', () => showStep(1));

    // 확인 단계에서 입력값을 바꾸면 다시 확인받도록 입력 단계로 되돌린다
    form.querySelectorAll('input, select').forEach((field) => {
        field.addEventListener('input', () => {
            if (!confirmBox.hidden && field !== passwordInput) {
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
});
