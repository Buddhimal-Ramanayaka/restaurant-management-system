package com.rms.controller;

import com.rms.domain.MenuItem;
import com.rms.dto.request.MenuItemRequest;
import com.rms.dto.response.MenuItemDetailResponse;
import com.rms.dto.response.MenuItemResponse;
import com.rms.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GET is readable by every operational role (the POS grid, the Kitchen display,
 * and the Cashier all need to resolve item names/prices); write operations are
 * Admin/Manager only per SecurityConfig.
 */
@RestController
@RequestMapping("/api/menu-items")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> findAll() {
        List<MenuItemResponse> items = menuService.findAll().stream()
                .map(MenuItemResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    @GetMapping("/available")
    public ResponseEntity<List<MenuItemResponse>> findAvailable() {
        List<MenuItemResponse> items = menuService.findAvailable().stream()
                .map(MenuItemResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(items);
    }

    /** Admin edit-screen detail fetch, including current recipe lines. */
    @GetMapping("/{id}")
    public ResponseEntity<MenuItemDetailResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.findByIdWithRecipes(id));
    }

    @PostMapping
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemRequest request) {
        MenuItem saved = menuService.createOrUpdate(null, request);
        return ResponseEntity.ok(MenuItemResponse.from(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponse> update(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        MenuItem saved = menuService.createOrUpdate(id, request);
        return ResponseEntity.ok(MenuItemResponse.from(saved));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<MenuItemResponse> setAvailability(@PathVariable Long id, @RequestParam boolean available) {
        MenuItem saved = menuService.setAvailability(id, available);
        return ResponseEntity.ok(MenuItemResponse.from(saved));
    }
}
