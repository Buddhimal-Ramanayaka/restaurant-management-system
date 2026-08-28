package com.rms.controller;

import com.rms.domain.RestaurantTable;
import com.rms.dto.response.TableResponse;
import com.rms.service.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @GetMapping
    public ResponseEntity<List<TableResponse>> findAll() {
        List<TableResponse> tables = tableService.findAll().stream()
                .map(TableResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tables);
    }

    /** Housekeeping/manager action once a table has been wiped down after CLEANING. */
    @PatchMapping("/{id}/available")
    public ResponseEntity<TableResponse> markAvailable(@PathVariable Long id) {
        RestaurantTable table = tableService.markAvailable(id);
        return ResponseEntity.ok(TableResponse.from(table));
    }

    @PatchMapping("/{id}/cleaning")
    public ResponseEntity<TableResponse> markCleaning(@PathVariable Long id) {
        RestaurantTable table = tableService.markCleaning(id);
        return ResponseEntity.ok(TableResponse.from(table));
    }

    @PatchMapping("/{id}/reserved")
    public ResponseEntity<TableResponse> setReserved(@PathVariable Long id) {
        RestaurantTable table = tableService.setReserved(id);
        return ResponseEntity.ok(TableResponse.from(table));
    }
}
