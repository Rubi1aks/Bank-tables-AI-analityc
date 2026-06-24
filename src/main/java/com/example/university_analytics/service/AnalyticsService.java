package com.example.universityanalytics.service;

import com.example.universityanalytics.dto.FactResponse;
import com.example.universityanalytics.dto.NewsResponse;
import com.example.universityanalytics.dto.ScenarioResponse;
import com.example.universityanalytics.entity.BankAnalyticsEntity;
import com.example.universityanalytics.repository.BankAnalyticsRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsService {

    private final BankAnalyticsRepository repository;

    // Конструктор
    public AnalyticsService(BankAnalyticsRepository repository) {
        this.repository = repository;
    }

    public List<FactResponse> getFacts(String subject) {
        List<BankAnalyticsEntity> entities;
        if (subject != null && !subject.isEmpty()) {
            entities = repository.findBySubject(subject);
        } else {
            entities = repository.findAll();
        }
        return entities.stream()
                .map(this::toFactResponse)
                .toList();
    }

    private FactResponse toFactResponse(BankAnalyticsEntity e) {
        FactResponse dto = new FactResponse();
        dto.setPeriod(e.getPeriod());
        dto.setDistrict(e.getDistrict());
        dto.setSubject(e.getSubject());
        dto.setBankIncome(e.getBankIncome());
        dto.setAvgBankTariffPct(e.getAvgBankTariffPct());
        dto.setTransactionVolume(e.getTransactionVolume());
        dto.setAvgLunchCost(e.getAvgLunchCost());
        dto.setClientCount(e.getClientCount());
        dto.setTotalUniversityPeople(e.getTotalUniversityPeople());
        dto.setCanteenEatersPct(e.getCanteenEatersPct());
        dto.setStudents(e.getStudents());
        dto.setAdminStaff(e.getAdminStaff());
        return dto;
    }

    public List<NewsResponse> getNews(String subject) {
        List<NewsResponse> news = new ArrayList<>();
        news.add(new NewsResponse(
                UUID.randomUUID().toString(),
                "Рост цен на продукты в " + subject,
                "РБК",
                "2025-06",
                "Зафиксировано увеличение стоимости продуктов питания на 4.2%, что может повлиять на средний чек в столовых",
                "neutral"
        ));
        news.add(new NewsResponse(
                UUID.randomUUID().toString(),
                "Увеличение числа студентов в регионе",
                "Ведомости",
                "2025-05",
                "В " + subject + " наблюдается прирост абитуриентов на 8%, что создает базу для роста клиентской базы банка",
                "positive"
        ));
        news.add(new NewsResponse(
                UUID.randomUUID().toString(),
                "Изменение тарифной политики",
                "Коммерсантъ",
                "2025-06",
                "Банк рассматривает возможность повышения тарифа эквайринга для образовательных учреждений",
                "negative"
        ));
        return news;
    }
}