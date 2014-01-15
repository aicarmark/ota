#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional eng

LOCAL_STATIC_JAVA_LIBRARIES := android-common android-support-v13 libAWSCore libAWSDdb libAWSS3

LOCAL_SRC_FILES := $(call all-java-files-under, src)
#LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_PACKAGE_NAME := OTAClient
# Certificate should be platform
LOCAL_CERTIFICATE := platform

LOCAL_DEX_PREOPT := false

#LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

# Include AWS libraries
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := eng
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libAWSCore:jar/aws-android-sdk-1.7.0-core.jar \
										libAWSDdb:jar/aws-android-sdk-1.7.0-ddb.jar \
										libAWSS3:jar/aws-android-sdk-1.7.0-s3.jar
include $(BUILD_MULTI_PREBUILT)

