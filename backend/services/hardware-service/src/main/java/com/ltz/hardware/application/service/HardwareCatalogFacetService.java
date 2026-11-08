package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.response.HardwareCatalogFacetsResponse;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareCatalogFacetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareCatalogFacetService {
    private final HardwareCatalogFacetRepository facets;
    private final CurrentActor actor;

    public HardwareCatalogFacetService(HardwareCatalogFacetRepository facets, CurrentActor actor) {
        this.facets = facets;
        this.actor = actor;
    }

    @Transactional(readOnly = true)
    public HardwareCatalogFacetsResponse facets(ComponentCategory category) {
        actor.requireUserId();
        return new HardwareCatalogFacetsResponse(category, facets.facets(category));
    }
}
