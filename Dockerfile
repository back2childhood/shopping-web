version: "3.9"

services:

  # --------------------------
  # Databases
  # --------------------------

  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: shopping
    ports:
      - "3306:3306"
    networks:
      - shopping-net

  redis:
    image: redis:latest
    ports:
      - "6379:6379"
    networks:
      - shopping-net

  cassandra:
    image: cassandra:4.1
    environment:
      CASSANDRA_CLUSTER_NAME: "ShoppingCluster"
      CASSANDRA_DC: "datacenter1"
    ports:
      - "9042:9042"
    networks:
      - shopping-net

  kafka:
    image: bitnami/kafka:3.6
    environment:
      KAFKA_ENABLE_KRAFT: yes
      KAFKA_CFG_NODE_ID: 1
      KAFKA_CFG_PROCESS_ROLES: "controller,broker"
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: "1@kafka:9093"
      KAFKA_CFG_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093"
      KAFKA_CFG_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:9092"
    ports:
      - "9092:9092"
    networks:
      - shopping-net

  # --------------------------
  # Microservices
  # --------------------------

  account-service:
    build: ./AccountService
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/shopping
    depends_on:
      - mysql
    ports:
      - "8081:8081"
    networks:
      - shopping-net

  auth-service:
    build: ./AuthService
    depends_on:
      - account-service
    ports:
      - "8082:8082"
    networks:
      - shopping-net

  item-service:
    build: ./ItemService
    depends_on:
      - mysql
    ports:
      - "8083:8083"
    networks:
      - shopping-net

  order-service:
    build: ./OrderService
    depends_on:
      - kafka
      - cassandra
    ports:
      - "8084:8084"
    networks:
      - shopping-net

  cart-service:
    build: ./CartService
    depends_on:
      - redis
    ports:
      - "8085:8085"
    networks:
      - shopping-net

  # --------------------------
  # Frontend
  # --------------------------

  webapp:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - item-service
      - auth-service
      - order-service
      - cart-service
      - account-service
    networks:
      - shopping-net

networks:
  shopping-net: