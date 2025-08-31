SET search_path TO payment;
-- Payment Service Database Schema
-- 결제 관련 테이블 생성

-- 결제 정보 테이블
CREATE TABLE payments (
                          id BIGSERIAL PRIMARY KEY,
                          payment_id VARCHAR(100) UNIQUE NOT NULL, -- 결제 고유 ID
                          order_id VARCHAR(100) NOT NULL, -- 주문 ID
                          user_id BIGINT NOT NULL, -- 사용자 ID
                          merchant_uid VARCHAR(100) UNIQUE NOT NULL, -- 가맹점 주문번호
                          imp_uid VARCHAR(100) UNIQUE, -- 아임포트 거래번호
                          amount DECIMAL(10,2) NOT NULL, -- 결제 금액
                          currency VARCHAR(3) NOT NULL DEFAULT 'KRW', -- 통화
                          payment_method VARCHAR(50) NOT NULL, -- 결제 수단
                          payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- 결제 상태
                          payment_provider VARCHAR(50) NOT NULL DEFAULT 'IAMPORT', -- 결제 제공업체
                          pg_provider VARCHAR(50), -- PG사
                          pg_tid VARCHAR(100), -- PG사 거래번호
                          receipt_url TEXT, -- 영수증 URL
                          buyer_name VARCHAR(100), -- 구매자 이름
                          buyer_email VARCHAR(255), -- 구매자 이메일
                          buyer_phone VARCHAR(20), -- 구매자 전화번호
                          buyer_address TEXT, -- 구매자 주소
                          buyer_postcode VARCHAR(10), -- 구매자 우편번호
                          product_name VARCHAR(255) NOT NULL, -- 상품명
                          product_count INTEGER DEFAULT 1, -- 상품 수량
                          custom_data JSONB, -- 추가 데이터
                          fail_reason TEXT, -- 실패 사유
                          cancelled_at TIMESTAMP, -- 취소 시각
                          cancel_reason TEXT, -- 취소 사유
                          cancel_amount DECIMAL(10,2), -- 취소 금액
                          refund_holder VARCHAR(100), -- 환불 수취인
                          refund_bank VARCHAR(50), -- 환불 은행
                          refund_account VARCHAR(50), -- 환불 계좌
                          paid_at TIMESTAMP, -- 결제 완료 시각
                          failed_at TIMESTAMP, -- 결제 실패 시각
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 결제 방법 제약조건 (CARD, TRANS, VBANK, PHONE, CULTURELAND, SMARTCULTURE, BOOKNLIFE, KAKAOPAY, PAYCO, LPAY, SSGPAY, TOSSPAY)
ALTER TABLE payments ADD CONSTRAINT chk_payment_method
    CHECK (payment_method IN ('CARD', 'TRANS', 'VBANK', 'PHONE', 'CULTURELAND',
                              'SMARTCULTURE', 'BOOKNLIFE', 'KAKAOPAY', 'PAYCO',
                              'LPAY', 'SSGPAY', 'TOSSPAY'));

-- 결제 상태 제약조건 (PENDING, PAID, CANCELLED, FAILED, PARTIAL_CANCELLED)
ALTER TABLE payments ADD CONSTRAINT chk_payment_status
    CHECK (payment_status IN ('PENDING', 'PAID', 'CANCELLED', 'FAILED', 'PARTIAL_CANCELLED'));

-- 결제 제공업체 제약조건 (IAMPORT, TOSS, KAKAO, NAVER)
ALTER TABLE payments ADD CONSTRAINT chk_payment_provider
    CHECK (payment_provider IN ('IAMPORT', 'TOSS', 'KAKAO', 'NAVER'));

-- 인덱스 생성
CREATE INDEX idx_payments_payment_id ON payments(payment_id);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_merchant_uid ON payments(merchant_uid);
CREATE INDEX idx_payments_imp_uid ON payments(imp_uid);
CREATE INDEX idx_payments_status ON payments(payment_status);
CREATE INDEX idx_payments_method ON payments(payment_method);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_payments_paid_at ON payments(paid_at);

-- 결제 내역 테이블 (상세 로그)
CREATE TABLE payment_histories (
                                   id BIGSERIAL PRIMARY KEY,
                                   payment_id BIGINT NOT NULL,
                                   status VARCHAR(50) NOT NULL,
                                   previous_status VARCHAR(50),
                                   amount DECIMAL(10,2),
                                   description TEXT,
                                   pg_response JSONB, -- PG사 응답 데이터
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

CREATE INDEX idx_payment_histories_payment_id ON payment_histories(payment_id);
CREATE INDEX idx_payment_histories_status ON payment_histories(status);
CREATE INDEX idx_payment_histories_created_at ON payment_histories(created_at);

-- 환불 정보 테이블
CREATE TABLE refunds (
                         id BIGSERIAL PRIMARY KEY,
                         payment_id BIGINT NOT NULL,
                         refund_id VARCHAR(100) UNIQUE NOT NULL,
                         refund_amount DECIMAL(10,2) NOT NULL,
                         refund_reason TEXT NOT NULL,
                         refund_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                         refund_method VARCHAR(50), -- 환불 방법
                         refund_account_info JSONB, -- 환불 계좌 정보
                         pg_refund_id VARCHAR(100), -- PG사 환불 ID
                         refunded_at TIMESTAMP,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

-- 환불 상태 제약조건 (PENDING, COMPLETED, FAILED)
ALTER TABLE refunds ADD CONSTRAINT chk_refund_status
    CHECK (refund_status IN ('PENDING', 'COMPLETED', 'FAILED'));

CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_refund_id ON refunds(refund_id);
CREATE INDEX idx_refunds_status ON refunds(refund_status);
CREATE INDEX idx_refunds_created_at ON refunds(created_at);

-- 업데이트 트리거 생성
-- CREATE TRIGGER update_payments_updated_at
--     BEFORE UPDATE ON payments
--     FOR EACH ROW
--     EXECUTE FUNCTION update_updated_at_column();
--
-- CREATE TRIGGER update_refunds_updated_at
--     BEFORE UPDATE ON refunds
--     FOR EACH ROW
--     EXECUTE FUNCTION update_updated_at_column();

-- 웹훅 로그 테이블 (PortOne 웹훅 처리 로그)
CREATE TABLE webhook_logs (
                              id BIGSERIAL PRIMARY KEY,
                              webhook_id VARCHAR(100),
                              imp_uid VARCHAR(100),
                              merchant_uid VARCHAR(100),
                              status VARCHAR(50),
                              webhook_type VARCHAR(50),
                              payload JSONB,
                              processed BOOLEAN DEFAULT FALSE,
                              error_message TEXT,
                              retry_count INTEGER DEFAULT 0,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              processed_at TIMESTAMP
);

CREATE INDEX idx_webhook_logs_imp_uid ON webhook_logs(imp_uid);
CREATE INDEX idx_webhook_logs_merchant_uid ON webhook_logs(merchant_uid);
CREATE INDEX idx_webhook_logs_status ON webhook_logs(status);
CREATE INDEX idx_webhook_logs_processed ON webhook_logs(processed);
CREATE INDEX idx_webhook_logs_created_at ON webhook_logs(created_at);
