-- Test database initialization script
-- This script only sets up PostgreSQL extensions
-- Tables will be created by Hibernate with ddl-auto=create-drop
-- Test data will be inserted by test setup methods

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Log initialization completion
SELECT 'Test database extensions initialized successfully' as initialization_status;