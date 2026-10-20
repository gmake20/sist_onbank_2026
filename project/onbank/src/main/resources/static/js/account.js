// 계좌 입출금 화면 공통 스크립트

document.addEventListener('DOMContentLoaded', () => {
    const amountInput = document.querySelector('.js-amount');

    // 금액 바로입력 버튼 : 100만, 50만 ... (누를 때마다 더함), 전액, 정정
    document.querySelectorAll('.amount-shortcut button').forEach((button) => {
        button.addEventListener('click', () => {
            if (!amountInput) {
                return;
            }
            if (button.hasAttribute('data-clear')) {
                amountInput.value = '';
            } else if (button.hasAttribute('data-all')) {
                amountInput.value = button.dataset.all;
            } else {
                const current = Number(amountInput.value) || 0;
                amountInput.value = current + Number(button.dataset.add);
            }
            amountInput.dispatchEvent(new Event('input'));	// 직접 입력할 때와 같이 안내 문구 등을 갱신
            amountInput.focus();
        });
    });

    // 계좌번호 하이픈 자동 입력 : 숫자만 치면 4-3-6 형식으로 넣는다 (1002-482-915307)
    // 영문이 섞인 값(TEST-0001 같은 테스트 계좌)은 그대로 둔다
    document.querySelectorAll('.js-account-number').forEach((input) => {
        input.addEventListener('input', () => {
            const value = input.value;
            if (!/^[\d-]*$/.test(value)) {
                return;
            }
            const digits = value.replace(/-/g, '').slice(0, 13);
            const formatted = [digits.slice(0, 4), digits.slice(4, 7), digits.slice(7)].filter(Boolean).join('-');
            if (formatted === value) {
                return;
            }
            // 커서 위치 유지 : 커서 앞에 있던 숫자 개수만큼 지난 자리로 옮긴다
            const digitsBeforeCaret = value.slice(0, input.selectionStart).replace(/-/g, '').length;
            input.value = formatted;
            let caret = 0;
            for (let count = 0; caret < formatted.length && count < digitsBeforeCaret; caret++) {
                if (formatted[caret] !== '-') {
                    count++;
                }
            }
            input.setSelectionRange(caret, caret);
        });
    });

    // 되돌릴 수 없는 요청 확인 : data-confirm 이 있는 폼은 제출 전에 한 번 묻는다 (예약이체 취소 등)
    // 아래 중복 제출 방지보다 먼저 등록해야 [취소]를 눌러도 버튼이 잠기지 않는다
    document.querySelectorAll('form[data-confirm]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!window.confirm(form.dataset.confirm)) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }
        });
    });

    // 버튼 중복 클릭 방지 : 한 번 제출하면 제출 버튼을 잠근다 (두 번 입출금되는 것 방지)
    document.querySelectorAll('.js-once-form').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (form.dataset.submitted === 'true') {
                event.preventDefault();
                return;
            }
            form.dataset.submitted = 'true';
            form.querySelectorAll('button[type="submit"]').forEach((submit) => {
                submit.disabled = true;
                submit.textContent = '처리 중...';
            });
        });
    });
});
