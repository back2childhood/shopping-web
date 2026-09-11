CREATE KEYSPACE IF NOT EXISTS item_service
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS item_service.items_by_id (
    id text PRIMARY KEY,
    userid bigint,
    name text,
    description text,
    price decimal,
    currency text,
    createdat timestamp,
    updatedat timestamp,
    version bigint
);
