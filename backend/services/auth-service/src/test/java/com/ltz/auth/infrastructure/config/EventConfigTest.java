package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

class EventConfigTest {

    private final MockEnvironment environment = new MockEnvironment();

    @Test
    void hedefListesiBosVarsayilanOlayKapalidir() {
        EventProperties empty = events(null, Duration.ofSeconds(10), Duration.ofHours(1));

        assertThat(empty.targets()).isEmpty();
        assertThatCode(() -> new EventConfig(empty, deletion(Map.of()), environment)).doesNotThrowAnyException();
    }

    @Test
    void hedefinAdresiTanimliOlmalidir() {
        EventProperties events = events(Set.of("community"), Duration.ofSeconds(10), Duration.ofHours(1));

        DeletionProperties noUrls = deletion(Map.of());

        assertThatThrownBy(() -> new EventConfig(events, noUrls, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ltz.auth.deletion.service-urls.community");
    }

    @Test
    void gecersizHedefAdiReddedilir() {
        EventProperties events = events(Set.of("Community;DROP"), Duration.ofSeconds(10), Duration.ofHours(1));

        DeletionProperties invalidName = deletion(Map.of("Community;DROP", URI.create("http://x:1")));

        assertThatThrownBy(() -> new EventConfig(events, invalidName, environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("plain service names");
    }

    @Test
    void azamiGeriCekilmeIlkindenKisaOlamaz() {
        EventProperties events = events(Set.of("community"), Duration.ofMinutes(10), Duration.ofMinutes(1));

        DeletionProperties community = deletion(Map.of("community", URI.create("http://community-service:8080")));

        assertThatThrownBy(() -> new EventConfig(events, community, environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("max-backoff");
    }

    @Test
    void prodProfilindeSifresizHedefAcilmazDevdeAcilir() {
        EventProperties events = events(Set.of("community"), Duration.ofSeconds(10), Duration.ofHours(1));
        DeletionProperties plain = deletion(Map.of("community", URI.create("http://community-service:8080")));

        assertThatCode(() -> new EventConfig(events, plain, environment)).doesNotThrowAnyException();

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new EventConfig(events, plain, prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
        assertThatCode(() -> new EventConfig(events,
                deletion(Map.of("community", URI.create("https://community.internal"))), prod)).doesNotThrowAnyException();
    }

    @Test
    void hedefListesiAuthEventTargetsOrtamDegiskeniylePropertiesDosyasindanBaglanir() throws Exception {
        assertThat(boundTargets(null)).as("ENV yoksa olay kapalı").isEmpty();
        assertThat(boundTargets("")).as("boş ENV olay kapalı").isEmpty();
        assertThat(boundTargets("community")).containsExactly("community");
        assertThat(boundTargets("community, user")).containsExactlyInAnyOrder("community", "user");
    }

    /** Gerçek application.properties satırı ve AUTH_EVENT_TARGETS yer tutucusuyla hedef kümesini bağlar. */
    private static Set<String> boundTargets(String environmentValue) throws Exception {
        MockEnvironment env = new MockEnvironment();
        if (environmentValue != null) {
            env.setProperty("AUTH_EVENT_TARGETS", environmentValue);
        }
        Properties file = PropertiesLoaderUtils.loadProperties(
                new FileSystemResource("src/main/resources/application.properties"));
        env.getPropertySources().addLast(new PropertiesPropertySource("application", file));
        return Binder.get(env).bind("ltz.auth.events.targets", Bindable.setOf(String.class)).orElse(Set.of());
    }

    private static EventProperties events(Set<String> targets, Duration initialBackoff, Duration maxBackoff) {
        return new EventProperties(targets, false, Duration.ofSeconds(5), 20, Duration.ofMinutes(2), initialBackoff,
                maxBackoff, 20, Duration.ofDays(7), Duration.ofSeconds(5));
    }

    private static DeletionProperties deletion(Map<String, URI> serviceUrls) {
        return new DeletionProperties(Set.of(), serviceUrls, Duration.ofSeconds(5), Duration.ofMinutes(30),
                Duration.ofSeconds(60), Duration.ofMinutes(10), URI.create("https://app.example/account/delete"),
                false, Duration.ofSeconds(30), 20, Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofHours(6), 10,
                Duration.ofDays(14));
    }
}
