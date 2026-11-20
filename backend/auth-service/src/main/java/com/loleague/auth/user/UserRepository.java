package com.loleague.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u where lower(u.username) = lower(?1)")
    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(?1)")
    boolean existsByUsernameIgnoreCase(String username);
}
