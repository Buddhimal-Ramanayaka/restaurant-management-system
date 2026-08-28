package com.rms.controller;

import com.rms.dto.request.RecordGrnRequest;
import com.rms.dto.response.GrnResponse;
import com.rms.security.UserPrincipal;
import com.rms.service.GrnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grn")
@RequiredArgsConstructor
public class GrnController {

    private final GrnService grnService;

    @GetMapping
    public ResponseEntity<List<GrnResponse>> findAll() {
        return ResponseEntity.ok(grnService.findAll());
    }

    @PostMapping
    public ResponseEntity<GrnResponse> record(
            @Valid @RequestBody RecordGrnRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(grnService.recordGrn(request, principal.getId()));
    }
}
