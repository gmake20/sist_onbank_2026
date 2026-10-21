package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.NotificationPoll;
import org.doit.onbank.admin.entity.AdminNotification;
import org.doit.onbank.admin.repository.AdminNotificationRepository;
import org.doit.onbank.common.security.ActorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// MON-001 탐지 실시간 알림 (D-71 폴링). 받는 사람은 탐지 때 FdsDetectionService 가 정해 저장해 둔다 (D-66)
// 여기서는 "내 알림"만 읽는다. 읽음 처리는 알림함(MON-005) 몫
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final AdminNotificationRepository adminNotificationRepository;

    @Transactional(readOnly = true)
    public NotificationPoll poll(Long adminId, Long after) {
        long unread = adminNotificationRepository.countByRecipientTypeAndRecipientIdAndReadAtIsNull(ActorType.ADMIN, adminId);

        // 화면을 처음 열 때 : 지금 마지막 번호만 알려 준다 (로그아웃 중 온 알림은 unread 배지로 보임)
        if (after == null) {
            Long lastId = adminNotificationRepository
                    .findFirstByRecipientTypeAndRecipientIdOrderByNotificationIdDesc(ActorType.ADMIN, adminId)
                    .map(AdminNotification::getNotificationId)
                    .orElse(0L);
            return new NotificationPoll(lastId, unread, List.of());
        }

        List<NotificationPoll.Item> items = adminNotificationRepository
                .findTop50ByRecipientTypeAndRecipientIdAndNotificationIdGreaterThanOrderByNotificationIdAsc(
                        ActorType.ADMIN, adminId, after)
                .stream()
                .map(NotificationPoll.Item::from)
                .toList();
        Long lastId = items.isEmpty() ? after : items.get(items.size() - 1).notificationId();
        return new NotificationPoll(lastId, unread, items);
    }
}
