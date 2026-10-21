package org.doit.onbank.branch.dto;

import java.util.List;

import org.doit.onbank.branch.entity.Branch;
import org.doit.onbank.branch.entity.BranchServiceType;
import org.doit.onbank.branch.entity.BranchType;

// 지점/ATM 찾기 화면(지도 마커, 목록)에 보여 줄 정보. /api/branches 응답(JSON)
public record BranchView(Long branchId, BranchType branchType, String typeLabel, String name, String address,
		double latitude, double longitude, String phone, String businessHours, boolean open24h,
		List<String> services) {

	public static BranchView from(Branch branch) {
		return new BranchView(branch.getBranchId(), branch.getBranchType(), branch.getBranchType().getLabel(),
				branch.getName(), branch.getAddress(), branch.getLatitude(), branch.getLongitude(),
				formatPhone(branch.getPhone()), branch.getBusinessHours(), branch.isOpen24h(),
				branch.getServices().stream().sorted().map(BranchServiceType::getLabel).toList());	// enum 선언 순서
	}

	// 숫자만 저장된 전화번호에 '-' 넣기 : 0215880000 → 02-1588-0000, 0315880004 → 031-588-0004
	static String formatPhone(String phone) {
		if (phone == null || phone.length() < 9) {
			return phone;
		}
		int areaLength = phone.startsWith("02") ? 2 : 3;
		int lastStart = phone.length() - 4;
		return phone.substring(0, areaLength) + "-" + phone.substring(areaLength, lastStart) + "-"
				+ phone.substring(lastStart);
	}
}
