-- Aşama 15b: business-hours calendar for the SLA clocks.
-- One settings row (id = 1). While disabled (the default) SLA clocks count every hour, as before.

CREATE TABLE business_hours (
    id         SMALLINT     PRIMARY KEY CONSTRAINT business_hours_single_row CHECK (id = 1),
    enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    zone_id    VARCHAR(64)  NOT NULL DEFAULT 'Europe/Istanbul',
    -- Comma separated java.time.DayOfWeek names
    work_days  VARCHAR(80)  NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
    start_time TIME         NOT NULL DEFAULT '09:00',
    end_time   TIME         NOT NULL DEFAULT '18:00',
    updated_by VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT business_hours_order CHECK (start_time < end_time)
);

INSERT INTO business_hours (id) VALUES (1);

CREATE TABLE holidays (
    day  DATE         PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

-- Public holidays with a fixed date. Holidays that move every year (religious ones) are added by managers.
INSERT INTO holidays (day, name)
SELECT make_date(y, m, d), name
FROM (VALUES (1, 1, 'Yılbaşı'),
             (4, 23, 'Ulusal Egemenlik ve Çocuk Bayramı'),
             (5, 1, 'Emek ve Dayanışma Günü'),
             (5, 19, 'Atatürk''ü Anma, Gençlik ve Spor Bayramı'),
             (7, 15, 'Demokrasi ve Millî Birlik Günü'),
             (8, 30, 'Zafer Bayramı'),
             (10, 29, 'Cumhuriyet Bayramı')) AS fixed (m, d, name)
CROSS JOIN (VALUES (2026), (2027)) AS years (y);
