-- V13: CPCB battery-recycler master data — the 13-row seed sample, shipped as a proper migration
-- so a fresh database always has it (Flyway tracks this like schema — runs once, never re-runs,
-- no app-startup logic needed). Verbatim from eprid_recyclers_seed_sample.csv, including its two
-- deliberately partial rows (Attero, Gravita — see data_quality_notes on each). A full CPCB re-pull
-- later goes through POST /api/v1/cpcb-recyclers/ingest, not another migration — this file is a
-- one-time seed, not the ongoing ingestion path.
--
-- Composite scores are NOT seeded here — they're derived data (recomputed as the scoring formula
-- evolves), not master data, so they don't belong in a migration. CpcbRecyclerScoringBackfillRunner
-- computes them on startup for any recycler that doesn't have one yet.
SET search_path TO eprid;

INSERT INTO cpcb_recyclers (
    id, cpcb_id, cpcb_uuid, recycler_name, recycler_address, state_id, recycler_gst_no,
    consent_air_expiry, consent_water_expiry, hwmd_valid_expiry, dic_valid_expiry,
    recycler_type_raw, recycling_capacity, latitude, longitude,
    authorized_name, authorized_email, authorized_mobile, source_created_at, certificate_flag,
    staff_no, worker_no, inspection_status, internal_app_status,
    data_quality_partial_capture, data_quality_notes
) VALUES
    ('73407728-6722-4390-a868-1c8acfb801ec', '659', '8f130b40-fffd-4ac3-ab6c-8631a7469190', 'A P PRODUCT', 'KalaAmb', '7', '02AANFA2267J1ZJ', '2028-03-31', '2028-03-31', '2028-03-31', '2004-04-04', NULL, NULL, 30.500502, 77.220147, 'Ishan Aggarwal', 'ishan.aggarwal03@gmail.com', '9736287566', '2024-05-22 09:38:53.000000+00', '1', NULL, NULL, NULL, NULL, false, NULL),
    ('05f61188-bdc8-4172-b92f-46e11e7d54c8', '658', 'fb81bc8a-6da7-4434-864b-d6b98f567ead', 'A R INDUSTRIES', 'PLOT NO D87,MIDC TASAWADE,KARAD', '12', '27AAOFA0259P1ZY', '2026-03-31', '2026-03-31', '2028-03-31', '2023-04-19', NULL, NULL, 17.363636, 74.104658, 'AVADHESH VERMA', 'abverma976@gmail.com', '9822572276', '2024-05-22 09:35:41.000000+00', '1', NULL, NULL, NULL, NULL, false, NULL),
    ('f3ca6cc9-9634-4e55-a97f-0dc7a71f4266', '634', 'ab8863d8-3970-47e2-a41f-2d6f3d600533', 'A R METAL & ALLIED INDUSTRIES', 'D-6,D-7 BEGARAJJPUR INDUSTRIAL AREA', '23', '09AATFA8736E2ZZ', '2028-12-31', '2028-12-31', '2028-12-31', '2022-05-10', NULL, NULL, 29.374343, 77.697372, 'MEHBOOB AHMAD', 'a.r.metal1973@gmail.com', '9358347360', NULL, '1', NULL, NULL, NULL, NULL, false, NULL),
    ('c57c377b-4c9b-4058-bc9e-2848fd3a487b', '573', 'f2b8f940-f1be-44c8-af61-61619d4d98aa', 'GOTECH BATTERIES AND ALLOYS', 'SY NO 1225/2-1-B., Thippavarapadu Village, Gudur Mandal, SPSR Nellore, Andhra Pradesh 524106', '1', '37AATFG1466D1Z8', '2025-02-28', '2025-02-28', '2025-02-28', '2025-02-28', NULL, NULL, 14.130301, 79.769049, 'BHASKAR RAO AMMINENI', 'gotechba@gmail.com', '9248004477', '2024-03-30 13:28:22.000000+00', '1', 0, 0, NULL, NULL, false, NULL),
    ('897fb623-4aff-4c5d-b5d0-8f0a93280702', '960', 'fd386b2a-1155-4cf0-9a2c-d3d756edcf6d', 'GRAFEL INDIA PRIVATE LIMITED', 'Plot No. SP 4-9, RIICO Industrial Area, Neemrana, Kotputli Behror, Rajasthan 301705', '19', '08AAKCG8296K1ZR', '2030-06-30', '2030-06-30', '2030-06-30', '2024-07-02', NULL, NULL, 27.936383, 76.390492, 'Ankit Goyal', 'info@grafelindia.com', '9257112551', '2025-11-11 11:26:23.000000+00', '1', 0, 0, NULL, NULL, false, NULL),
    ('f8a33f4d-ac82-4a6b-92c7-c81838889c7a', '925', '49681015-d3b3-4d4f-bca2-44497b458e20', 'MOHD SARTAJ', 'N-2, Industrial Area, Begrajpur, Muzaffarnagar', '23', '09BLUPS8694G1ZV', '2027-12-31', '2027-12-31', '2028-12-27', '2031-11-10', NULL, NULL, 29.418467, 77.68009, 'MOHD SARTAJ', 'princemetal910@gmail.com', '8630242490', '2025-09-01 15:16:21.000000+00', '1', 0, 0, NULL, NULL, false, NULL),
    ('a1e98c3c-3bfe-49b6-8708-cc7bc04ff9a9', '728', '58974f4d-50f8-4ba6-bd62-e94f77758b8e', 'MOHD SHAHID', 'L-10, Industrial Area, Begrajpur, Muzaffarnagar', '23', '09CKVPS0838P1ZY', '2024-12-31', '2024-12-31', '2029-02-07', '2024-12-11', NULL, NULL, 28.66432, 77.17942, 'Mohd Shahid', 'shahidrna76@gmail.com', '8171778184', '2024-07-27 16:54:05.000000+00', '1', 0, 0, NULL, NULL, false, NULL),
    ('01ee2f76-9172-47c2-a795-78cd4ad6ca79', '709', 'f491fb79-931c-4e1e-8bfd-6ee0da75f375', 'MOHD ZAHID', 'NASIRPUR, PANCHENDA ROAD, AREA SUBHASH NAGAR', '23', NULL, '2028-12-31', '2028-12-31', '2028-12-31', '2017-10-22', NULL, NULL, 27.825941994162, 80.500166729074, 'MOHD ZAHID', 'onlyfor7games@gmail.com', '7037009967', '2024-07-05 11:34:00.000000+00', '1', 0, 0, NULL, NULL, false, NULL),
    ('34c88709-1978-4d07-9378-42a07185c500', '403', '7dc15145-b032-4eb1-a5a0-5e9e6e3a7097', 'SANT METAL INDUSTRIES', 'SHEIKHU ROAD MALOUT', '18', '03AJRPG8928P1ZF', '2028-06-30', '2028-06-30', '2028-06-30', NULL, NULL, NULL, 30.195897, 74.520238, 'JYOTI SAROOP GROVER', 'jyotigrover79@gmail.com', '9569596008', '2023-12-02 05:39:34.000000+00', '1', 0, 0, NULL, NULL, false, NULL),
    ('12b8691a-8040-4af9-a285-d76095ab23b3', '973', 'b5f5bcb2-669f-4154-9529-89431be690ab', 'SANDEEP SINGHAL', 'J 11, INDUSTRIAL AREA, BEGRAJPUR, Muzaffarnagar, Uttar Pradesh, 251201', '23', '09BAJPS2903N2Z5', '2026-12-31', '2026-12-31', '2027-07-18', '2021-10-11', NULL, NULL, 29.374102, 77.699543, 'SANDEEP SINGHAL', 'industriesaaryametal@gmail.com', '8191081325', '2025-12-18 15:56:15.000000+00', '1', 0, 0, NULL, NULL, false, NULL),
    ('4d44ac1a-dfd7-4e52-a5fd-5ee93dc97ab4', '493', 'c2c9c292-50ea-452d-870b-872bc6d433ac', 'Santosh Pigment & Chemical Industries Pvt. Ltd', 'HE 05,06,07,Gopalpur Industrial Area, Sikandrabad', '23', NULL, '2027-12-31', '2027-12-31', '2025-05-17', '2020-08-08', NULL, NULL, 28.48514, 77.651867, 'sumit singh', NULL, NULL, NULL, '1', NULL, NULL, NULL, NULL, false, NULL),
    -- Attero: no source id/uuid in the seed (partial-capture artifact) and a genuinely ragged
    -- source row (short its InternalAppStatus column) — staff_no=0 is a confirmed value,
    -- worker_no/internal_app_status are truly absent, not zero.
    ('b16c349d-bd2a-4ef8-97e3-dcae7a876829', NULL, NULL, 'ATTERO RECYCLING PRIVATE LIMITED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'R2: Only Battery Dismantling and Physical separation (Processing till Black Mass Generation) of all types of battery except Lead acid battery#,R3: Refiners  Only Black Mass Processor (Processing till metals are obtained in compound form) of all types of battery except Lead acid battery#,R4: Battery Dismantling, Physical Separation and Refining (Black Mass Processing) of all types of battery except Lead acid battery', 7380, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, 3, NULL, true, 'missing_source_id: CPCB row id/uuid not captured -- likely a partial-capture artifact, not confirmed to be absent at source | partial_capture: 7 of 10 optional fields blank in this row -- treat blanks here as unassessed, not confirmed-missing (see CpcbRecyclerIngestionService)'),
    ('615f2040-88c1-44aa-a668-318a2e949206', '126', 'bb51ac5e-3bcf-41ed-ab92-8a03d369e6f6', 'GRAVITA INDIA LIMITED', 'Saurabh, Chittora Road, Harsulia Mod, Diggi-Malpura Road, Tehsil- Phagi', '19', '08AAACG6753F1ZM', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, true, 'partial_capture: 9 of 10 optional fields blank in this row -- treat blanks here as unassessed, not confirmed-missing (see CpcbRecyclerIngestionService)');

-- Attero's R2/R3/R4 categories, parsed out of its '#,'-joined recycler_type_raw above.
INSERT INTO cpcb_recycler_authorizations (id, recycler_id, category_code, category_label) VALUES
    ('d1a1c1a0-a001-4a00-9a00-000000000001', 'b16c349d-bd2a-4ef8-97e3-dcae7a876829', 'R2', 'Only Battery Dismantling and Physical separation (Processing till Black Mass Generation) of all types of battery except Lead acid battery'),
    ('d1a1c1a0-a001-4a00-9a00-000000000002', 'b16c349d-bd2a-4ef8-97e3-dcae7a876829', 'R3', 'Refiners  Only Black Mass Processor (Processing till metals are obtained in compound form) of all types of battery except Lead acid battery'),
    ('d1a1c1a0-a001-4a00-9a00-000000000003', 'b16c349d-bd2a-4ef8-97e3-dcae7a876829', 'R4', 'Battery Dismantling, Physical Separation and Refining (Black Mass Processing) of all types of battery except Lead acid battery');
