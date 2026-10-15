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


document.addEventListener("DOMContentLoaded", function () {

    const form = document.getElementById("filterForm");
    const productList = document.getElementById("productList");
    const countElement = document.getElementById("visibleCount");
    const sortElement = document.getElementById("productSort");

    // 상품 목록 페이지에서만 실행
    if (!form || !productList) return;

    const products = Array.from(
        productList.querySelectorAll(".onloan-product")
    );

    function filterProducts() {

        const keyword = document.getElementById("productSearch")
            .value.trim().toLowerCase();

        const customer = form.querySelector(
            'input[name="customer"]:checked'
        ).value;

        let count = 0;

        products.forEach(product => {

            const name = (product.dataset.name || "").toLowerCase();
            const target = product.dataset.target || "";

            const nameMatch = name.includes(keyword);

            const customerMatch =
                customer === "all" || target.includes(customer);

            const visible = nameMatch && customerMatch;

            product.hidden = !visible;

            if (visible) count++;
        });

        countElement.textContent = count;

        const emptyMessage =
            document.getElementById("emptyProducts");

        if (emptyMessage) {
            emptyMessage.hidden = count !== 0;
        }
    }

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        filterProducts();
    });

    form.addEventListener("reset", function () {
        setTimeout(filterProducts, 0);
    });

    // 상품명 정렬
    if (sortElement) {
        sortElement.addEventListener("change", function () {

            const sorted = [...products];

            if (this.value === "name") {
                sorted.sort((a, b) =>
                    (a.dataset.name || "").localeCompare(
                        b.dataset.name || "", "ko"
                    )
                );
            }

            sorted.forEach(product => {
                productList.appendChild(product);
            });
        });
    }

    filterProducts();
});

function getProductDetailUrl(product) {

    const category = product.category || currentCategory;

    if (category === "credit") {
        return `/loan/products/${encodeURIComponent(product.id)}`;
    }

    return `/loan/categories/${encodeURIComponent(category)}/${encodeURIComponent(product.id)}`;
}
