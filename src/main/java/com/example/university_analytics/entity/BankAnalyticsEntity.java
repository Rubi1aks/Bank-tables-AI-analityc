package com.example.universityanalytics.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "university_bank_analytics")
public class BankAnalyticsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String period;
    private String district;
    private String subject;

    @Column(name = "bank_income_rub")
    private Double bankIncome;

    @Column(name = "avg_bank_tariff_pct")
    private Double avgBankTariffPct;

    @Column(name = "transaction_volume_rub")
    private Double transactionVolume;

    @Column(name = "avg_lunch_cost_rub")
    private Double avgLunchCost;

    @Column(name = "client_count_cl")
    private Integer clientCount;

    @Column(name = "total_university_people_cl")
    private Integer totalUniversityPeople;

    @Column(name = "canteen_eaters_pct")
    private Double canteenEatersPct;

    @Column(name = "students_cl")
    private Integer students;

    @Column(name = "admin_staff_cl")
    private Integer adminStaff;

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public Double getBankIncome() { return bankIncome; }
    public void setBankIncome(Double bankIncome) { this.bankIncome = bankIncome; }

    public Double getAvgBankTariffPct() { return avgBankTariffPct; }
    public void setAvgBankTariffPct(Double avgBankTariffPct) { this.avgBankTariffPct = avgBankTariffPct; }

    public Double getTransactionVolume() { return transactionVolume; }
    public void setTransactionVolume(Double transactionVolume) { this.transactionVolume = transactionVolume; }

    public Double getAvgLunchCost() { return avgLunchCost; }
    public void setAvgLunchCost(Double avgLunchCost) { this.avgLunchCost = avgLunchCost; }

    public Integer getClientCount() { return clientCount; }
    public void setClientCount(Integer clientCount) { this.clientCount = clientCount; }

    public Integer getTotalUniversityPeople() { return totalUniversityPeople; }
    public void setTotalUniversityPeople(Integer totalUniversityPeople) { this.totalUniversityPeople = totalUniversityPeople; }

    public Double getCanteenEatersPct() { return canteenEatersPct; }
    public void setCanteenEatersPct(Double canteenEatersPct) { this.canteenEatersPct = canteenEatersPct; }

    public Integer getStudents() { return students; }
    public void setStudents(Integer students) { this.students = students; }

    public Integer getAdminStaff() { return adminStaff; }
    public void setAdminStaff(Integer adminStaff) { this.adminStaff = adminStaff; }
}