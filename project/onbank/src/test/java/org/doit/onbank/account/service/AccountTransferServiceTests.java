package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.doit.onbank.account.dto.TransferLimitInfo;
import org.doit.onbank.account.dto.TransferReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountTransfer;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.account.entity.TransferType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.AccountTransferRepository;
import org.doit.onbank.account.repository.MemberTransferLimit;
import org.doit.onbank.account.repository.TransactionHistoryRepository;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// AccountTransferService 단위 테스트 : DB 대신 가짜(Mock) Repository 사용
// 확인하는 것 : 이체 순서(비밀번호 → 잠금), 한도 검사, 잠금 순서, 기록되는 값, 실패 시 아무것도 바뀌지 않는지
// 확인하지 못하는 것 : 실제 잠금(FOR UPDATE), REQUIRES_NEW 커밋 → DB 통합 테스트·화면에서 확인
@ExtendWith(MockitoExtension.class)
class AccountTransferServiceTests {

	private static final Long MY_ID = 100L;
	private static final Long OTHER_ID = 200L;
	private static final String SQNO = "20261015143012-482915";

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountTransferRepository accountTransferRepository;

	@Mock
	private TransactionHistoryRepository transactionHistoryRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private AccountPasswordVerifier accountPasswordVerifier;

	@Mock
	private TransactionSqnoGenerator transactionSqnoGenerator;

	@InjectMocks
	private AccountTransferService service;

	private Account account(Long accountId, Long memberId, String number, String status, long balance) {
		Account account = Account.builder()
				.memberId(memberId)
				.accountNumber(number)
				.accountStatus(status)
				.balance(balance)
				.todayTransferAmount(0L)
				.build();
		ReflectionTestUtils.setField(account, "accountId", accountId);	// DB가 넣어 주는 ID를 직접 설정
		return account;
	}

	private Member member(String name) {
		return Member.builder().loginId("id").password("pw").name(name).birthDate(LocalDate.of(2000, 1, 1)).build();
	}

	private MemberTransferLimit limit(Long daily, Long once) {
		return new MemberTransferLimit() {
			@Override
			public Long getDailyTransferLimit() {
				return daily;
			}

			@Override
			public Long getOnceTransferLimit() {
				return once;
			}
		};
	}

