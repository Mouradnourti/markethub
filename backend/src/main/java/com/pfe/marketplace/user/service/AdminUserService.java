package com.pfe.marketplace.user.service;

import com.pfe.marketplace.auth.service.RefreshTokenService;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.user.dto.UserDto;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.mapper.UserMapper;
import com.pfe.marketplace.user.repository.UserRepository;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(Role role, String query, int page, int size) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(page, Math.clamp(size, 1, 100), Sort.by("createdAt").descending());
        return PageResponse.of(userRepository.search(role, pattern, pageable), mapper::toDto);
    }

    /** Disabling a user also revokes all of their sessions. */
    @Transactional
    public UserDto setEnabled(Long adminId, Long userId, boolean enabled) {
        if (Objects.equals(adminId, userId)) {
            throw new BusinessException("You cannot disable your own account");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokenService.revokeAll(userId);
        }
        return mapper.toDto(user);
    }
}
