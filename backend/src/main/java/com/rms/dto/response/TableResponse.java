package com.rms.dto.response;

import com.rms.domain.RestaurantTable;
import com.rms.domain.enums.TableStatus;

public record TableResponse(
        Long id,
        String tableNumber,
        Integer seatingCapacity,
        TableStatus operationalStatus,
        Long currentOrderId
) {
    public static TableResponse from(RestaurantTable table) {
        return new TableResponse(
                table.getId(), table.getTableNumber(), table.getSeatingCapacity(),
                table.getOperationalStatus(), table.getCurrentOrderId()
        );
    }
}
