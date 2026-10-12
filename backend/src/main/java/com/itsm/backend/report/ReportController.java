package com.itsm.backend.report;

import java.util.List;

import com.itsm.backend.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(CurrentUser user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return reports.summary(user, days);
    }

    @GetMapping("/team")
    public List<TeamMemberResponse> team(CurrentUser user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return reports.team(user, days);
    }
}
