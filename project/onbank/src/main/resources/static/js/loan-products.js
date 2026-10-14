document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('filterForm');
  const list = document.getElementById('productList');
  if (!form || !list) return;
  const items = Array.from(list.querySelectorAll('.onloan-product'));
  const count = document.getElementById('visibleCount');
  const empty = document.getElementById('emptyProducts');
  const apply = () => {
    const keyword = document.getElementById('productSearch').value.trim().toLowerCase();
    const customer = form.querySelector('input[name="customer"]:checked')?.value || 'all';
    let shown = 0;
    items.forEach(item => {
      const match = (item.dataset.name || '').toLowerCase().includes(keyword) &&
       (customer === 'all' || (item.dataset.target || '').includes(customer));
      item.hidden = !match;
      if (match) shown++;
    });
    count.textContent = String(shown);
    empty.hidden = shown !== 0;
  };
  form.addEventListener('submit', e => { e.preventDefault(); apply(); });
  form.addEventListener('reset', () => { setTimeout(apply, 0); });
  document.getElementById('productSort').addEventListener('change', e => {
    const sorted = [...items];
    if (e.target.value === 'name') sorted.sort((a,b) => (a.dataset.name || '').localeCompare(b.dataset.name || '', 'ko'));
    sorted.forEach(item => list.appendChild(item));
  });
  document.querySelectorAll('.onloan-iconbtn').forEach(button => button.addEventListener('click', () => {
    const selected = button.classList.toggle('saved');
    button.textContent = selected ? '★' : '☆';
    button.setAttribute('aria-label', selected ? '관심상품 해제' : '관심상품 추가');
  }));
  document.querySelectorAll('.onloan-category button').forEach(btn => btn.addEventListener('click', () => {
    if (!btn.classList.contains('active')) alert('해당 상품 분류는 아직 준비 중입니다.');
  }));
});

// ONBANK 대출 상품 상세 탭
function initLoanProductTabs() {

    const wrapper = document.getElementById("loanProductInformation");

    if (!wrapper) return;

    const buttons = wrapper.querySelectorAll(".loan-tab-btn");
    const panels = wrapper.querySelectorAll(".loan-tab-content");

    function changeTab(tabId) {

        buttons.forEach(button => {
            const active = button.dataset.tab === tabId;

            button.classList.toggle("active", active);
            button.setAttribute("aria-selected", String(active));
            button.tabIndex = active ? 0 : -1;
        });

        panels.forEach(panel => {
            const active = panel.id === tabId;

            panel.hidden = !active;
            panel.classList.toggle("active", active);
        });
    }

    buttons.forEach(button => {
        button.addEventListener("click", function () {
            changeTab(this.dataset.tab);
        });
    });

    changeTab("features");
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initLoanProductTabs);
} else {
    initLoanProductTabs();
}