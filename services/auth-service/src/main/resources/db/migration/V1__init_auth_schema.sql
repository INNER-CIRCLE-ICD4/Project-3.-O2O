-- UUID 확장 활성화
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    driver_license_number VARCHAR(100),
    driver_verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_provider_provider_id ON users(provider, provider_id);
CREATE INDEX idx_users_email ON users(email);
