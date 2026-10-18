package org.doit.onbank.otp.dto;

import java.time.LocalDateTime;

import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.entity.OtpStatus;

// 보안설정 > OTP 화면에 보여 줄 기기 정보 (시드는 넣지 않는다)
public record OtpDeviceInfo(String serialNo, OtpStatus status, int failCount,
		LocalDateTime issuedAt, LocalDateTime activatedAt) {

	public static OtpDeviceInfo from(OtpDevice device) {
		return new OtpDeviceInfo(device.getSerialNo(), device.getStatus(), device.getFailCount(),
				device.getIssuedAt(), device.getActivatedAt());
	}
}
