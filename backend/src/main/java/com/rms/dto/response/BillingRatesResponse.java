package com.rms.dto.response;

import java.math.BigDecimal;

public record BillingRatesResponse(BigDecimal serviceChargeRate, BigDecimal vatRate) {}
