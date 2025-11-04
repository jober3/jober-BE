package com.example.final_projects.repository;

import com.example.final_projects.entity.TemplateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateHistoryRepository extends JpaRepository<TemplateHistory, Long> {
    List<TemplateHistory> findByTemplateId(Long templateId);
}
