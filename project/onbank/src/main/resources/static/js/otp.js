// 보안설정 > OTP 발급 화면 : 등록용 QR 코드 그리기

document.addEventListener('DOMContentLoaded', () => {
    const qr = document.getElementById('otp-qr');

    // QR 라이브러리(qrcode.min.js)를 불러오지 못했으면 그리지 않는다 (등록 코드로 등록 가능)
    if (!qr || typeof QRCode === 'undefined') {
        return;
    }
    new QRCode(qr, {
        text: qr.dataset.uri,
        width: 180,
        height: 180,
        correctLevel: QRCode.CorrectLevel.M
    });
});
