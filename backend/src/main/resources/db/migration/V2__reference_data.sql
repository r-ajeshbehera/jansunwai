-- Reference data needed in every environment (departments, default SLA policies).
-- Wards with demo boundaries live in db/demo because real ward maps would come from the municipality.

INSERT INTO departments (code, name) VALUES
 ('PUBLIC_WORKS', 'Public Works (Roads)'),
 ('WATER_SUPPLY', 'Water Supply'),
 ('DRAINAGE',     'Drainage & Sewerage'),
 ('SANITATION',   'Sanitation & Solid Waste'),
 ('ELECTRICAL',   'Electrical (Streetlights)'),
 ('GENERAL',      'General Administration');

-- Default policies (department_id NULL). Configurable sample values, NOT official municipal standards.
INSERT INTO sla_configurations (department_id, priority, resolution_hours, warning_percent) VALUES
 (NULL, 'HIGH',   24,  75),
 (NULL, 'MEDIUM', 72,  75),
 (NULL, 'LOW',    168, 75);
