package com.sentinel.auth.adapter.out.persistence;

import com.sentinel.auth.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndOrganizationId(String email, UUID organizationId);

    boolean existsByEmailAndOrganizationId(String email, UUID organizationId);

    List<User> findByOrganizationIdOrderByFirstNameAsc(UUID organizationId);
}
