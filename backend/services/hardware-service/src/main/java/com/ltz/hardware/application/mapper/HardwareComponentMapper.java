package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.request.SpecificationsRequest;
import com.ltz.hardware.api.dto.response.HardwareComponentResponse;
import com.ltz.hardware.api.dto.response.SpecificationsResponse;
import com.ltz.hardware.domain.entity.HardwareComponent;
import com.ltz.hardware.domain.entity.HardwareSpecifications;
import org.springframework.stereotype.Component;

@Component
public class HardwareComponentMapper {
    public HardwareSpecifications toSpecifications(SpecificationsRequest request) {
        if (request == null) return null;
        return new HardwareSpecifications(request.capacityGb(), request.clockMhz(), request.coreCount(),
                request.powerWatts(), request.memoryType(), request.socket(), request.interfaceType(), request.formFactor());
    }

    public HardwareComponentResponse toResponse(HardwareComponent component) {
        var specs = component.getSpecifications();
        var response = specs == null ? null : new SpecificationsResponse(specs.getCapacityGb(), specs.getClockMhz(),
                specs.getCoreCount(), specs.getPowerWatts(), specs.getMemoryType(), specs.getSocket(),
                specs.getInterfaceType(), specs.getFormFactor());
        return new HardwareComponentResponse(component.getId(), component.getCategory(), component.getProductId(),
                component.getBrandId(), component.getModelName(), component.getBrandName(), component.getQuantity(),
                component.getSource(), component.getVersion(), response);
    }
}
