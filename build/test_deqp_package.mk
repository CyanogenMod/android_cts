# Copyright (C) 2012 The Android Open Source Project
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
# Builds dEQP test description XMLs needed by CTS.
#

CTS_DEQP_CONFIG_PATH := $(call my-dir)

cts_library_xmls:=$(foreach xml_file, $(call find-files-in-subdirs, external/deqp/android/cts/master, 'com.drawelements.deqp.$(DEQP_API).*xml', .), $(CTS_TESTCASES_OUT)/$(xml_file))
$(cts_library_xmls) : PRIVATE_API := $(DEQP_API)
$(cts_library_xmls) : PRIVATE_TEST_NAME := $(DEQP_TEST_NAME)
$(cts_library_xmls) : PRIVATE_TEST_PACKAGE := com.drawelements.deqp.$(DEQP_API)
$(cts_library_xmls) : PRIVATE_DUMMY_CASELIST := $(CTS_DEQP_CONFIG_PATH)/deqp_dummy_test_list
$(cts_library_xmls) : $(CTS_TESTCASES_OUT)/%.xml: external/deqp/android/cts/master/%.xml $(CTS_EXPECTATIONS) $(CTS_UNSUPPORTED_ABIS) $(CTS_XML_GENERATOR)
	$(hide) echo Generating test description $@
	$(hide) mkdir -p $(CTS_TESTCASES_OUT)
# Query build ABIs by routing a dummy test list through xml generator and parse result
	$(hide) $(eval supported_abi_attr := $(shell $(CTS_XML_GENERATOR) -t dummyTest \
										-n dummyName \
										-p invalid.dummy \
										-e $(CTS_EXPECTATIONS) \
										-b $(CTS_UNSUPPORTED_ABIS) \
										-a $(CTS_TARGET_ARCH) \
										< $(PRIVATE_DUMMY_CASELIST) \
										| grep --only-matching -e " abis=\"[^\"]*\""))
# Patch xml caselist with supported abi
	$(hide) $(SED_EXTENDED) -e 's:^(\s*)<Test ((.[^/]|[^/])*)(/?)>$$:\1<Test \2 $(supported_abi_attr)\4>:' \
				< $< \
				> $@
