package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.response.HardwareBrandResponse;
import com.ltz.hardware.api.dto.response.HardwareProductResponse;
import com.ltz.hardware.api.dto.response.SpecificationsResponse;
import com.ltz.hardware.domain.entity.HardwareBrand;
import com.ltz.hardware.domain.entity.HardwareProduct;
import com.ltz.hardware.domain.enums.CatalogStatus;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class HardwareCatalogMapper {
    public HardwareBrandResponse brand(HardwareBrand brand) {
        return new HardwareBrandResponse(brand.getId(), brand.getName(), brand.getStatus(), brand.getVersion());
    }

    public HardwareProductResponse product(HardwareProduct product) {
        var specs = product.getSpecifications();
        var specification = specs == null ? null : new SpecificationsResponse(specs.getCapacityGb(), specs.getClockMhz(),
                specs.getCoreCount(), specs.getPowerWatts(), specs.getMemoryType(), specs.getSocket(),
                specs.getInterfaceType(), specs.getFormFactor());
        var brands = product.getBrands().stream().filter(b -> b.getStatus() == CatalogStatus.ACTIVE)
                .sorted(Comparator.comparing(HardwareBrand::getNormalizedName).thenComparing(HardwareBrand::getId))
                .map(this::brand).toList();
        return new HardwareProductResponse(product.getId(), product.getCategory(), product.getModel(),
                brands, specification, product.getStatus(), product.getVersion());
    }
}
