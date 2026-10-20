
document.addEventListener("DOMContentLoaded", () => {

    const container = document.getElementById(
        "savingsAjaxContent"
    );

    if (!container) return;

    let requestController = null;

    async function loadSavingsPage(url, pushHistory = true) {

        // 이전 요청 취소
        if (requestController) {
            requestController.abort();
        }

        requestController = new AbortController();
        const controller = requestController;

        container.classList.add("sm-loading");

        try {
            const response = await fetch(url, {
                method: "GET",
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                },
                credentials: "same-origin",
                signal: controller.signal
            });

            if (!response.ok) {
                throw new Error("페이지 요청 실패: " + response.status);
            }

            // 로그인 세션 만료 등으로 리다이렉트된 경우
            if (response.redirected &&
                new URL(response.url).pathname !== "/savings/mine") {
                window.location.href = response.url;
                return;
            }

            const html = await response.text();

            // 요청 취소 여부 재확인
            if (controller.signal.aborted) return;

            const documentFragment = new DOMParser()
                .parseFromString(html, "text/html");

            const newContent = documentFragment.getElementById(
                "savingsAjaxContent"
            );

            if (!newContent) {
                throw new Error("AJAX 콘텐츠를 찾을 수 없습니다.");
            }

            container.innerHTML = newContent.innerHTML;

            if (pushHistory) {
                history.pushState({}, "", url);
            }

        } catch (error) {
            if (error.name !== "AbortError") {
                console.error(error);
                // 문제 발생 시 일반 페이지 이동으로 복구
                window.location.href = url;
            }
        } finally {
            if (requestController === controller) {
                container.classList.remove("sm-loading");
                requestController = null;
            }
        }
    }

    // 동적으로 교체한 페이지 버튼에도 이벤트 적용
    container.addEventListener("click", (event) => {

        const link = event.target.closest(
            "a.sm-page-btn, a.sm-page-number, a.sm-account-card"
        );

        if (!link || !container.contains(link)) return;

        const url = new URL(link.href);

        if (url.origin !== window.location.origin ||
            url.pathname !== "/savings/mine") {
            return;
        }

        event.preventDefault();

        loadSavingsPage(url.href);
    });

    // 브라우저 뒤로 가기 / 앞으로 가기
    window.addEventListener("popstate", () => {
        loadSavingsPage(window.location.href, false);
    });

});
