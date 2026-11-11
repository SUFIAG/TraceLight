package com.sentinel.profile.adapter.out.persistence;

import com.sentinel.profile.domain.model.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {
}
