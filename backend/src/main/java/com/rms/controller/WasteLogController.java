package com.rms.controller;

import com.rms.dto.request.RecordWasteRequest;
import com.rms.dto.response.WasteLogResponse;
import com.rms.security.UserPrincipal;
import com.rms.service.WasteLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waste-logs")
@RequiredArgsConstructor
public class WasteLogController {

    private final WasteLogService wasteLogService;

    @PostMapping
    public ResponseEntity<WasteLogResponse> record(
            @Valid @RequestBody RecordWasteRequest request, @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(wasteLogService.record(request, principal.getId()));
    }

    @GetMapping
    public ResponseEntity<List<WasteLogResponse>> findAll() {
        return ResponseEntity.ok(wasteLogService.findAll());
    }
}
