SET search_path TO auth;
-- Auth Service Database Schema
-- 사용자 관련 테이블 생성

-- 사용자 정보 테이블
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password VARCHAR(255),
                       username VARCHAR(100) NOT NULL,
                       phone_number VARCHAR(20),
                       user_role VARCHAR(50) NOT NULL DEFAULT 'USER',
                       user_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
                       profile_image_url TEXT,
                       birth_date DATE,
                       gender VARCHAR(10),
                       provider VARCHAR(50), -- OAuth provider (GOOGLE, KAKAO, NAVER)
                       provider_id VARCHAR(255), -- OAuth provider의 사용자 ID
                       last_login_at TIMESTAMP,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 사용자 역할 (USER, ADMIN, PROVIDER, CONSUMER)
ALTER TABLE users ADD CONSTRAINT chk_user_role
    CHECK (user_role IN ('USER', 'ADMIN', 'PROVIDER', 'CONSUMER'));

-- 사용자 상태 (ACTIVE, INACTIVE, SUSPENDED, DELETED)
ALTER TABLE users ADD CONSTRAINT chk_user_status
    CHECK (user_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED'));

-- 성별 (MALE, FEMALE, OTHER)
ALTER TABLE users ADD CONSTRAINT chk_gender
    CHECK (gender IN ('MALE', 'FEMALE', 'OTHER') OR gender IS NULL);

-- OAuth 제공자 (GOOGLE, KAKAO, NAVER, LOCAL)
ALTER TABLE users ADD CONSTRAINT chk_provider
    CHECK (provider IN ('GOOGLE', 'KAKAO', 'NAVER', 'LOCAL') OR provider IS NULL);

-- 인덱스 생성
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_provider ON users(provider, provider_id);
CREATE INDEX idx_users_status ON users(user_status);
CREATE INDEX idx_users_role ON users(user_role);
CREATE INDEX idx_users_created_at ON users(created_at);

-- 리프레시 토큰 테이블
CREATE TABLE refresh_tokens (
                                id BIGSERIAL PRIMARY KEY,
                                user_id BIGINT NOT NULL,
                                token VARCHAR(500) UNIQUE NOT NULL,
                                expires_at TIMESTAMP NOT NULL,
                                is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- 사용자 프로필 테이블 (확장 정보)
CREATE TABLE user_profiles (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               nickname VARCHAR(50),
                               bio TEXT,
                               location VARCHAR(255),
                               website VARCHAR(255),
                               social_media JSONB, -- 소셜 미디어 링크들
                               preferences JSONB,  -- 사용자 설정
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_nickname ON user_profiles(nickname);

-- 업데이트 트리거 함수
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- 업데이트 트리거 생성
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_refresh_tokens_updated_at
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
