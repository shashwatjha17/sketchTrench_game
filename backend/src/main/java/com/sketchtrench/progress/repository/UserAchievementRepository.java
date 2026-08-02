package com.sketchtrench.progress.repository;

import com.sketchtrench.progress.entity.UserAchievement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UserAchievement.UserAchievementId> {

    Optional<UserAchievement> findByUserIdAndAchievementCode(Long userId, String code);

    @EntityGraph(attributePaths = "achievement")
    List<UserAchievement> findByUserId(Long userId);
}
