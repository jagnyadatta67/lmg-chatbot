-- ======================
-- MAIN MENUS
-- ======================
INSERT INTO menu (id, title, active, display_order) VALUES
(1, 'Offline', TRUE, 1),
(2, 'Online', TRUE, 2),
(3, 'Policy Details', TRUE, 3);

-- ======================
-- SUBMENUS (OFFLINE)
-- ======================
INSERT INTO sub_menu (id, title, type, display_order, menu_id,active) VALUES
(101, 'Order', 'dynamic', 1, 1,1),
(102, 'Return Policy', 'static', 2, 1,1),
(103, 'Exchange', 'static', 3, 1,1);

-- ======================
-- SUBMENUS (ONLINE)
-- ======================
INSERT INTO sub_menu (id, title, type, display_order, menu_id,active) VALUES
(201, 'Order', 'dynamic', 1, 2,1),
(202, 'Cancel', 'static', 2, 2,1),
(203, 'Return', 'static', 3, 2,1);

-- ======================
-- SUBMENUS (POLICY DETAILS)
-- ======================
INSERT INTO sub_menu (id, title, type, display_order, menu_id,active) VALUES
(301, 'Order', 'static', 1, 3,1),
(302, 'Return', 'static', 2, 3,1),
(303, 'Cancel', 'static', 3, 3,1),
(304, 'Shipping', 'static', 4, 3,1);
