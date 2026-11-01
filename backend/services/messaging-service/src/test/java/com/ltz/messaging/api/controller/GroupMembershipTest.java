package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * MSG-03 üye ekleme ve rol yönetimi, MSG-04 ayrılma, MSG-05 yöneticinin üye çıkarması. Yetki matrisi: OWNER herkesi
 * ekler/çıkarır ve rol değiştirir; ADMIN ekler ve yalnız MEMBER çıkarır; MEMBER yönetemez; üye olmayan 404 alır.
 */
class GroupMembershipTest extends IntegrationTestSupport {

    private UUID owner;
    private UUID admin;
    private UUID member;
    private UUID group;

    @BeforeEach
    void setUp() throws Exception {
        owner = newUser();
        admin = newUser();
        member = newUser();
        group = createGroupAndGetId(owner, admin, member);
        changeRole(owner, group, admin, "ADMIN").andExpect(status().isOk());
    }

    // ------------------------------------------------------------ üye ekleme (MSG-03)

    @Test
    void sahipVeYoneticiUyeEkler() throws Exception {
        UUID byOwner = newUser();
        UUID byAdmin = newUser();

        addMembers(owner, group, byOwner).andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(4));
        addMembers(admin, group, byAdmin).andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.members[?(@.userId=='%s')].role".formatted(byAdmin)).value("MEMBER"));
    }

    @Test
    void siradanUyeEkleyemez403() throws Exception {
        UUID outsider = newUser();
        int callsBefore = blocks.calls();

        addMembers(member, group, outsider).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_GROUP_ROLE"));
        assertThat(activeRole(group, outsider)).isNull();
        // Yetkisiz kişi engel sorgusu tetikleyemez.
        assertThat(blocks.calls()).isEqualTo(callsBefore);
    }

    @Test
    void uyeOlmayanEkleyemez404VeEngelSorulmaz() throws Exception {
        int callsBefore = blocks.calls();

        addMembers(newUser(), group, newUser()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        assertThat(blocks.calls()).isEqualTo(callsBefore);
    }

    @Test
    void zatenUyeOlanEklenmezHicbiriEklenmez409() throws Exception {
        UUID fresh = newUser();

        addMembers(owner, group, fresh, member).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        assertThat(activeRole(group, fresh)).isNull();
    }

    @Test
    void kendiniEklemek400() throws Exception {
        addMembers(owner, group, owner).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_ADD_SELF"));
    }

    @Test
    void ekleyenIleEngelliKisiEklenemez() throws Exception {
        UUID blocked = newUser();
        blocks.block(admin, blocked);

        addMembers(admin, group, blocked).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));
        assertThat(activeRole(group, blocked)).isNull();
        // Engel ekleyene özgüdür: başka bir yönetici ekleyebilir.
        addMembers(owner, group, blocked).andExpect(status().isOk());
    }

    @Test
    void engelDurumuOgrenilemezseEklemeYapilmaz503() throws Exception {
        UUID fresh = newUser();
        blocks.setUnavailable(true);

        addMembers(owner, group, fresh).andExpect(status().isServiceUnavailable());
        assertThat(activeRole(group, fresh)).isNull();
    }

    @Test
    void birebirSohbeteUyeEklenemez409UyeOlmayan404() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);

        addMembers(alice, direct, newUser()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_CONVERSATION_REQUIRED"));
        // Üye olmayan sohbetin türünü öğrenemez.
        addMembers(newUser(), direct, newUser()).andExpect(status().isNotFound());
    }

    @Test
    void grupSiniriAsilamaz() throws Exception {
        UUID[] members = Stream.generate(UUID::randomUUID).limit(98).toArray(UUID[]::new);
        UUID big = createGroupAndGetId(owner, members);

        addMembers(owner, big, newUser(), newUser()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_LIMIT_REACHED"));
        addMembers(owner, big, newUser()).andExpect(status().isOk());
        assertThat(activeMemberCount(big)).isEqualTo(100);
        addMembers(owner, big, newUser()).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------ üye çıkarma (MSG-05)

    @Test
    void sahipUyeyiCikarirCikarilanErisemez() throws Exception {
        removeMember(owner, group, member).andExpect(status().isNoContent());

        assertThat(activeRole(group, member)).isNull();
        assertThat(jdbc.queryForObject("select removed_by from conversation_members "
                + "where conversation_id = ? and user_id = ?", UUID.class, group, member)).isEqualTo(owner);
        getConversation(member, group).andExpect(status().isNotFound());
        leave(member, group).andExpect(status().isNotFound());
        addMembers(member, group, newUser()).andExpect(status().isNotFound());
    }

    @Test
    void yoneticiYalnizUyeCikarir() throws Exception {
        UUID otherAdmin = newUser();
        addMembers(owner, group, otherAdmin).andExpect(status().isOk());
        changeRole(owner, group, otherAdmin, "ADMIN").andExpect(status().isOk());

        removeMember(admin, group, owner).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_GROUP_ROLE"));
        removeMember(admin, group, otherAdmin).andExpect(status().isForbidden());
        removeMember(admin, group, member).andExpect(status().isNoContent());
        assertThat(activeRole(group, owner)).isEqualTo("OWNER");
        assertThat(activeRole(group, otherAdmin)).isEqualTo("ADMIN");
    }

    @Test
    void sahipYoneticiyiCikarabilir() throws Exception {
        removeMember(owner, group, admin).andExpect(status().isNoContent());
        assertThat(activeRole(group, admin)).isNull();
    }

    @Test
    void siradanUyeCikaramaz() throws Exception {
        UUID other = newUser();
        addMembers(owner, group, other).andExpect(status().isOk());

        removeMember(member, group, other).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_GROUP_ROLE"));
        assertThat(activeRole(group, other)).isEqualTo("MEMBER");
    }

    @Test
    void kendiniCikarmak400UyeOlmayanHedef404() throws Exception {
        removeMember(owner, group, owner).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_SELF"));
        removeMember(owner, group, newUser()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        removeMember(newUser(), group, member).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void cikarilanYenidenEklenirseUyeRoluyleDoner() throws Exception {
        removeMember(owner, group, admin).andExpect(status().isNoContent());

        addMembers(owner, group, admin).andExpect(status().isOk());

        assertThat(activeRole(group, admin)).isEqualTo("MEMBER");
        assertThat(jdbc.queryForObject("select removed_by is null from conversation_members "
                + "where conversation_id = ? and user_id = ?", Boolean.class, group, admin)).isTrue();
        getConversation(admin, group).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ rol yönetimi (MSG-03)

    @Test
    void yalnizSahipRolDegistirir() throws Exception {
        changeRole(admin, group, member, "ADMIN").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_GROUP_ROLE"));
        changeRole(member, group, admin, "MEMBER").andExpect(status().isForbidden());

        changeRole(owner, group, admin, "MEMBER").andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.userId=='%s')].role".formatted(admin)).value("MEMBER"));
        assertThat(activeRole(group, admin)).isEqualTo("MEMBER");
    }

    @Test
    void sahiplikRolUcuylaVerilmezKendiRoluDegismez() throws Exception {
        changeRole(owner, group, admin, "OWNER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
        changeRole(owner, group, owner, "MEMBER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLE"));
        changeRole(owner, group, newUser(), "ADMIN").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        changeRole(owner, group, admin, "SUPERUSER").andExpect(status().isBadRequest());
        assertThat(activeRole(group, owner)).isEqualTo("OWNER");
    }

    @Test
    void birebirSohbetteRolDegismez() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);

        changeRole(alice, direct, bob, "ADMIN").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_CONVERSATION_REQUIRED"));
    }

    // ------------------------------------------------------------ ayrılma (MSG-04)

    @Test
    void uyeAyrilirErisimiBiterIkinciKez404() throws Exception {
        leave(member, group).andExpect(status().isNoContent());

        assertThat(activeRole(group, member)).isNull();
        getConversation(member, group).andExpect(status().isNotFound());
        leave(member, group).andExpect(status().isNotFound());
        getConversation(owner, group).andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void sahipAyrilincaEnEskiYoneticiSahipOlur() throws Exception {
        UUID laterAdmin = newUser();
        addMembers(owner, group, laterAdmin).andExpect(status().isOk());
        changeRole(owner, group, laterAdmin, "ADMIN").andExpect(status().isOk());

        leave(owner, group).andExpect(status().isNoContent());

        assertThat(activeRole(group, admin)).isEqualTo("OWNER");
        assertThat(activeRole(group, laterAdmin)).isEqualTo("ADMIN");
        assertThat(activeRole(group, member)).isEqualTo("MEMBER");
    }

    @Test
    void yoneticiYoksaEnEskiUyeSahipOlur() throws Exception {
        UUID later = newUser();
        UUID soloOwner = newUser();
        UUID first = newUser();
        UUID solo = createGroupAndGetId(soloOwner, first);
        addMembers(soloOwner, solo, later).andExpect(status().isOk());

        leave(soloOwner, solo).andExpect(status().isNoContent());

        assertThat(activeRole(solo, first)).isEqualTo("OWNER");
        assertThat(activeRole(solo, later)).isEqualTo("MEMBER");
    }

    @Test
    void sonUyeAyrilincaGrupBosKalir() throws Exception {
        UUID soloOwner = newUser();
        UUID only = newUser();
        UUID solo = createGroupAndGetId(soloOwner, only);

        leave(soloOwner, solo).andExpect(status().isNoContent());
        leave(only, solo).andExpect(status().isNoContent());

        assertThat(activeMemberCount(solo)).isZero();
        getConversation(only, solo).andExpect(status().isNotFound());
        getConversation(soloOwner, solo).andExpect(status().isNotFound());
    }

    @Test
    void birebirSohbettenAyrilinmaz() throws Exception {
        UUID alice = newUser();
        UUID direct = openDirectAndGetId(alice, newUser());

        leave(alice, direct).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_CONVERSATION_REQUIRED"));
        removeMember(alice, direct, alice).andExpect(status().isConflict());
        assertThat(activeRole(direct, alice)).isEqualTo("MEMBER");
    }
}
