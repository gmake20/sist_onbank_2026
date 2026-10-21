package org.doit.onbank.admin.dto;

import org.doit.onbank.admin.entity.AdminNotification;

import java.time.LocalDateTime;
import java.util.List;

// MON-001 폴링 응답. 화면 JS는 lastId 를 다음 요청의 after 로 보낸다
// unreadCount = 내 미확인 알림 전체 개수 (헤더 배지) / items = after 다음 새 알림 (처음 열 때는 빈 목록)
public record NotificationPoll(Long lastId, long unreadCount, List<Item> items) {

    public record Item(Long notificationId, String type, Long targetId, String message, LocalDateTime createdAt) {

        public static Item from(AdminNotification n) {
            return new Item(n.getNotificationId(), n.getType(), n.getTargetId(), n.getMessage(), n.getCreatedAt());
        }
    }
}
