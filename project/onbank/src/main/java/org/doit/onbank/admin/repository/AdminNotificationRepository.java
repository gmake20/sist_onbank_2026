package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.entity.AdminNotification;
import org.doit.onbank.common.security.ActorType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    // MON-001 폴링 : after 다음 번호부터 오래된 순, 한 번에 최대 50개
    List<AdminNotification> findTop50ByRecipientTypeAndRecipientIdAndNotificationIdGreaterThanOrderByNotificationIdAsc(
            ActorType recipientType, Long recipientId, Long after);

    // 화면을 처음 열 때 기준 번호 (옛 알림을 쏟지 않게)
    Optional<AdminNotification> findFirstByRecipientTypeAndRecipientIdOrderByNotificationIdDesc(
            ActorType recipientType, Long recipientId);

    // 헤더 배지 : 미확인(read_at NULL) 개수
    long countByRecipientTypeAndRecipientIdAndReadAtIsNull(ActorType recipientType, Long recipientId);
}
