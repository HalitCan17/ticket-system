package com.itsm.backend.user;

import java.util.List;

import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserDirectory userDirectory;

    public UserController(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    /** Agents and managers to choose an assignee from; staff only. */
    @GetMapping("/staff")
    public List<StaffMember> staff(CurrentUser user) {
        if (!user.isStaff()) {
            throw new ForbiddenException("STAFF_ONLY", "Only staff can list staff members.");
        }
        return userDirectory.staffMembers();
    }
}
