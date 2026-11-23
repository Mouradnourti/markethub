package com.pfe.marketplace.config;

import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD when the platform has none. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || isBlank(admin.email()) || isBlank(admin.password()) || userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        User user = new User();
        user.setEmail(admin.email().trim().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setFirstName("Platform");
        user.setLastName("Admin");
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        log.info("Bootstrap admin account created for {}", user.getEmail());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
