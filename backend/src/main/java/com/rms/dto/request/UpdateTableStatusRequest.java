package com.rms.dto.request;

import com.rms.domain.enums.TableStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTableStatusRequest(
        @NotNull TableStatus status
) {}
