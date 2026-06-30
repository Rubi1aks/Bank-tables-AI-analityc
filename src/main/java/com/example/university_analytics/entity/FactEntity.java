package com.example.universityanalytics.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "facts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period", "subject", "indicator"}))
public class FactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String period;      // "2025-01"
    private String district;    // "Северо-Западный ФО"
    private String subject;     // "Мурманская обл."
    private String indicator;   // "Студенты", "Самолёты" — динамически!
    private String unit;        // "чел", "руб", "%"
    private Double value;       // 3260.0

    // Геттеры, сеттеры, конструкторы
}