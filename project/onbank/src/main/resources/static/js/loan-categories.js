document.addEventListener('DOMContentLoaded', () => {
  const form=document.getElementById('filterForm');
  const list=document.getElementById('productList');
  if (!form || !list) return;
  const items=[...list.querySelectorAll('.onloan-product')];
  const count=document.getElementById('visibleCount');
  const empty=document.getElementById('emptyProducts');
  const search=document.getElementById('productSearch');
  const params=new URLSearchParams(window.location.search);
  search.value=params.get('keyword')||'';
  const apply=()=>{
    const keyword=search.value.trim().toLocaleLowerCase('ko');
    const customer=form.querySelector('input[name="customer"]:checked')?.value||'all';
    let visible=0;
    items.forEach(el=>{
      const matched=(el.dataset.name||'').toLocaleLowerCase('ko').includes(keyword)
        && (customer==='all'||(el.dataset.target||'').includes(customer));
      el.hidden=!matched;
      if(matched) visible++;
    });
    count.textContent=visible;
    empty.hidden=visible>0;
  };
  form.addEventListener('submit',e=>{e.preventDefault();apply()});
  form.addEventListener('reset',()=>setTimeout(apply,0));
  document.getElementById('productSort')?.addEventListener('change',e=>{
    const sorted=[...items];
    if(e.target.value==='name') sorted.sort((a,b)=>(a.dataset.name||'').localeCompare(b.dataset.name||'','ko'));
    else sorted.sort((a,b)=>Number(a.dataset.order)-Number(b.dataset.order));
    sorted.forEach(item=>list.appendChild(item));
  });
  apply();
});
