document.addEventListener("DOMContentLoaded", function () {

    const form = document.getElementById("interestCalcForm");

    if (!form) return;

    const won = (value) =>
        Math.round(value).toLocaleString("ko-KR") + "원";

    form.addEventListener("submit", function (event) {

        event.preventDefault();

        if (!form.reportValidity()) return;

        const amount = Number(
            document.getElementById("calcAmount").value
        );

        const months = Number(
            document.getElementById("calcMonths").value
        );

        const annualRate = Number(
            document.getElementById("calcRate").value
        );

        const method =
            document.getElementById("calcMethod").value;

        // 입력값 검증
        if (
            !Number.isSafeInteger(amount) ||
            amount < 10000 ||
            !Number.isInteger(months) ||
            months < 1 ||
            months > 600 ||
            !Number.isFinite(annualRate) ||
            annualRate < 0 ||
            annualRate > 100
        ) {
            alert("대출금액, 기간, 이자율을 올바르게 입력하세요.");
            return;
        }

        const monthlyRate = annualRate / 100 / 12;

        let balance = amount;
        let totalInterest = 0;
        let schedule = [];

        // 원리금균등상환 월 납입액
        const equalPayment = monthlyRate === 0
            ? amount / months
            : amount * monthlyRate /
              (1 - Math.pow(1 + monthlyRate, -months));

        for (let i = 1; i <= months; i++) {

            const interest = balance * monthlyRate;

            let principal;

            if (method === "EQUAL") {
                principal = equalPayment - interest;

            } else if (method === "PRINCIPAL") {
                principal = amount / months;

            } else {
                principal = i === months ? balance : 0;
            }

            // 마지막 회차의 잔액 오차 보정
            if (i === months) {
                principal = balance;
            }

            principal = Math.min(
                balance,
                Math.max(0, principal)
            );

            const payment = principal + interest;

            balance = Math.max(0, balance - principal);
            totalInterest += interest;

            schedule.push({
                month: i,
                principal: principal,
                interest: interest,
                payment: payment,
                balance: balance
            });
        }

        const totalPayment = amount + totalInterest;

        const label =
            method === "EQUAL"
                ? "매월 상환금액"
                : method === "PRINCIPAL"
                ? "첫 달 상환금액"
                : "만기 전 월 이자";

        document.getElementById("calcMonthlyLabel")
            .textContent = label;

        document.getElementById("calcMonthlyResult")
            .textContent = won(
                method === "BULLET" && months === 1
                    ? amount * monthlyRate
                    : schedule[0].payment
            );

        document.getElementById("calcInterestResult")
            .textContent = won(totalInterest);

        document.getElementById("calcTotalResult")
            .textContent = won(totalPayment);

        // 월별 상환표 출력
        const tbody =
            document.getElementById("interestScheduleBody");

        tbody.innerHTML = schedule.map(row => `
            <tr>
                <td>${row.month}회</td>
                <td>${won(row.principal)}</td>
                <td>${won(row.interest)}</td>
                <td>${won(row.payment)}</td>
                <td>${won(row.balance)}</td>
            </tr>
        `).join("");

        const result =
            document.getElementById("interestCalcResult");

        result.hidden = false;

        result.scrollIntoView({
            behavior: "smooth",
            block: "start"
        });
    });

});