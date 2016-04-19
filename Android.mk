LOCAL_PATH:=$(call my-dir)

# Software Name : RCS IMS Stack
include $(CLEAR_VARS)

# This is the target being built. (Name of APK)
LOCAL_PACKAGE_NAME := TctRcsCore
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := platform
# Link against the current Android SDK.
LOCAL_SDK_VERSION := current


LOCAL_MODULE_PATH := $(TARGET_OUT)/priv-app
# LOCAL_JAVA_LIBRARIES += bouncycastle 

# 
LOCAL_STATIC_JAVA_LIBRARIES := android-common rcs_api guava

define all-Iaidl-files-except
$(patsubst ./%,%, \
  $(shell cd $(LOCAL_PATH) ; \
          find -L $(1) -name "I*.aidl" -and -not -name $2) \
 )
endef

# Only compile source java files in this apk.
LOCAL_SRC_FILES += \
	$(call all-Iaidl-files-except,  src/api, "*Configuration.aidl" )\
	$(call all-java-files-under,  src/api)\
	$(call all-java-files-under,  src/jsip)\
	$(call all-java-files-under,  src/dns)\
	$(call all-java-files-under,  src/cert)\
	$(call all-java-files-under, src/main)

# Add AIDL files (the parcelable must not be added in SRC_FILES, but included in LOCAL_AIDL_INCLUDES)
#LOCAL_SRC_FILES += $(call all-Iaidl-files-under, src)

# FRAMEWORKS_BASE_JAVA_SRC_DIRS comes from build/core/pathmap.mk
#LOCAL_AIDL_INCLUDES += $(FRAMEWORKS_BASE_JAVA_SRC_DIRS)
LOCAL_AIDL_INCLUDES += $(LOCAL_PATH)/src/api

# Add classes used by reflexion
LOCAL_PROGUARD_FLAG_FILES := proguard.cfg

# Tell it to build an APK
include $(BUILD_PACKAGE)


##########################################################################
# Build the RCS API : rcs_api.jar
##########################################################################

# Software Name : RCS IMS Stack
include $(CLEAR_VARS)

# This is the target being built. 
LOCAL_MODULE:= rcs_api

LOCAL_SRC_FILES += \
	$(call all-Iaidl-files-except,  src/api, "*Configuration.aidl" )\
	$(call all-java-files-under,  src/api)\

LOCAL_AIDL_INCLUDES += $(LOCAL_PATH)/src/api

# Tell it to build an APK
include $(BUILD_STATIC_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
