# Canonical development commands for the Vast Hall sideload drop.
# This repo's only source is README.md, which documents the released APK.

MARKDOWNLINT ?= $(HOME)/.npm-global/bin/markdownlint

.PHONY: check lint verify verify-offline

## Run all checks (lint + release verification).
check: lint verify

## Lint the Markdown docs.
lint:
	$(MARKDOWNLINT) README.md

## Download the released APK and verify it matches README (tag/size/md5/manifest).
verify:
	python3 scripts/verify-release.py

## Validate README internal consistency without network access.
verify-offline:
	python3 scripts/verify-release.py --offline
