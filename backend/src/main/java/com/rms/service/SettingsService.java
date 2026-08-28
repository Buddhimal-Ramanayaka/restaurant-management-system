package com.rms.service;

import com.rms.domain.SystemSetting;
import com.rms.dto.response.BillingRatesResponse;
import com.rms.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * FR-21 - service charge and VAT are Admin-configurable, not hardcoded literals. Rates are
 * cached in memory (a single-instance deployment, per the dissertation's stated scope - see
 * Scope Exclusions) and refreshed on every write, so the hot billing path (BillingService.
 * buildBill runs on every preview and every settle) never pays a DB round-trip just to read
 * two numbers that change on the order of "once a year", if ever.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String SERVICE_CHARGE_KEY = "SERVICE_CHARGE_RATE";
    private static final String VAT_KEY = "VAT_RATE";

    private final SystemSettingRepository systemSettingRepository;

    private volatile BigDecimal serviceChargeRate;
    private volatile BigDecimal vatRate;

    @PostConstruct
    void loadCache() {
        serviceChargeRate = readRate(SERVICE_CHARGE_KEY, new BigDecimal("0.10"));
        vatRate = readRate(VAT_KEY, new BigDecimal("0.08"));
    }

    public BigDecimal getServiceChargeRate() {
        return serviceChargeRate;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public BillingRatesResponse getBillingRates() {
        return new BillingRatesResponse(serviceChargeRate, vatRate);
    }

    @Transactional
    public BillingRatesResponse updateBillingRates(BigDecimal newServiceChargeRate, BigDecimal newVatRate) {
        writeRate(SERVICE_CHARGE_KEY, newServiceChargeRate);
        writeRate(VAT_KEY, newVatRate);
        serviceChargeRate = newServiceChargeRate;
        vatRate = newVatRate;
        return getBillingRates();
    }

    private BigDecimal readRate(String key, BigDecimal fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(s -> new BigDecimal(s.getSettingValue()))
                .orElse(fallback);
    }

    private void writeRate(String key, BigDecimal value) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseGet(() -> SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value.toPlainString());
        systemSettingRepository.save(setting);
    }
}
