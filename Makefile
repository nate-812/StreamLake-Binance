.PHONY: help build test lint status package-jobs check

help:
	@echo "Available commands:"
	@echo "  make build         - Build all components (Flink, Spring, Frontend)"
	@echo "  make test          - Run tests"
	@echo "  make status        - Check local services status"
	@echo "  make package-jobs  - Package Flink jobs only"
	@echo "  make check         - Run local DataSentry checks"

build: package-jobs
	@echo "Building API server..."
	cd api-server && mvn clean package -DskipTests
	@echo "Building frontend..."
	cd frontend && npm install && npm run build

package-jobs:
	@echo "Packaging Flink stream jobs..."
	cd stream-jobs && mvn clean package -DskipTests

test:
	@echo "Running tests..."
	cd stream-jobs && mvn test
	cd api-server && mvn test

status:
	@echo "Checking services status..."
	@if [ -d "ops/checks" ]; then ./ops/checks/check_streamlake_status.sh; fi

check:
	@echo "Running security and freshness checks..."
	@if [ -d "ops/checks" ]; then ./ops/checks/check_kafka_topics.sh; fi
	@if [ -d "ops/checks" ]; then ./ops/checks/check_flink_jobs.sh; fi
