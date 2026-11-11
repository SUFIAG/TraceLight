package com.sentinel.security.adapter.out.persistence;

import com.sentinel.security.domain.model.UserSecurity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSecurityRepository extends JpaRepository<UserSecurity, UUID> {

    List<UserSecurity> findByOrganizationId(UUID organizationId);

    List<UserSecurity> findByUserIdIn(List<UUID> userIds);
}
