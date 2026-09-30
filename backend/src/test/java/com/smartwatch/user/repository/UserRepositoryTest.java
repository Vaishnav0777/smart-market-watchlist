package com.smartwatch.user.repository;

import com.smartwatch.support.PostgresIntegrationTest;
import com.smartwatch.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsUserAndFindsByEmail() {
        User saved = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(userRepository.findByEmail("ada@example.com")).contains(saved);
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(new User("ada@example.com", "Ada"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("ada@example.com", "Ada Lovelace")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
