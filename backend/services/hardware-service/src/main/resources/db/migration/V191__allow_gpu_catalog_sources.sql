-- V191 explicitly allocated to Hardware by the user; V1-V10 remain unchanged.
ALTER TABLE hardware_product_sources DROP CONSTRAINT hardware_product_sources_category_check;
ALTER TABLE hardware_product_sources ADD CONSTRAINT ck_hardware_product_sources_category
    CHECK (category IN ('CPU','GPU','MOTHERBOARD','RAM','STORAGE'));
