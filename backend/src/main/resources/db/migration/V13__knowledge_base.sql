-- Aşama 16: knowledge base. Staff write articles; customers read the PUBLIC ones. Plain text only.
-- Votes are one per user per article (helpful or not helpful) and can be changed.

CREATE TABLE kb_articles (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    category   VARCHAR(16)  NOT NULL
        CONSTRAINT kb_articles_category_chk CHECK (category IN ('HARDWARE', 'SOFTWARE', 'NETWORK', 'ACCESS', 'OTHER')),
    visibility VARCHAR(16)  NOT NULL
        CONSTRAINT kb_articles_visibility_chk CHECK (visibility IN ('DRAFT', 'INTERNAL', 'PUBLIC')),
    created_by VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_by VARCHAR(64)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_kb_articles_title ON kb_articles (lower(title));

CREATE TABLE kb_article_votes (
    article_id BIGINT      NOT NULL REFERENCES kb_articles (id) ON DELETE CASCADE,
    user_id    VARCHAR(64) NOT NULL,
    helpful    BOOLEAN     NOT NULL,
    voted_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (article_id, user_id)
);

INSERT INTO kb_articles (title, body, category, visibility, created_by, created_at, updated_by, updated_at) VALUES
    ('VPN bağlantısı kurulamıyor',
     E'1. İnternet bağlantınızın çalıştığını doğrulayın.\n2. VPN istemcisini tamamen kapatıp yeniden açın.\n3. Parolanızı yakın zamanda değiştirdiyseniz yeni parolayla giriş yapın.\n4. Sorun sürerse hata mesajının ekran görüntüsüyle talep açın.',
     'NETWORK', 'PUBLIC', 'system', now(), 'system', now()),
    ('Parolamı unuttum',
     E'Giriş ekranındaki "Parolamı unuttum" bağlantısını kullanın. Doğrulama kodu kayıtlı e-posta adresinize gelir.\nKodu alamıyorsanız talep açıp kategori olarak Erişim seçin.',
     'ACCESS', 'PUBLIC', 'system', now(), 'system', now()),
    ('Yazıcı çıktı vermiyor',
     E'1. Yazıcının açık ve kağıt/toner durumunun uygun olduğunu kontrol edin.\n2. Yazdırma kuyruğundaki takılı işleri iptal edin.\n3. Yazıcıyı kapatıp 30 saniye sonra açın.\n4. Düzelmezse yazıcının üzerindeki envanter numarasıyla talep açın.',
     'HARDWARE', 'PUBLIC', 'system', now(), 'system', now()),
    ('Ağ yazıcısı sürücü kurulumu (iç not)',
     E'Sürücü paketi paylaşımlı klasördeki Yazicilar dizinindedir. Kurulumdan sonra varsayılan yazıcıyı kullanıcıya göre ayarlayın.',
     'SOFTWARE', 'INTERNAL', 'system', now(), 'system', now());
