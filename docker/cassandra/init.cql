CREATE KEYSPACE IF NOT EXISTS item_service
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
