.PHONY: help test build lint clean publish-local

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

test: ## Run unit tests on kue-reader SDK
	./gradlew :kue-reader:testDebugUnitTest

build: ## Build kue-reader AAR and sample APK
	./gradlew :kue-reader:assembleRelease :sample:assembleDebug

lint: ## Run lint on kue-reader SDK
	./gradlew :kue-reader:lint

clean: ## Clean build cache and outputs
	./gradlew clean

publish-local: ## Publish kue-reader to local Maven (~/.m2)
	./gradlew :kue-reader:publishToMavenLocal
