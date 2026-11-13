package com.ltz.lfp.application.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.lfp.application.port.BlockCheckPort;
import com.ltz.lfp.application.port.BlockCheckUnavailableException;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

import lombok.RequiredArgsConstructor;

/**
 * LFP-17: social-service'in engel sorgusunu ilan etkileşimlerine uygular. Engelin yönü bilinmez ve önemsizdir: iki
 * kullanıcı arasında herhangi bir yönde engel varsa birbirlerinin ilanlarını görmez ve iletişim başlatamaz. Engel
 * durumu öğrenilemezse işlem fail-closed {@code 503 BLOCK_CHECK_UNAVAILABLE} ile reddedilir; engel yokmuş gibi
 * davranılmaz. Kullanıcının kendi ilanı için Social'a sorulmaz.
 */
@Component
@RequiredArgsConstructor
class BlockGuard {

    private final BlockCheckPort blocks;

    /** @return {@code owners} içinden {@code viewer} ile arasında engel olanlar (en çok 100 farklı sahip) */
    Set<UUID> blockedOwners(UUID viewer, Collection<UUID> owners) {
        Set<UUID> others = new LinkedHashSet<>(owners);
        others.remove(viewer);
        if (others.isEmpty()) {
            return Set.of();
        }
        try {
            return blocks.blockedAmong(viewer, others);
        } catch (BlockCheckUnavailableException exception) {
            throw new LfpException(LfpErrorCode.BLOCK_CHECK_UNAVAILABLE, exception);
        }
    }

    boolean isBlocked(UUID viewer, UUID owner) {
        return blockedOwners(viewer, List.of(owner)).contains(owner);
    }
}