	// 내 계좌(from) → 다른 회원 계좌(to) 이체가 성공하는 데 필요한 설정
	private void givenTransferable(Account from, Account to) {
		given(accountRepository.findByAccountNumber(to.getAccountNumber())).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());	// 한도 NULL → 기본값
		given(accountPasswordVerifier.verify(from.getAccountId(), MY_ID, "1234")).willReturn(true);
		given(accountRepository.findByIdForUpdate(from.getAccountId())).willReturn(Optional.of(from));
		given(accountRepository.findByIdForUpdate(to.getAccountId())).willReturn(Optional.of(to));
	}

	private void givenRecordable() {
		given(memberRepository.findById(MY_ID)).willReturn(Optional.of(member("홍길동")));
		given(memberRepository.findById(OTHER_ID)).willReturn(Optional.of(member("김철수")));
		given(transactionSqnoGenerator.generate(any())).willReturn(SQNO);
	}

	// ---------- 성공 ----------

	@Test
	void transferMovesMoneyAndRecordsThreeRows() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 100_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 5_000L);
		givenTransferable(from, to);
		givenRecordable();

		TransferReceipt receipt = service.transfer(MY_ID, 1L, "1234", "TEST-0002", 30_000L, null, null);

		// 잔액과 오늘 이체 누적
		assertThat(from.getBalance()).isEqualTo(70_000L);
		assertThat(to.getBalance()).isEqualTo(35_000L);
		assertThat(from.getTodayTransferAmount()).isEqualTo(30_000L);
		assertThat(to.getTodayTransferAmount()).isZero();	// 받는 쪽은 누적하지 않음

		// 이체 1행
		ArgumentCaptor<AccountTransfer> transfer = ArgumentCaptor.forClass(AccountTransfer.class);
		verify(accountTransferRepository).save(transfer.capture());
		assertThat(transfer.getValue().getAccountId()).isEqualTo(1L);
		assertThat(transfer.getValue().getWithdrawalAccountNumber()).isEqualTo("TEST-0001");
		assertThat(transfer.getValue().getDepositAccountNumber()).isEqualTo("TEST-0002");
		assertThat(transfer.getValue().getFinancialInstitutionId()).isNull();	// 당행
		assertThat(transfer.getValue().getTransferAmount()).isEqualTo(30_000L);
		assertThat(transfer.getValue().getTransferType()).isEqualTo(TransferType.IMMEDIATE);
		assertThat(transfer.getValue().getTransactionSqno()).isEqualTo(SQNO);

		// 거래내역 2행 : 같은 거래 번호, 통장 표시를 비우면 상대방 이름
		ArgumentCaptor<TransactionHistory> history = ArgumentCaptor.forClass(TransactionHistory.class);
		verify(transactionHistoryRepository, times(2)).save(history.capture());
		List<TransactionHistory> rows = history.getAllValues();
		assertThat(rows.get(0).getAccountId()).isEqualTo(1L);
		assertThat(rows.get(0).getTransactionType()).isEqualTo(TransactionType.TRANSFER_OUT);
		assertThat(rows.get(0).getBalanceAfterTransaction()).isEqualTo(70_000L);
		assertThat(rows.get(0).getTransactionContent()).isEqualTo("김철수");
		assertThat(rows.get(1).getAccountId()).isEqualTo(2L);
		assertThat(rows.get(1).getTransactionType()).isEqualTo(TransactionType.TRANSFER_IN);
		assertThat(rows.get(1).getBalanceAfterTransaction()).isEqualTo(35_000L);
		assertThat(rows.get(1).getTransactionContent()).isEqualTo("홍길동");
		assertThat(rows).allMatch(row -> SQNO.equals(row.getTransactionSqno()));

		// 영수증 : 받는 분 이름은 가림
		assertThat(receipt.recipientName()).isEqualTo("김*수");
		assertThat(receipt.balanceAfter()).isEqualTo(70_000L);
	}

	@Test
	void transferUsesMemoWhenGiven() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 100_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		givenRecordable();

		TransferReceipt receipt = service.transfer(MY_ID, 1L, "1234", "TEST-0002", 10_000L, "월세", "10월 월세");

		assertThat(receipt.senderMemo()).isEqualTo("월세");
		assertThat(receipt.receiverMemo()).isEqualTo("10월 월세");
	}

	// 두 계좌를 항상 ID 오름차순으로 잠근다 (데드락 방지)
	@Test
	void transferLocksAccountsInIdOrder() {
		Account from = account(2L, MY_ID, "TEST-0002", AccountStatus.ACTIVE, 100_000L);
		Account to = account(1L, OTHER_ID, "TEST-0001", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		givenRecordable();

		service.transfer(MY_ID, 2L, "1234", "TEST-0001", 10_000L, null, null);

		InOrder order = inOrder(accountPasswordVerifier, accountRepository);
		order.verify(accountPasswordVerifier).verify(2L, MY_ID, "1234");	// 비밀번호 확인이 잠금보다 먼저
		order.verify(accountRepository).findByIdForUpdate(1L);
		order.verify(accountRepository).findByIdForUpdate(2L);
	}

	// ---------- 실패 : 잠그기 전에 걸러지는 것 ----------

	@Test
	void transferFailsWhenRecipientNotFound() {
		given(accountRepository.findByAccountNumber("NONE")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", "NONE", 10_000L, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("입금계좌를 찾을 수 없습니다");
		verify(accountPasswordVerifier, never()).verify(anyLong(), anyLong(), anyString());
	}

	@Test
	void transferFailsWhenRecipientPending() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.PENDING, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", "TEST-0002", 10_000L, null, null))
				.hasMessageContaining("입금할 수 없는 계좌");
	}

	@Test
	void transferFailsWhenSameAccount() {
		Account mine = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 100_000L);
		given(accountRepository.findByAccountNumber("TEST-0001")).willReturn(Optional.of(mine));

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", "TEST-0001", 10_000L, null, null))
				.hasMessageContaining("같은 계좌");
	}

	@Test
	void transferFailsWhenOverOnceLimit() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());	// 1회 기본 100만 원

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", "TEST-0002", 1_000_001L, null, null))
				.hasMessageContaining("1회 이체한도");
		verify(accountPasswordVerifier, never()).verify(anyLong(), anyLong(), anyString());
	}

	// 비밀번호가 틀리면 계좌를 잠그지도, 잔액을 바꾸지도 않는다
	@Test
	void transferFailsWhenPasswordWrong() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		given(accountPasswordVerifier.verify(1L, MY_ID, "0000")).willReturn(false);

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "0000", "TEST-0002", 10_000L, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("비밀번호");
		verify(accountRepository, never()).findByIdForUpdate(anyLong());
		verify(transactionHistoryRepository, never()).save(any());
	}

	// ---------- 실패 : 잠근 뒤 걸러지는 것 (아무것도 바뀌지 않아야 함) ----------

	@Test
	void transferFailsWhenOverDailyLimit() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 10_000_000L);
		ReflectionTestUtils.setField(from, "todayTransferAmount", 4_500_000L);
		ReflectionTestUtils.setField(from, "lastTransactionAt", java.time.LocalDateTime.now());	// 오늘 450만 원 이체함
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", "TEST-0002", 600_000L, null, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("1일 이체한도")
				.hasMessageContaining("500,000원");
		assertThat(from.getBalance()).isEqualTo(10_000_000L);
		assertThat(to.getBalance()).isZero();
		verify(accountTransferRepository, never()).save(any());
		verify(transactionHistoryRepository, never()).save(any());
	}

	@Test
	void transferFailsWhenBalanceInsufficient() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 5_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", "TEST-0002", 10_000L, null, null))
				.hasMessageContaining("잔액");
		assertThat(to.getBalance()).isZero();
		verify(transactionHistoryRepository, never()).save(any());
	}

	// ---------- 한도 조회 ----------

	@Test
	void limitUsesDefaultsWhenMemberLimitIsNull() {
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.of(limit(null, null)));

		TransferLimitInfo info = service.getLimit(MY_ID, null);

		assertThat(info.dailyLimit()).isEqualTo(AccountTransferService.DEFAULT_DAILY_LIMIT);
		assertThat(info.onceLimit()).isEqualTo(AccountTransferService.DEFAULT_ONCE_LIMIT);
	}

	@Test
	void limitUsesMemberValuesAndTodayUsed() {
		Account mine = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 0L);
		ReflectionTestUtils.setField(mine, "todayTransferAmount", 300_000L);
		ReflectionTestUtils.setField(mine, "lastTransactionAt", java.time.LocalDateTime.now());
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.of(limit(2_000_000L, 500_000L)));
		given(accountRepository.findById(1L)).willReturn(Optional.of(mine));

		TransferLimitInfo info = service.getLimit(MY_ID, 1L);

		assertThat(info.dailyLimit()).isEqualTo(2_000_000L);
		assertThat(info.onceLimit()).isEqualTo(500_000L);
		assertThat(info.todayUsed()).isEqualTo(300_000L);
		assertThat(info.remaining()).isEqualTo(1_700_000L);
	}
}
