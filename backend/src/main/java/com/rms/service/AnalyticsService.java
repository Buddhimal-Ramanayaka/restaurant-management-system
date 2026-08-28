package com.rms.service;

import com.rms.domain.*;
import com.rms.dto.response.CustomerVisitSummaryResponse;
import com.rms.dto.response.DailySalesReportResponse;
import com.rms.dto.response.TopSellingItemResponse;
import com.rms.repository.CustomerRepository;
import com.rms.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 2.9/2.10 reporting. Deliberately read-only and side-effect free - every
 * method here is a pure query over already-committed data, never a writer. See
 * BillingService for the pricing simplification note this class inherits (revenue
 * is computed off MenuItem.getPrice() at report time, not a per-order price
 * snapshot).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public DailySalesReportResponse dailySalesReport(LocalDate date) {
        List<Order> completed = ordersForDate(date);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;

        for (Order order : completed) {
            for (OrderDetail detail : order.getItems()) {
                totalRevenue = totalRevenue.add(lineRevenue(detail));
                totalCogs = totalCogs.add(lineCogs(detail));
            }
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCogs);
        BigDecimal marginPercent = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new DailySalesReportResponse(date, completed.size(), totalRevenue, totalCogs, grossProfit, marginPercent);
    }

    /** Manager Dashboard revenue-vs-COGS chart (dissertation Figure 3.10): trailing N days, oldest first. */
    @Transactional(readOnly = true)
    public List<DailySalesReportResponse> revenueTrend(int days) {
        LocalDate today = LocalDate.now();
        List<DailySalesReportResponse> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            trend.add(dailySalesReport(today.minusDays(i)));
        }
        return trend;
    }

    @Transactional(readOnly = true)
    public List<TopSellingItemResponse> topSellingItems(LocalDate date, int limit) {
        List<Order> completed = ordersForDate(date);

        // Group every line item across the day per menu item name, accumulating
        // quantity/revenue/COGS - a straightforward in-memory reduction since a single
        // day of orders for one restaurant location is a small enough dataset that a
        // SQL-side GROUP BY would only add complexity without a measurable performance
        // benefit at this scale.
        Map<String, BigDecimal[]> agg = new LinkedHashMap<>();
        Map<String, Integer> qty = new LinkedHashMap<>();

        for (Order order : completed) {
            for (OrderDetail detail : order.getItems()) {
                String name = detail.getMenuItem().getName();
                agg.putIfAbsent(name, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                qty.merge(name, detail.getQuantity(), Integer::sum);

                BigDecimal[] totals = agg.get(name);
                totals[0] = totals[0].add(lineRevenue(detail));
                totals[1] = totals[1].add(lineCogs(detail));
            }
        }

        return agg.entrySet().stream()
                .map(e -> {
                    BigDecimal revenue = e.getValue()[0];
                    BigDecimal cogs = e.getValue()[1];
                    BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) > 0
                            ? revenue.subtract(cogs).multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new TopSellingItemResponse(e.getKey(), qty.get(e.getKey()), revenue, cogs, margin);
                })
                .sorted(Comparator.comparing(TopSellingItemResponse::revenue).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CustomerVisitSummaryResponse> topCustomersByVisitFrequency(int limit) {
        return customerRepository.findAll().stream()
                .sorted(Comparator.comparing(Customer::getVisitCount).reversed())
                .limit(limit)
                .map(c -> new CustomerVisitSummaryResponse(c.getName(), c.getPhoneNumber(), c.getVisitCount(), c.getLifetimeSpend(), c.getLoyaltyTier()))
                .collect(Collectors.toList());
    }

    private List<Order> ordersForDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        return orderRepository.findCompletedBetween(from, to);
    }

    private BigDecimal lineRevenue(OrderDetail detail) {
        return detail.getMenuItem().getPrice().multiply(BigDecimal.valueOf(detail.getQuantity()));
    }

    /** Sum over the menu item recipe of (quantityRequired * ingredient averageUnitCost), times qty sold. */
    private BigDecimal lineCogs(OrderDetail detail) {
        BigDecimal costPerUnit = detail.getMenuItem().getRecipes().stream()
                .map(r -> r.getQuantityRequired().multiply(r.getIngredient().getAverageUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return costPerUnit.multiply(BigDecimal.valueOf(detail.getQuantity()));
    }
}
