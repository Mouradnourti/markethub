package com.pfe.marketplace.admin;

import com.pfe.marketplace.catalog.dto.CatalogDtos.CategoryDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.CategoryRequest;
import com.pfe.marketplace.catalog.service.CategoryService;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.dispute.dto.DisputeDtos.DisputeDto;
import com.pfe.marketplace.dispute.dto.DisputeDtos.ResolveDisputeRequest;
import com.pfe.marketplace.dispute.entity.DisputeStatus;
import com.pfe.marketplace.dispute.service.DisputeService;
import com.pfe.marketplace.security.AuthUser;
import com.pfe.marketplace.settings.dto.SettingsDtos.PlatformSettingsDto;
import com.pfe.marketplace.settings.service.SettingsService;
import com.pfe.marketplace.shop.dto.ShopDtos.CommissionRequest;
import com.pfe.marketplace.shop.dto.ShopDtos.ShopAdminDto;
import com.pfe.marketplace.shop.dto.ShopDtos.ShopStatusRequest;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.stats.dto.StatsDtos.AdminStatsDto;
import com.pfe.marketplace.stats.service.AdminStatsService;
import com.pfe.marketplace.user.dto.UserDto;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Platform administration. URL rules restrict /api/admin/** to ROLE_ADMIN. */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    public record EnabledRequest(boolean enabled) {
    }

    private final AdminUserService userService;
    private final ShopService shopService;
    private final CategoryService categoryService;
    private final SettingsService settingsService;
    private final DisputeService disputeService;
    private final AdminStatsService statsService;

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) Role role,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return userService.list(role, q, page, size);
    }

    @Operation(summary = "Enable or disable a user account (disabling revokes its sessions)")
    @PatchMapping("/users/{id}/enabled")
    public UserDto setEnabled(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id,
                              @RequestBody EnabledRequest request) {
        return userService.setEnabled(admin.id(), id, request.enabled());
    }

    @GetMapping("/shops")
    public PageResponse<ShopAdminDto> shops(@RequestParam(required = false) ShopStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return shopService.listForAdmin(status, page, size);
    }

    @Operation(summary = "Validate, reject or suspend a seller's shop")
    @PatchMapping("/shops/{id}/status")
    public ShopAdminDto shopStatus(@PathVariable Long id, @Valid @RequestBody ShopStatusRequest request) {
        return shopService.changeStatus(id, request.status());
    }

    @Operation(summary = "Set a shop-specific commission rate (null = platform default)")
    @PatchMapping("/shops/{id}/commission")
    public ShopAdminDto shopCommission(@PathVariable Long id, @Valid @RequestBody CommissionRequest request) {
        return shopService.changeCommission(id, request);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
    }

    @GetMapping("/settings")
    public PlatformSettingsDto settings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    public PlatformSettingsDto updateSettings(@Valid @RequestBody PlatformSettingsDto request) {
        return settingsService.update(request);
    }

    @GetMapping("/disputes")
    public PageResponse<DisputeDto> disputes(@RequestParam(required = false) DisputeStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return disputeService.list(status, page, size);
    }

    @Operation(summary = "Approve or reject a return request / dispute")
    @PatchMapping("/disputes/{id}")
    public DisputeDto resolveDispute(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id,
                                     @Valid @RequestBody ResolveDisputeRequest request) {
        return disputeService.resolve(admin.id(), id, request);
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return statsService.global();
    }
}
