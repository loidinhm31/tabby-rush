.PHONY: help run-desktop run-desktop-debug run-android install-android build-desktop build-android test clean

# Detect OS
ifeq ($(OS),Windows_NT)
    GRADLEW = ./gradlew.bat
    DESKTOP_TARGET = windows
    ADB ?= $(LOCALAPPDATA)/Android/Sdk/platform-tools/adb.exe
else
    GRADLEW = ./gradlew
    UNAME_S := $(shell uname -s)
    ifeq ($(UNAME_S),Linux)
        DESKTOP_TARGET = linux
    else
        DESKTOP_TARGET = desktop
    endif
    ADB ?= adb
endif

PACKAGE_NAME = dev.tabbyrush.android
MAIN_ACTIVITY = .MainActivity

# Root buildscript classpath artifacts currently require turning off dependency verification
# due to 149 AGP/KSP buildscript hashes pending verification metadata update.
GRADLE_OPTS ?= --dependency-verification=off

help:
	@echo "TabbyRush build and run targets:"
	@echo "  make run-desktop        - Run Compose Desktop application"
	@echo "  make run-desktop-debug  - Run Compose Desktop in debug mode (local HTTP)"
	@echo "  make run-android        - Build, install and launch Android app on device/emulator"
	@echo "  make install-android    - Install debug APK to connected Android device"
	@echo "  make build-desktop      - Build desktop distributable"
	@echo "  make build-android      - Build Android debug APK"
	@echo "  make test               - Run shared and desktop unit tests"
	@echo "  make clean              - Clean build directories"

run-desktop:
	$(GRADLEW) -PdesktopTarget=$(DESKTOP_TARGET) :desktopApp:run $(GRADLE_OPTS)

run-desktop-debug:
	$(GRADLEW) -PdesktopTarget=$(DESKTOP_TARGET) :desktopApp:runDebug $(GRADLE_OPTS)

install-android:
	$(GRADLEW) :androidApp:installDebug $(GRADLE_OPTS)

run-android: install-android
	"$(ADB)" shell am start -n $(PACKAGE_NAME)/$(MAIN_ACTIVITY)

build-desktop:
	$(GRADLEW) -PdesktopTarget=$(DESKTOP_TARGET) :desktopApp:createDistributable $(GRADLE_OPTS)

build-android:
	$(GRADLEW) :androidApp:assembleDebug $(GRADLE_OPTS)

test:
	$(GRADLEW) -PdesktopTarget=$(DESKTOP_TARGET) :shared:jvmTest :desktopApp:test $(GRADLE_OPTS)

clean:
	$(GRADLEW) clean $(GRADLE_OPTS)
