package com.jansunwai.security;

import com.jansunwai.entity.Complaint;
import com.jansunwai.entity.Role;
import com.jansunwai.exception.ForbiddenActionException;
import com.jansunwai.exception.ResourceNotFoundException;
import com.jansunwai.repository.ComplaintAssignmentRepository;
import org.springframework.stereotype.Component;

/**
 * Every "may this user see / change this complaint?" rule in one place.
 * Complaints a user may not SEE produce 404 (their existence is not revealed).
 */
@Component
public class AccessPolicy {
    private final ComplaintAssignmentRepository assignments;

    public AccessPolicy(ComplaintAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    public boolean canView(AppUserPrincipal user, Complaint c) {
        return switch (user.role()) {
            case ADMIN -> true;
            case CITIZEN -> c.getCitizenId().equals(user.id());
            case DEPARTMENT_OFFICER -> c.getDepartmentId() == null || c.getDepartmentId().equals(user.departmentId());
            case FIELD_OFFICER -> isAssignedTo(user, c);
        };
    }

    public void assertCanView(AppUserPrincipal user, Complaint c) {
        if (!canView(user, c)) throw new ResourceNotFoundException("Complaint", c.getTrackingId());
    }

    /** Department officer of the complaint's department (or of an unrouted complaint), or admin. */
    public void assertDepartmentOfficer(AppUserPrincipal user, Complaint c) {
        assertCanView(user, c);
        if (user.role() != Role.DEPARTMENT_OFFICER && !user.isAdmin()) {
            throw new ForbiddenActionException("Only a department officer can do this");
        }
    }

    public void assertAssignedFieldOfficer(AppUserPrincipal user, Complaint c) {
        assertCanView(user, c);
        if (user.role() != Role.FIELD_OFFICER || !isAssignedTo(user, c)) {
            throw new ForbiddenActionException("Only the assigned field officer can do this");
        }
    }

    public void assertOwner(AppUserPrincipal user, Complaint c) {
        assertCanView(user, c);
        if (!c.getCitizenId().equals(user.id())) throw new ForbiddenActionException("Only the citizen who filed it can do this");
    }

    public boolean isStaff(AppUserPrincipal user) {
        return user.role() != Role.CITIZEN;
    }

    private boolean isAssignedTo(AppUserPrincipal user, Complaint c) {
        return assignments.findActive(c.getId()).map(a -> a.getFieldOfficer().getId().equals(user.id())).orElse(false);
    }
}
