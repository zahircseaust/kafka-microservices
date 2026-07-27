#!/bin/bash
# Setup script for local PostgreSQL databases (Linux/Mac)
# Prerequisites: PostgreSQL installed locally with psql in PATH
# Usage: ./setup-local-db.sh [postgres_user] [postgres_password]

PGUSER=${1:-postgres}
export PGPASSWORD=${2:-postgres}

echo "Creating databases for Kafka Microservices Demo..."
echo "Using PostgreSQL user: $PGUSER"

create_db_if_not_exists() {
    local dbname=$1
    if psql -U "$PGUSER" -lqt | cut -d \| -f 1 | grep -qw "$dbname"; then
        echo "$dbname already exists, skipping..."
    else
        echo "Creating $dbname..."
        psql -U "$PGUSER" -c "CREATE DATABASE $dbname;"
    fi
}

create_db_if_not_exists "orderdb"
create_db_if_not_exists "inventorydb"
create_db_if_not_exists "notificationdb"
create_db_if_not_exists "authdb"

echo ""
echo "Done! Databases created:"
psql -U "$PGUSER" -c "SELECT datname FROM pg_database WHERE datname IN ('orderdb', 'inventorydb', 'notificationdb', 'authdb');"
