
document.addEventListener("DOMContentLoaded", function () {

    const picker = document.getElementById("schedulePicker");
    const scheduleIdInput = document.getElementById("selectedScheduleId");
    const principalEl = document.getElementById("previewPrincipal");
    const interestEl = document.getElementById("previewInterest");
    const totalEl = document.getElementById("previewTotal");
    const button = document.getElementById("repayButton");
    const form = document.getElementById("repaymentForm");

    if (!picker || !scheduleIdInput || !principalEl ||
        !interestEl || !totalEl || !button || !form) {
        console.error("상환 페이지 HTML 요소를 찾지 못했습니다.");
        return;
    }

    const formatter = new Intl.NumberFormat("ko-KR");

    function resetPreview() {
        scheduleIdInput.value = "";
        principalEl.textContent = "—";
        interestEl.textContent = "—";
        totalEl.textContent = "—";
        button.disabled = true;
    }

    function updatePreview() {

        const option = picker.selectedOptions[0];

        if (!option || !picker.value) {
            resetPreview();
            return;
        }

        const principal = Number(option.dataset.principal);
        const interest = Number(option.dataset.interest);
        const total = Number(option.dataset.total);

        const valid =
            option.dataset.principal !== undefined &&
            option.dataset.interest !== undefined &&
            option.dataset.total !== undefined &&
            [principal, interest, total].every(Number.isSafeInteger) &&
            principal >= 0 &&
            interest >= 0 &&
            total > 0 &&
            principal + interest === total;

        if (!valid) {
            console.error("상환 금액 데이터 오류:", option.dataset);
            resetPreview();
            return;
        }

        principalEl.textContent =
            formatter.format(principal) + "원";

        interestEl.textContent =
            formatter.format(interest) + "원";

        totalEl.textContent =
            formatter.format(total) + "원";

        scheduleIdInput.value = picker.value;
        button.disabled = false;
    }

    picker.addEventListener("change", updatePreview);

    // 선택된 회차가 있으면 초기 화면에도 표시
    updatePreview();

    form.addEventListener("submit", function (event) {

        updatePreview();

        if (!scheduleIdInput.value || button.disabled) {
            event.preventDefault();
            alert("상환 회차와 금액을 확인하세요.");
            return;
        }

        const selected = picker.selectedOptions[0];
        const total = Number(selected.dataset.total);

        if (!confirm(
            `총 ${formatter.format(total)}원을 상환하시겠습니까?\n` +
            "연결된 입출금 계좌에서 출금됩니다."
        )) {
            event.preventDefault();
            return;
        }

        button.disabled = true;
        button.textContent = "상환 처리 중...";
    });
});
