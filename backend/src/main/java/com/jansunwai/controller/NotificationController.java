package com.jansunwai.controller;

import com.jansunwai.dto.response.NotificationResponse;
import com.jansunwai.dto.response.PageResponse;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationQueryService notifications;

    public NotificationController(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public PageResponse<NotificationResponse> mine(@PageableDefault(size = 30) Pageable pageable) {
        return notifications.mine(CurrentUser.get(), Pages.capped(pageable));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", notifications.unread(CurrentUser.get()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notifications.markRead(id, CurrentUser.get());
        return ResponseEntity.noContent().build();
    }
}
