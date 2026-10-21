package org.doit.onbank.branch.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// branch.branch_type
@Getter
@RequiredArgsConstructor
public enum BranchType {
	BRANCH("지점"),	// 지점 (본점 포함)
	ATM("ATM");

	private final String label;	// 화면 표시용
}
