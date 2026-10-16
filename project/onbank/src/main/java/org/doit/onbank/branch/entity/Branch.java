package org.doit.onbank.branch.entity;

import java.util.HashSet;
import java.util.Set;

import org.doit.onbank.common.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.YesNoConverter;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 지점/ATM (본점 포함). 직원의 소속 지점으로도 사용한다 (employee.branch_id)
// 지금은 조회만 한다. 등록·수정(관리자 지점/ATM 관리 화면)은 그 기능을 만들 때 메서드를 추가
@Entity
@Table(name = "branch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString(exclude = "services")
public class Branch extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	private Long branchId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private BranchType branchType;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, length = 200)
	private String address;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Column(length = 20)
	private String phone;		// 숫자만 저장

	@Column(length = 50)
	private String businessHours;

	// CHAR(1) 'Y'/'N' ↔ boolean
	@Convert(converter = YesNoConverter.class)
	@Column(name = "open_24h_yn", nullable = false, length = 1)
	private boolean open24h = false;

	// 삭제 대신 미사용(N) 처리
	@Convert(converter = YesNoConverter.class)
	@Column(name = "use_yn", nullable = false, length = 1)
	private boolean inUse = true;

	// 이용 가능 업무 (branch_service 테이블). 지점 목록만 볼 때는 읽지 않도록 LAZY (기본값)
	@ElementCollection
	@CollectionTable(name = "branch_service", joinColumns = @JoinColumn(name = "branch_id"))
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "service_code", nullable = false, length = 30)
	private Set<BranchServiceType> services = new HashSet<>();
}
