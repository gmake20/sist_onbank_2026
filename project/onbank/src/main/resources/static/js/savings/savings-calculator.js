(() => {
  'use strict';
  document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id);
    const amount = $('scAmount'), months = $('scMonths'), rate = $('scRate');
    const error = $('scError');
    const tabs = [...document.querySelectorAll('[data-kind]')];
    const formatter = new Intl.NumberFormat('ko-KR');
    let kind = 'FIXED_DEPOSIT';
    const won = n => formatter.format(n);

    function setKind(next) {
      kind = next;
      tabs.forEach(button => {
        const active = button.dataset.kind === kind;
        button.classList.toggle('is-active', active);
        button.setAttribute('aria-pressed', String(active));
      });
      $('scAmountLabel').textContent = kind === 'FIXED_DEPOSIT' ? '예치 원금' : '매월 납입액';
      $('scAmountHelp').textContent = kind === 'FIXED_DEPOSIT' ? '가입 시 한 번에 맡기는 금액입니다.' : '매월 초 동일 금액을 납입하는 것으로 가정합니다.';
      amount.value = kind === 'FIXED_DEPOSIT' ? '10000000' : '300000';
      calculate();
    }
    function roundHalfUp(numerator, denominator) { return (numerator + denominator / 2n) / denominator; }
    function calculate() {
      error.hidden = true;
      const rawAmount = amount.value.trim(), rawRate = rate.value.trim();
      const a = Number(rawAmount), m = Number(months.value), r = Number(rawRate);
      if (!/^\d+$/.test(rawAmount) || !Number.isSafeInteger(a) || a < 1 || a > 1000000000000 ||
          !Number.isInteger(m) || m < 1 || m > 120 || !Number.isFinite(r) || r < 0 || r > 20 || !/^\d+(?:\.\d{1,2})?$/.test(rawRate)) {
        error.textContent = '금액(1원~1조원), 기간(1~120개월), 연이율(0~20%, 소수 2자리)을 확인해 주세요.';
        error.hidden = false;
        return;
      }
      const p = BigInt(a), n = BigInt(m), bp = BigInt(Math.round(r * 100));
      const principal = kind === 'FIXED_DEPOSIT' ? p : p * n;
      // rate in basis points (3.20% => 320); term in months.
      // Deposit: P × (bp/10000) × (months/12).
      // Installment: P × (bp/10000) × (m(m+1)/2) / 12.
      const gross = kind === 'FIXED_DEPOSIT'
        ? roundHalfUp(p * bp * n, 120000n)
        : roundHalfUp(p * bp * n * (n + 1n), 240000n);
      const tax = (gross * 154n) / 1000n;
      const net = gross - tax;
      const maturity = principal + net;
      $('scPrincipal').textContent = `${won(principal)}원`;
      $('scGross').textContent = `${won(gross)}원`;
      $('scTax').textContent = `− ${won(tax)}원`;
      $('scNet').textContent = `${won(net)}원`;
      $('scMaturity').textContent = won(maturity);
      const pct = maturity > 0n ? Number(net * 10000n / maturity) / 100 : 0;
      $('scMeterInterest').style.width = `${Math.max(0, Math.min(100, pct))}%`;
    }
    tabs.forEach(btn => btn.addEventListener('click', () => setKind(btn.dataset.kind)));
    document.querySelectorAll('[data-add]').forEach(btn => btn.addEventListener('click', () => {
      const current = Number(amount.value) || 0;
      amount.value = String(Math.min(1000000000000, current + Number(btn.dataset.add)));
      calculate();
    }));
    $('scResetAmount').addEventListener('click', () => { amount.value = '0'; calculate(); });
    $('scCalculate').addEventListener('click', calculate);
    [amount, months, rate].forEach(el => el.addEventListener('input', calculate));
    const params = new URLSearchParams(location.search);
    const desiredKind = params.get('type');
    if (desiredKind === 'INSTALLMENT_SAVINGS' || desiredKind === 'FIXED_DEPOSIT') setKind(desiredKind);
    const urlAmount = params.get('amount'), urlMonths = params.get('months'), urlRate = params.get('rate');
    if (urlAmount !== null) amount.value = urlAmount;
    if (urlMonths !== null && [...months.options].some(x => x.value === urlMonths)) months.value = urlMonths;
    if (urlRate !== null) rate.value = urlRate;
    calculate();
  });
})();
