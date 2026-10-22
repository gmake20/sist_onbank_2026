// 휴대폰·이메일 인증 공용 스크립트 (회원가입 auth/signup.html, 회원정보 수정 mypage/edit.html)
// 화면에 아래 id의 칸이 있으면 인증 버튼을 연결한다. 인증을 쓰지 않는 칸(버튼 없음)은 건너뛴다
//   휴대폰 : phone, btnSendCode, btnVerifyCode, codeRow, phoneCode, phoneTimer, phoneMsg
//   이메일 : email, btnSendEmailCode, btnVerifyEmailCode, emailCodeRow, emailCode, emailTimer, emailMsg
// 버튼의 data-url 로 /api/phone/**, /api/email/** 를 호출하고, 같은 폼의 CSRF 토큰(_csrf)을 함께 보낸다
(() => {
    // 응답 : { ok, message }. 세션이 끝나 CSRF가 맞지 않으면 로그인 화면으로 redirect 되어 JSON이 아님
    async function post(url, params, csrf) {
        try {
            const res = await fetch(url, { method: 'POST', body: new URLSearchParams({ ...params, _csrf: csrf }) });
            if (res.redirected) {
                return { ok: false, message: '화면이 만료되었습니다. 새로고침 후 다시 시도해 주세요.' };
            }
            return await res.json();
        } catch (e) {
            return { ok: false, message: '잠시 후 다시 시도해 주세요.' };
        }
    }

    function mmss(sec) {
        return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0');
    }

    // o : { input, btnSend, btnVerify, codeRow, code, timer, msg, param(서버로 보낼 이름), sanitize(입력값 정리, 선택) }
    function setupVerification(o) {
        if (!o.btnSend) {
            return;	// 이 인증을 쓰지 않는 경우 (칸이 화면에 없음)
        }
        const { input, btnSend, btnVerify, codeRow, code, timer, msg } = o;
        const csrf = btnSend.closest('form').querySelector('input[name="_csrf"]').value;	// th:action 이 넣어 준 CSRF 토큰
        let expireTimer = null;
        let resendTimer = null;

        function showMsg(text, type) {
            msg.textContent = text;
            msg.className = 'verify-msg' + (type ? ' ' + type : '');
        }

        function startExpireTimer(seconds) {
            clearInterval(expireTimer);
            let left = seconds;
            timer.textContent = mmss(left);
            expireTimer = setInterval(() => {
                left--;
                timer.textContent = mmss(Math.max(left, 0));
                if (left <= 0) {
                    clearInterval(expireTimer);
                    btnVerify.disabled = true;
                    showMsg('인증번호가 만료되었습니다. 다시 받아 주세요.', 'fail');
                }
            }, 1000);
        }

        // 재발송은 일정 시간 뒤에만 (서버도 같은 규칙으로 막음)
        function startResendTimer(seconds) {
            clearInterval(resendTimer);
            let left = seconds;
            btnSend.disabled = true;
            btnSend.textContent = '재발송 (' + left + ')';
            resendTimer = setInterval(() => {
                left--;
                btnSend.textContent = '재발송 (' + left + ')';
                if (left <= 0) {
                    clearInterval(resendTimer);
                    btnSend.disabled = false;
                    btnSend.textContent = '재발송';
                }
            }, 1000);
        }

        function resetCodeRow() {
            clearInterval(expireTimer);
            clearInterval(resendTimer);
            codeRow.hidden = true;
            code.value = '';
            btnSend.disabled = false;
            btnSend.textContent = '인증번호 받기';
            showMsg('', '');
        }

        // 값을 바꾸면 이전 값으로 받은 인증번호는 쓸 수 없으므로 초기화
        input.addEventListener('input', () => {
            if (o.sanitize) {
                input.value = o.sanitize(input.value);
            }
            if (!codeRow.hidden) {
                resetCodeRow();
            }
        });
        code.addEventListener('input', () => {
            code.value = code.value.replace(/\D/g, '');
        });

        btnSend.addEventListener('click', async () => {
            btnSend.disabled = true;
            const data = await post(btnSend.dataset.url, { [o.param]: input.value }, csrf);
            if (!data.ok) {
                btnSend.disabled = false;
                showMsg(data.message, 'fail');
                return;
            }
            codeRow.hidden = false;
            code.value = '';
            btnVerify.disabled = false;
            code.focus();
            showMsg(data.message, '');
            startExpireTimer(data.expiresIn);
            startResendTimer(data.resendIn);
        });

        btnVerify.addEventListener('click', async () => {
            const data = await post(btnVerify.dataset.url, { [o.param]: input.value, code: code.value }, csrf);
            if (!data.ok) {
                showMsg(data.message, 'fail');
                return;
            }
            // 인증 완료 : 값을 고정하고 더 이상 발송하지 않음
            clearInterval(expireTimer);
            clearInterval(resendTimer);
            codeRow.hidden = true;
            input.readOnly = true;
            btnSend.disabled = true;
            btnSend.textContent = '인증 완료';
            showMsg(data.message, 'ok');
        });

        // 인증번호 입력칸에서 Enter를 누르면 폼이 제출되지 않고 확인 버튼이 눌리도록
        code.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                btnVerify.click();
            }
        });
    }

    const $ = (id) => document.getElementById(id);

    // 휴대폰 : 숫자만 입력
    setupVerification({
        input: $('phone'), btnSend: $('btnSendCode'), btnVerify: $('btnVerifyCode'),
        codeRow: $('codeRow'), code: $('phoneCode'), timer: $('phoneTimer'), msg: $('phoneMsg'),
        param: 'phone', sanitize: (v) => v.replace(/\D/g, '')
    });

    // 이메일
    setupVerification({
        input: $('email'), btnSend: $('btnSendEmailCode'), btnVerify: $('btnVerifyEmailCode'),
        codeRow: $('emailCodeRow'), code: $('emailCode'), timer: $('emailTimer'), msg: $('emailMsg'),
        param: 'email'
    });
})();
