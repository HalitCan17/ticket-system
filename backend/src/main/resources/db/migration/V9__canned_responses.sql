-- Aşama 12: team-wide reply templates. Managers maintain them, agents and managers insert them into comments.
-- Placeholders such as {müşteri} are filled in by the client when inserting; the stored text is kept as written.

CREATE TABLE canned_responses (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(100) NOT NULL,
    body       TEXT         NOT NULL,
    visibility VARCHAR(16)  NOT NULL
        CONSTRAINT canned_responses_visibility_chk CHECK (visibility IN ('INTERNAL', 'EXTERNAL')),
    created_by VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_by VARCHAR(64)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_canned_responses_title ON canned_responses (lower(title));

INSERT INTO canned_responses (title, body, visibility, created_by, created_at, updated_by, updated_at) VALUES
    ('Ek bilgi isteği',
     'Merhaba {müşteri}, talebinizi ({talep}) inceliyoruz. Sorunu daha iyi anlayabilmemiz için hata mesajının ekran görüntüsünü ve sorunun ne zamandan beri yaşandığını paylaşabilir misiniz?',
     'EXTERNAL', 'system', now(), 'system', now()),
    ('Yeniden başlatma önerisi',
     'Merhaba {müşteri}, lütfen bilgisayarınızı tamamen kapatıp yeniden açın ve sorunun devam edip etmediğini bize bildirin.',
     'EXTERNAL', 'system', now(), 'system', now()),
    ('Çözüldü – onay isteği',
     'Merhaba {müşteri}, {talep} numaralı talebinizdeki sorunu giderdik. Her şey yolundaysa talebi onaylayıp kapatabilirsiniz. İyi çalışmalar, {ben}',
     'EXTERNAL', 'system', now(), 'system', now()),
    ('Üçüncü taraf bekleniyor',
     'Tedarikçiden yanıt bekleniyor; gelince talep güncellenecek.',
     'INTERNAL', 'system', now(), 'system', now());
