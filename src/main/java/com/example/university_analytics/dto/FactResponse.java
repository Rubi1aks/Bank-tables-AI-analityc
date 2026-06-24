package com.example.universityanalytics.dto;

public class FactResponse {
    private String period;
    private String district;
    private String subject;
    private Double bankIncome;
    private Double avgBankTariffPct;
    private Double transactionVolume;
    private Double avgLunchCost;
    private Integer clientCount;
    private Integer totalUniversityPeople;
    private Double canteenEatersPct;
    private Integer students;
    private Integer adminStaff;

    public FactResponse() {}

    public FactResponse(String period, String district, String subject, Double bankIncome,
                        Double avgBankTariffPct, Double transactionVolume, Double avgLunchCost,
                        Integer clientCount, Integer totalUniversityPeople, Double canteenEatersPct,
                        Integer students, Integer adminStaff) {
        this.period = period;
        this.district = district;
        this.subject = subject;
        this.bankIncome = bankIncome;
        this.avgBankTariffPct = avgBankTariffPct;
        this.transactionVolume = transactionVolume;
        this.avgLunchCost = avgLunchCost;
        this.clientCount = clientCount;
        this.totalUniversityPeople = totalUniversityPeople;
        this.canteenEatersPct = canteenEatersPct;
        this.students = students;
        this.adminStaff = adminStaff;
    }

    // Геттеры и сеттеры
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