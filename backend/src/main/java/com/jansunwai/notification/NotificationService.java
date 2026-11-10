package com.jansunwai.notification;

import com.jansunwai.entity.Notification;
import com.jansunwai.entity.Role;
import com.jansunwai.repository.NotificationRepository;
import com.jansunwai.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications, written in the same transaction as the business change (so a rolled-back action never
 * produces a notification). An e-mail/SMS channel could be added behind the same method later.
 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Long userId, Long complaintId, String type, String message) {
        notifications.save(new Notification(userId, complaintId, type, message));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyDepartmentOfficers(Long departmentId, Long complaintId, String type, String message) {
        if (departmentId == null) {
            notifyAdmins(complaintId, type, message);
            return;
        }
        users.findByDepartment_IdAndRoleAndActiveTrueOrderByFullName(departmentId, Role.DEPARTMENT_OFFICER)
                .forEach(u -> notify(u.getId(), complaintId, type, message));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyAdmins(Long complaintId, String type, String message) {
        users.findByRoleAndActiveTrue(Role.ADMIN).forEach(u -> notify(u.getId(), complaintId, type, message));
    }
}
