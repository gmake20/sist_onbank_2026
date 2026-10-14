document.addEventListener('DOMContentLoaded', () => {
  const root = document.querySelector('.loan-page'); if (!root) return;
  const get = id => root.querySelector('#' + id);
  let mode='equal'; let rows=[];
  const won = v => Math.round(v).toLocaleString('ko-KR') + '원';
  const clear = () => { get('results').hidden=true; get('error').textContent=''; rows=[]; };
  root.querySelectorAll('.mode').forEach(btn => btn.addEventListener('click', () => {
    mode=btn.dataset.mode;
    root.querySelectorAll('.mode').forEach(item => {item.classList.toggle('active',item===btn);item.setAttribute('aria-pressed',String(item===btn));});
    clear();
  }));
  ['amount','years','rate'].forEach(id => get(id).addEventListener('input',clear));
  get('loan-form').addEventListener('reset', () => setTimeout(clear,0));
  get('details-button').addEventListener('click', () => {
    if (get('results').hidden) {get('error').textContent='먼저 정보를 입력하고 결과보기를 눌러주세요.';return;}
    get('schedule-wrap').scrollIntoView({behavior:'smooth',block:'start'});
  });
  get('loan-form').addEventListener('submit',event => {
    event.preventDefault(); clear();
    const amountText=get('amount').value.trim().replaceAll(',','');
    const yearsText=get('years').value.trim(); const rateText=get('rate').value.trim();
    const a=Number(amountText), y=Number(yearsText), r=Number(rateText);
    if (!amountText || !yearsText || !rateText || !Number.isFinite(a) || a<=0 || a>1e9 || !Number.isInteger(y) || y<1 || y>50 || !Number.isFinite(r) || r<0 || r>100) {
      get('error').textContent='대출금액(만원)은 0 초과, 기간은 1~50년 정수, 연 이자율은 0~100%로 입력해주세요.';return;
    }
    const principal=a*10000, n=y*12, monthlyRate=r/1200;
    const fixed=monthlyRate===0? principal/n : principal*monthlyRate/(1-Math.pow(1+monthlyRate,-n));
    let balance=principal, interestSum=0;
    for(let i=1;i<=n;i++){
      const interest=balance*monthlyRate;
      let paid=mode==='equal'?fixed-interest:mode==='principal'?principal/n:(i===n?principal:0);
      paid=Math.min(balance,Math.max(0,paid)); if(i===n) paid=balance;
      balance=Math.max(0,balance-paid);interestSum+=interest;
      rows.push({i,paid,interest,total:paid+interest,balance});
    }
    get('payment-label').textContent=mode==='bullet'?'만기 전 월 이자':mode==='principal'?'첫 달 상환금액':'매월 상환금액';
    get('monthly').textContent=won(rows[0].total);get('interest').textContent=won(interestSum);get('total').textContent=won(principal+interestSum);
    get('schedule-body').innerHTML=rows.map(v=>`<tr><td>${v.i}회</td><td>${won(v.paid)}</td><td>${won(v.interest)}</td><td>${won(v.total)}</td><td>${won(v.balance)}</td></tr>`).join('');
    get('results').hidden=false;get('results').scrollIntoView({behavior:'smooth',block:'start'});
  });
});

function searchLoanProducts() {
    const keyword = document.getElementById("loanSearch").value.trim();

    location.href = "/loan/products?keyword="
        + encodeURIComponent(keyword);
}

document.getElementById("loanSearch")
    .addEventListener("keydown", function(e) {
        if (e.key === "Enter") {
            e.preventDefault();
            searchLoanProducts();
        }
    });
