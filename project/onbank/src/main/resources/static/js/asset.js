// 자산관리 리포트 : 기준월을 고르면 바로 조회, 그래프에 마우스를 올리면 값 안내

document.addEventListener('DOMContentLoaded', () => {
    const monthSelect = document.querySelector('.js-month-select');
    if (monthSelect) {
        monthSelect.addEventListener('change', () => monthSelect.form.submit());
    }

    // data-tip 이 있는 막대·도넛 조각 위에서 커서를 따라다니는 안내
    const tooltip = document.querySelector('.js-tooltip');
    if (!tooltip) {
        return;
    }
    document.querySelectorAll('.js-tip').forEach((target) => {
        target.addEventListener('mousemove', (event) => {
            tooltip.textContent = target.dataset.tip;
            tooltip.hidden = false;
            // 화면 오른쪽 끝에서는 커서 왼쪽에 보여 준다
            const width = tooltip.offsetWidth;
            const left = event.clientX + 14 + width > window.innerWidth ? event.clientX - width - 14 : event.clientX + 14;
            tooltip.style.left = left + 'px';
            tooltip.style.top = (event.clientY + 14) + 'px';
        });
        target.addEventListener('mouseleave', () => {
            tooltip.hidden = true;
        });
    });
});
