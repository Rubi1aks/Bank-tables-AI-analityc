package com.example.universityanalytics.repository;

import com.example.universityanalytics.entity.BankAnalyticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BankAnalyticsRepository extends JpaRepository<BankAnalyticsEntity, Long> {
    List<BankAnalyticsEntity> findBySubject(String subject);
    List<BankAnalyticsEntity> findBySubjectAndPeriodBetween(String subject, String start, String end);
}