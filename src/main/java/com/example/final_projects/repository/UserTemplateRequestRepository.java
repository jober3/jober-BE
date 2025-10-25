package com.example.final_projects.repository;

import com.example.final_projects.entity.UserTemplateRequest;
import com.example.final_projects.entity.UserTemplateRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTemplateRequestRepository extends JpaRepository<UserTemplateRequest, Long> {
    Optional<UserTemplateRequest> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, UserTemplateRequestStatus status);
}
