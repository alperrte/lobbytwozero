-- Bounded, typed technical filters; no extension or external search dependency.
CREATE INDEX ix_hardware_products_active_capacity ON hardware_products(category, capacity_gb, id)
    WHERE status = 'ACTIVE' AND capacity_gb IS NOT NULL;
CREATE INDEX ix_hardware_products_active_clock ON hardware_products(category, clock_mhz, id)
    WHERE status = 'ACTIVE' AND clock_mhz IS NOT NULL;
CREATE INDEX ix_hardware_products_active_cores ON hardware_products(category, core_count, id)
    WHERE status = 'ACTIVE' AND core_count IS NOT NULL;
CREATE INDEX ix_hardware_products_active_power ON hardware_products(category, power_watts, id)
    WHERE status = 'ACTIVE' AND power_watts IS NOT NULL;
