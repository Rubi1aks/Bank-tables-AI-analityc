package com.example.universityanalytics.repository;

import com.example.universityanalytics.entity.FactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface FactRepository extends JpaRepository<FactEntity, Long> {

    List<FactEntity> findBySubject(String subject);

    List<FactEntity> findBySubjectAndPeriodBetween(String subject, String start, String end);

    List<FactEntity> findByPeriod(String period);

    @Modifying
    @Transactional
    @Query("DELETE FROM FactEntity f WHERE f.period = :period AND f.subject = :subject")
    void deleteByPeriodAndSubject(@Param("period") String period, @Param("subject") String subject);

    List<String> findDistinctIndicators();

    List<String> findDistinctSubjects();
}