-- =====================================================================================
-- DEMONSTRATION DATA ONLY (dev and test profiles). Fictional people, demo ward boundaries.
-- Every demo account password: Demo@1234 (development only!)
-- Times are relative to now() so SLA deadlines, escalations and dashboards always look current.
-- Complaints in status SUBMITTED are processed by the AI pipeline when the application starts.
-- =====================================================================================

-- Simplified rectangular boundaries around real Bhubaneswar localities (NOT official ward maps).
INSERT INTO wards (code, name, boundary) VALUES
 ('W01', 'Saheed Nagar (DEMO)', ST_MakeEnvelope(85.835, 20.280, 85.855, 20.300, 4326)),
 ('W02', 'Old Town (DEMO)',     ST_MakeEnvelope(85.825, 20.230, 85.845, 20.250, 4326)),
 ('W03', 'Patia (DEMO)',        ST_MakeEnvelope(85.810, 20.343, 85.830, 20.363, 4326)),
 ('W04', 'Nayapalli (DEMO)',    ST_MakeEnvelope(85.795, 20.285, 85.815, 20.305, 4326));

-- Password hash = BCrypt("Demo@1234")
INSERT INTO users (full_name, email, phone, password_hash, role, department_id) VALUES
 ('City Admin (Demo)',            'admin@jansunwai.demo',            NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'ADMIN', NULL),
 ('Roads Officer (Demo)',         'roads.officer@jansunwai.demo',    NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'DEPARTMENT_OFFICER', (SELECT id FROM departments WHERE code = 'PUBLIC_WORKS')),
 ('Roads Field Team A (Demo)',    'roads.field@jansunwai.demo',      NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'FIELD_OFFICER', (SELECT id FROM departments WHERE code = 'PUBLIC_WORKS')),
 ('Roads Field Team B (Demo)',    'roads.field2@jansunwai.demo',     NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'FIELD_OFFICER', (SELECT id FROM departments WHERE code = 'PUBLIC_WORKS')),
 ('Water Officer (Demo)',         'water.officer@jansunwai.demo',    NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'DEPARTMENT_OFFICER', (SELECT id FROM departments WHERE code = 'WATER_SUPPLY')),
 ('Water Field Team (Demo)',      'water.field@jansunwai.demo',      NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'FIELD_OFFICER', (SELECT id FROM departments WHERE code = 'WATER_SUPPLY')),
 ('Drainage Officer (Demo)',      'drainage.officer@jansunwai.demo', NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'DEPARTMENT_OFFICER', (SELECT id FROM departments WHERE code = 'DRAINAGE')),
 ('Drainage Field Team (Demo)',   'drainage.field@jansunwai.demo',   NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'FIELD_OFFICER', (SELECT id FROM departments WHERE code = 'DRAINAGE')),
 ('Sanitation Officer (Demo)',    'sanitation.officer@jansunwai.demo', NULL,       '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'DEPARTMENT_OFFICER', (SELECT id FROM departments WHERE code = 'SANITATION')),
 ('Sanitation Field Team (Demo)', 'sanitation.field@jansunwai.demo', NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'FIELD_OFFICER', (SELECT id FROM departments WHERE code = 'SANITATION')),
 ('Electrical Officer (Demo)',    'electrical.officer@jansunwai.demo', NULL,       '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'DEPARTMENT_OFFICER', (SELECT id FROM departments WHERE code = 'ELECTRICAL')),
 ('Electrical Field Team (Demo)', 'electrical.field@jansunwai.demo', NULL,         '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'FIELD_OFFICER', (SELECT id FROM departments WHERE code = 'ELECTRICAL')),
 ('Ananya Das (Demo)',            'citizen1@jansunwai.demo',         '9000000101', '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'CITIZEN', NULL),
 ('Rakesh Sahoo (Demo)',          'citizen2@jansunwai.demo',         '9000000102', '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'CITIZEN', NULL),
 ('Priya Mishra (Demo)',          'citizen3@jansunwai.demo',         '9000000103', '$2a$12$WqQ9oB4wH0miXWH9wk2YQeXXaimEwktYzievjQlfmqYDePztcyDEW', 'CITIZEN', NULL);

-- A department-specific policy example: drainage HIGH = 12 hours (monsoon flooding).
INSERT INTO sla_configurations (department_id, priority, resolution_hours, warning_percent)
VALUES ((SELECT id FROM departments WHERE code = 'DRAINAGE'), 'HIGH', 12, 70);

-- ---------- demo complaints ---------------------------------------------------------
CREATE TEMPORARY TABLE demo_c (
    n INT, citizen TEXT, title TEXT, description TEXT, lang TEXT, ward TEXT, lat NUMERIC, lng NUMERIC,
    category TEXT, dept TEXT, priority TEXT, status TEXT, created_h NUMERIC, accepted_h NUMERIC, field TEXT);

INSERT INTO demo_c VALUES
 -- Saheed Nagar market pothole (master of a future duplicate group)
 (1,  'citizen1', 'Big pothole near the main market', 'There is a big pothole near the main market. Two-wheelers are falling at night.', 'en', 'W01', 20.290100, 85.845200, 'ROAD_DAMAGE', 'PUBLIC_WORKS', 'HIGH', 'ASSIGNED', 30, 28, 'roads.field'),
 (2,  'citizen2', 'Streetlights off on main road', 'Four streetlights on the main road near the bus stop are not working for a week.', 'en', 'W01', 20.287300, 85.842100, 'STREETLIGHT', 'ELECTRICAL', 'MEDIUM', 'IN_PROGRESS', 60, 58, 'electrical.field'),
 (3,  'citizen3', 'Garbage not collected', 'Garbage has not been collected from our lane for five days and it smells badly.', 'en', 'W01', 20.293400, 85.850300, 'GARBAGE', 'SANITATION', 'MEDIUM', 'RESOLVED', 120, 118, 'sanitation.field'),
 (4,  'citizen1', 'Drain overflowing on the street', 'The drain is blocked and dirty water is overflowing onto the street near the temple.', 'en', 'W01', 20.284800, 85.838900, 'DRAINAGE', 'DRAINAGE', 'HIGH', 'ASSIGNED', 20, 19, 'drainage.field'),
 (5,  'citizen2', 'Low water pressure', 'Very low water pressure in the morning supply for the whole colony.', 'en', 'W01', 20.296100, 85.847700, 'WATER_SUPPLY', 'WATER_SUPPLY', 'LOW', 'APPROVED', 10, 9, NULL),
 -- Old Town
 (6,  'citizen3', 'Road broken after pipeline work', 'The road was dug for pipeline work and never repaired. Big holes everywhere.', 'en', 'W02', 20.238800, 85.834500, 'ROAD_DAMAGE', 'PUBLIC_WORKS', 'MEDIUM', 'RESOLUTION_SUBMITTED', 64, 60, 'roads.field2'),
 (7,  'citizen1', 'Public toilet unusable', 'The public toilet near the temple pond has no water and is very dirty.', 'en', 'W02', 20.242200, 85.838100, 'SANITATION', 'SANITATION', 'MEDIUM', 'CLOSED', 300, 296, 'sanitation.field'),
 (8,  'citizen2', 'Drain blocked with plastic', 'The roadside drain is completely blocked with plastic waste. Water stagnates for days.', 'en', 'W02', 20.235900, 85.829900, 'DRAINAGE', 'DRAINAGE', 'MEDIUM', 'IN_PROGRESS', 54, 50, 'drainage.field'),
 (9,  'citizen3', 'No water supply for two days', 'No water supply in our street for two days. Elderly people are suffering.', 'en', 'W02', 20.245500, 85.840800, 'WATER_SUPPLY', 'WATER_SUPPLY', 'HIGH', 'ASSIGNED', 40, 38, 'water.field'),
 -- Patia
 (10, 'citizen1', 'Streetlight pole damaged', 'A streetlight pole is bent after a truck hit it. It may fall on the road.', 'en', 'W03', 20.352200, 85.819100, 'STREETLIGHT', 'ELECTRICAL', 'HIGH', 'RESOLVED', 70, 69, 'electrical.field'),
 (11, 'citizen2', 'Garbage dumped on empty plot', 'People are dumping garbage on the empty plot next to the school.', 'en', 'W03', 20.356600, 85.823300, 'GARBAGE', 'SANITATION', 'LOW', 'APPROVED', 50, 45, NULL),
 (12, 'citizen3', 'Waterlogging after rain', 'Every rain the road near the IT park gets waterlogged knee deep.', 'en', 'W03', 20.348400, 85.815500, 'DRAINAGE', 'DRAINAGE', 'HIGH', 'IN_PROGRESS', 12, 10, 'drainage.field'),
 -- Nayapalli
 (13, 'citizen1', 'Potholes on service road', 'Many potholes on the service road. Auto rickshaws are getting damaged.', 'en', 'W04', 20.296700, 85.804400, 'ROAD_DAMAGE', 'PUBLIC_WORKS', 'LOW', 'ASSIGNED', 130, 120, 'roads.field'),
 (14, 'citizen2', 'Dirty water from tap', 'Brown and smelly water is coming from the tap since yesterday.', 'en', 'W04', 20.291100, 85.800800, 'WATER_SUPPLY', 'WATER_SUPPLY', 'HIGH', 'IN_PROGRESS', 21, 20, 'water.field'),
 (15, 'citizen3', 'Request for new park bench', 'Please install benches in the park. This is a suggestion.', 'en', 'W04', 20.299900, 85.809900, 'OTHER', 'GENERAL', 'LOW', 'REJECTED', 150, NULL, NULL),
 -- new complaints, processed by the AI pipeline at startup (duplicates of #1, multilingual)
 (16, 'citizen2', 'Road damaged near market entrance', 'Road damaged near the market entrance, a large hole beside the main market road.', 'en', 'W01', 20.290400, 85.845600, NULL, NULL, NULL, 'SUBMITTED', 3, NULL, NULL),
 (17, 'citizen3', 'बाज़ार के पास गड्ढा', 'मुख्य बाज़ार के पास सड़क पर बड़ा गड्ढा है, रात में दुर्घटना हो सकती है।', 'hi', 'W01', 20.289800, 85.844900, NULL, NULL, NULL, 'SUBMITTED', 2, NULL, NULL),
 (18, 'citizen1', 'ପାଣି ଆସୁନାହିଁ', 'ଆମ ଗଳିରେ ତିନି ଦିନ ହେଲା ପାଣି ଆସୁନାହିଁ। ଦୟାକରି ଶୀଘ୍ର ବ୍ୟବସ୍ଥା କରନ୍ତୁ।', 'or', 'W02', 20.245100, 85.840100, NULL, NULL, NULL, 'SUBMITTED', 1.5, NULL, NULL),
 (19, 'citizen2', 'Sewage smell from open manhole', 'An open manhole near the bus stop. Sewage smell and it is dangerous for children.', 'en', 'W03', 20.351000, 85.817800, NULL, NULL, NULL, 'SUBMITTED', 1, NULL, NULL),
 (20, 'citizen3', 'Garbage pile near school gate', 'Garbage pile near the school gate is not cleared, dogs spread it on the road.', 'en', NULL, NULL, NULL, NULL, NULL, NULL, 'SUBMITTED', 0.5, NULL, NULL);

INSERT INTO complaints (tracking_id, citizen_id, title, description, language, ward_id, latitude, longitude,
                        category, department_id, priority, classification_source, classified_by, classified_at,
                        status, accepted_at, sla_due_at, resolved_at, closed_at, rejection_reason, created_at, updated_at)
SELECT 'JS-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('complaint_tracking_seq')::text, 6, '0'),
       u.id, d.title, d.description, d.lang, w.id, d.lat, d.lng,
       CASE WHEN d.status = 'SUBMITTED' THEN NULL ELSE d.category END,
       dep.id,
       CASE WHEN d.status = 'SUBMITTED' THEN NULL ELSE d.priority END,
       CASE WHEN d.status IN ('SUBMITTED') THEN 'NONE' ELSE 'OFFICER_CONFIRMED' END,
       CASE WHEN d.status IN ('SUBMITTED') THEN NULL ELSE off.id END,
       CASE WHEN d.status IN ('SUBMITTED') THEN NULL ELSE now() - make_interval(secs => (d.created_h - 0.5) * 3600) END,
       d.status,
       CASE WHEN d.accepted_h IS NULL THEN NULL ELSE now() - make_interval(secs => d.accepted_h * 3600) END,
       CASE WHEN d.accepted_h IS NULL THEN NULL
            ELSE now() - make_interval(secs => d.accepted_h * 3600)
                 + make_interval(hours => CASE d.priority WHEN 'HIGH' THEN (CASE WHEN d.dept = 'DRAINAGE' THEN 12 ELSE 24 END)
                                                          WHEN 'MEDIUM' THEN 72 ELSE 168 END) END,
       CASE WHEN d.status IN ('RESOLVED', 'CLOSED') THEN now() - make_interval(secs => (d.accepted_h * 0.4) * 3600) END,
       CASE WHEN d.status = 'CLOSED' THEN now() - make_interval(secs => (d.accepted_h * 0.2) * 3600) END,
       CASE WHEN d.status = 'REJECTED' THEN 'This is a suggestion, not a grievance. Please use the city feedback portal.' END,
       now() - make_interval(secs => d.created_h * 3600),
       now() - make_interval(secs => d.created_h * 3600)
FROM demo_c d
JOIN users u ON u.email = d.citizen || '@jansunwai.demo'
LEFT JOIN wards w ON w.code = d.ward
LEFT JOIN departments dep ON dep.code = d.dept AND d.status <> 'SUBMITTED'
LEFT JOIN users off ON off.role = 'DEPARTMENT_OFFICER' AND off.department_id = dep.id
ORDER BY d.n;

-- assignments for complaints that are being worked on
INSERT INTO complaint_assignments (complaint_id, field_officer_id, assigned_by, assigned_at)
SELECT c.id, f.id, o.id, c.accepted_at + interval '30 minutes'
FROM demo_c d
JOIN complaints c ON c.title = d.title
JOIN users f ON f.email = d.field || '@jansunwai.demo'
JOIN users o ON o.role = 'DEPARTMENT_OFFICER' AND o.department_id = c.department_id
WHERE d.status IN ('ASSIGNED', 'IN_PROGRESS', 'RESOLUTION_SUBMITTED');

-- status history: submitted, then (for processed complaints) a short path to the current status
INSERT INTO complaint_status_history (complaint_id, from_status, to_status, note, changed_by, created_at)
SELECT c.id, NULL, 'SUBMITTED', 'Complaint submitted', c.citizen_id, c.created_at FROM complaints c;

INSERT INTO complaint_status_history (complaint_id, from_status, to_status, note, changed_by, created_at)
SELECT c.id, 'UNDER_REVIEW', CASE WHEN c.status = 'REJECTED' THEN 'REJECTED' ELSE 'APPROVED' END,
       CASE WHEN c.status = 'REJECTED' THEN c.rejection_reason ELSE 'Classification approved by department officer' END,
       c.classified_by, COALESCE(c.accepted_at, c.created_at + interval '1 hour')
FROM complaints c WHERE c.status NOT IN ('SUBMITTED');

INSERT INTO complaint_status_history (complaint_id, from_status, to_status, note, changed_by, created_at)
SELECT c.id, 'APPROVED', c.status, 'Demo history: ' || lower(replace(c.status, '_', ' ')), c.classified_by, c.accepted_at + interval '45 minutes'
FROM complaints c WHERE c.status NOT IN ('SUBMITTED', 'REJECTED', 'APPROVED');

DROP TABLE demo_c;
