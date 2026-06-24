package com.example.universityanalytics.service;

import com.example.universityanalytics.entity.BankAnalyticsEntity;
import com.example.universityanalytics.repository.BankAnalyticsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class DataLoaderService implements CommandLineRunner {

    private final BankAnalyticsRepository repository;
    private final Random random = new Random();

    // Конструктор
    public DataLoaderService(BankAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            System.out.println("Данные уже есть в БД, пропускаем загрузку.");
            return;
        }

        List<BankAnalyticsEntity> data = new ArrayList<>();

        String[] districts = {"Северо-Западный ФО", "Центральный ФО", "Приволжский ФО"};
        String[] subjects = {"Мурманская обл.", "Московская обл.", "Республика Татарстан"};

        for (int year = 2023; year <= 2025; year++) {
            for (int month = 1; month <= 12; month++) {
                String period = String.format("%d-%02d", year, month);

                for (int r = 0; r < subjects.length; r++) {
                    int students = 3000 + r * 500 + random.nextInt(300);
                    int adminStaff = 150 + r * 30 + random.nextInt(40);
                    int totalPeople = students + adminStaff;
                    double canteenEatersPct = 50 + random.nextDouble() * 25;
                    int clients = (int) (totalPeople * (canteenEatersPct / 100.0) * (0.7 + random.nextDouble() * 0.25));
                    double avgLunchCost = 230 + r * 30 + random.nextDouble() * 40;
                    double transactionVolume = clients * avgLunchCost * (4 + random.nextDouble() * 4);
                    double tariff = 4.5 + random.nextDouble() * 1.5;
                    double bankIncome = transactionVolume * (tariff / 100.0);

                    BankAnalyticsEntity entity = new BankAnalyticsEntity();
                    entity.setPeriod(period);
                    entity.setDistrict(districts[r]);
                    entity.setSubject(subjects[r]);
                    entity.setStudents(students);
                    entity.setAdminStaff(adminStaff);
                    entity.setTotalUniversityPeople(totalPeople);
                    entity.setCanteenEatersPct(canteenEatersPct);
                    entity.setClientCount(clients);
                    entity.setAvgLunchCost(avgLunchCost);
                    entity.setTransactionVolume(transactionVolume);
                    entity.setAvgBankTariffPct(tariff);
                    entity.setBankIncome(bankIncome);

                    data.add(entity);
                }
            }
        }

        repository.saveAll(data);
        System.out.println("Загружено " + data.size() + " записей в БД.");
    }
}