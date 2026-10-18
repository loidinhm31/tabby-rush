---
title: "Android App Header Redesign: Brand Logo Replacement & Button Spacing"
description: "Replace brand name text with compact logo in mobile top bar (<840dp) to maximize horizontal space for action buttons"
status: completed
priority: P2
effort: 3h
branch: main
tags: [ui, android, compose-multiplatform, accessibility]
created: 2026-10-19
---

# Android App Header Redesign: Brand Logo Replacement & Button Spacing

## Overview
TabbyRush's compact/mobile UI layout (<840dp in `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt`) currently renders a wide text title ("TabbyRush" in `titleLarge`, taking ~110dp) alongside five action buttons ("Add", "Inbox"/"Tasks", "Settings", "Notices", "Leave"). On standard Android mobile viewports (360dp–412dp), this causes horizontal space exhaustion and awkward label squishing/wrapping.

This plan provisions a high-resolution logo resource in `shared/src/commonMain/composeResources/drawable/` and replaces the text brand name with a compact 36×36dp logo badge featuring TalkBack accessibility semantics, while optimizing action button padding, preserving >=48dp touch targets, and introducing a horizontal scroll container fail-safe for narrow screens and 200% font scale.

## Phases
1. [Phase 01: Logo Asset Provisioning](phase-01-logo-asset-provisioning.md) - Status: Completed
2. [Phase 02: Compact Header Redesign](phase-02-compact-header-redesign.md) - Status: Completed
3. [Phase 03: Multiplatform Validation & Review](phase-03-validation-and-review.md) - Status: Completed
