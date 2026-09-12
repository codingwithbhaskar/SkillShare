-- ============================================================================
-- V8 - Categorize skills so the booking-creation form can filter them by
-- the service already picked (e.g. selecting "Plumbing" only offers
-- plumbing-related skills, not all ~50).
--
-- Two parts:
--   1. ALTER TABLE - add the nullable skills.category column (same shape
--      as services.category, which already existed).
--   2. UPDATE by exact skill_name - backfills every skill this project
--      has ever seeded: the 6 canonical names from 06_seed_data_v3.sql
--      (V5) and the 50 from db/bulk_seed_large.sql (run manually against
--      Neon on 2026-09-12, since it's a one-off data script, not a
--      migration - this UPDATE is what actually categorizes those rows
--      on the already-populated live database). A skill name that
--      doesn't exist on a given database simply matches zero rows -
--      harmless.
--
-- Canonical source: db/01_schema_v3.sql (skills table) and
-- db/06_seed_data_v3.sql (the 6 canonical INSERTs) both carry this
-- change already, per db/README.md's process.
-- ============================================================================

ALTER TABLE skills ADD COLUMN category VARCHAR(100);

UPDATE skills SET category = 'Electrical' WHERE skill_name IN (
    'Wiring', 'Fuse Box Repair',
    'Electrical Wiring', 'Circuit Breaker Repair', 'Solar Panel Installation',
    'Ceiling Fan Installation', 'Inverter Repair'
);

UPDATE skills SET category = 'Plumbing' WHERE skill_name IN (
    'Pipe Fitting', 'Drain Cleaning',
    'Leak Repair', 'Bathroom Fitting Installation', 'Water Tank Cleaning',
    'Drainage Cleaning'
);

UPDATE skills SET category = 'Cleaning' WHERE skill_name IN (
    'Deep House Cleaning', 'Kitchen Deep Cleaning', 'Sofa Shampooing',
    'Carpet Cleaning', 'Window Cleaning'
);

UPDATE skills SET category = 'Painting' WHERE skill_name IN (
    'Interior Wall Painting', 'Exterior Wall Painting', 'Waterproofing',
    'Wood Polishing', 'Texture Painting'
);

UPDATE skills SET category = 'Carpentry' WHERE skill_name IN (
    'Furniture Assembly',
    'Custom Carpentry', 'Door Repair', 'Cabinet Making', 'Wood Cutting'
);

UPDATE skills SET category = 'Gardening' WHERE skill_name IN (
    'Lawn Mowing', 'Garden Maintenance', 'Tree Trimming', 'Landscaping',
    'Plant Potting'
);

UPDATE skills SET category = 'Appliance Repair' WHERE skill_name IN (
    'AC Repair',
    'AC Installation', 'AC Gas Refilling', 'Refrigerator Repair',
    'Washing Machine Repair', 'Microwave Repair', 'Water Purifier Service'
);

UPDATE skills SET category = 'Pest Control' WHERE skill_name IN (
    'Pest Control - General', 'Termite Control', 'Cockroach Control', 'Rodent Control'
);

UPDATE skills SET category = 'Renovation' WHERE skill_name IN (
    'Wall Tiling', 'Floor Tiling', 'Marble Polishing'
);

UPDATE skills SET category = 'Fabrication' WHERE skill_name IN (
    'Welding', 'Grill Fabrication'
);

UPDATE skills SET category = 'Roofing' WHERE skill_name IN (
    'Roof Waterproofing', 'Roof Repair'
);

UPDATE skills SET category = 'Construction' WHERE skill_name IN (
    'Masonry Work'
);

UPDATE skills SET category = 'Home Security' WHERE skill_name IN (
    'CCTV Installation', 'Smart Lock Installation'
);
