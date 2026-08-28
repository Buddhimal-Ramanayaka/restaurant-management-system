package com.rms.controller;

import com.rms.dto.response.CustomerVisitSummaryResponse;
import com.rms.dto.response.DailySalesReportResponse;
import com.rms.dto.response.TopSellingItemResponse;
import com.rms.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/daily-sales")
    public ResponseEntity<DailySalesReportResponse> dailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(analyticsService.dailySalesReport(date));
    }

    @GetMapping("/revenue-trend")
    public ResponseEntity<List<DailySalesReportResponse>> revenueTrend(
            @RequestParam(defaultValue = "7") int days
    ) {
        return ResponseEntity.ok(analyticsService.revenueTrend(days));
    }

    @GetMapping("/top-items")
    public ResponseEntity<List<TopSellingItemResponse>> topItems(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(analyticsService.topSellingItems(date, limit));
    }

    @GetMapping("/top-customers")
    public ResponseEntity<List<CustomerVisitSummaryResponse>> topCustomers(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(analyticsService.topCustomersByVisitFrequency(limit));
    }
}
