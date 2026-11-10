package com.jansunwai.service;

import com.jansunwai.dto.response.NotificationResponse;
import com.jansunwai.dto.response.PageResponse;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.NotificationRepository;
import com.jansunwai.security.AppUserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationQueryService {
    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(AppUserPrincipal user, Pageable pageable) {
        return PageResponse.of(notifications.findByUserIdOrderByCreatedAtDesc(user.id(), pageable), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unread(AppUserPrincipal user) {
        return notifications.countByUserIdAndReadAtIsNull(user.id());
    }

    @Transactional
    public void markRead(Long id, AppUserPrincipal user) {
        var n = notifications.findById(id).filter(x -> x.getUserId().equals(user.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.markRead();
    }
}
