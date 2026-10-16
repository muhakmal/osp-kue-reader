.PHONY: help test build lint clean run

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

test: ## Run JVM unit test suite
	./gradlew testDebugUnitTest

build: ## Build debug APK
	./gradlew assembleDebug

lint: ## Run Android Lint checks
	./gradlew lintDebug

clean: ## Clean build cache and outputs
	./gradlew clean
