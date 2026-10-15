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
            amountInput.focus();
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
