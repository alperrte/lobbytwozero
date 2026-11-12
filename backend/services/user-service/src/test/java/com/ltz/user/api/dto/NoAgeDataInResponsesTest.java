package com.ltz.user.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Sözleşme: yaş doğrulaması Auth'a aittir (karar K2); User yaş, doğum tarihi ya da yaş doğrulama bilgisini hiçbir yanıtta
 * (profil, açık profil, toplu profil, arama, özet, gizlilik kararı) döndürmez. Yanıt tiplerinin bileşen adları taranır;
 * yeni bir yanıt alanı bu adlardan birini taşırsa test düşer ve karar yeniden ele alınır.
 */
class NoAgeDataInResponsesTest {

    private static final Pattern FORBIDDEN = Pattern.compile("adult|birth|ageverif|minor|dob",
            Pattern.CASE_INSENSITIVE);

    @Test
    void hicbirYanitTipiYasDogumTarihiVeyaYasDogrulamaAlaniTasimaz() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (BeanDefinition definition : scanner.findCandidateComponents("com.ltz.user.api.dto.response")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            scanned++;
            for (RecordComponent component : type.getRecordComponents()) {
                if (FORBIDDEN.matcher(component.getName().toLowerCase(Locale.ROOT)).find()) {
                    offenders.add(type.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertThat(scanned).as("taranan yanıt tipi sayısı (tarama çalışıyor mu)").isGreaterThan(5);
        assertThat(offenders).isEmpty();
    }
}
