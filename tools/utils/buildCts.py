#!/usr/bin/python

# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Module for generating CTS test descriptions and test plans."""

import glob
import os
import re
import shutil
import subprocess
import sys
import xml.dom.minidom as dom
from cts import tools
from multiprocessing import Pool

def GetSubDirectories(root):
  """Return all directories under the given root directory."""
  return [x for x in os.listdir(root) if os.path.isdir(os.path.join(root, x))]


def GetMakeFileVars(makefile_path):
  """Extracts variable definitions from the given make file.

  Args:
    makefile_path: Path to the make file.

  Returns:
    A dictionary mapping variable names to their assigned value.
  """
  result = {}
  pattern = re.compile(r'^\s*([^:#=\s]+)\s*:=\s*(.*?[^\\])$', re.MULTILINE + re.DOTALL)
  stream = open(makefile_path, 'r')
  content = stream.read()
  for match in pattern.finditer(content):
    result[match.group(1)] = match.group(2)
  stream.close()
  return result


class CtsBuilder(object):
  """Main class for generating test descriptions and test plans."""

  def __init__(self, argv):
    """Initialize the CtsBuilder from command line arguments."""
    if len(argv) != 6:
      print 'Usage: %s <testRoot> <ctsOutputDir> <tempDir> <androidRootDir> <docletPath>' % argv[0]
      print ''
      print 'testRoot:       Directory under which to search for CTS tests.'
      print 'ctsOutputDir:   Directory in which the CTS repository should be created.'
      print 'tempDir:        Directory to use for storing temporary files.'
      print 'androidRootDir: Root directory of the Android source tree.'
      print 'docletPath:     Class path where the DescriptionGenerator doclet can be found.'
      sys.exit(1)
    self.test_root = sys.argv[1]
    self.out_dir = sys.argv[2]
    self.temp_dir = sys.argv[3]
    self.android_root = sys.argv[4]
    self.doclet_path = sys.argv[5]

    self.test_repository = os.path.join(self.out_dir, 'repository/testcases')
    self.plan_repository = os.path.join(self.out_dir, 'repository/plans')
    self.definedplans_repository = os.path.join(self.android_root, 'cts/tests/plans')

  def GenerateTestDescriptions(self):
    """Generate test descriptions for all packages."""
    pool = Pool(processes=2)

    # generate test descriptions for android tests
    results = []
    pool.close()
    pool.join()
    return sum(map(lambda result: result.get(), results))

  def __WritePlan(self, plan, plan_name):
    print 'Generating test plan %s' % plan_name
    plan.Write(os.path.join(self.plan_repository, plan_name + '.xml'))

  def GenerateTestPlans(self):
    """Generate default test plans."""
    # TODO: Instead of hard-coding the plans here, use a configuration file,
    # such as test_defs.xml
    packages = []
    descriptions = sorted(glob.glob(os.path.join(self.test_repository, '*.xml')))
    for description in descriptions:
      doc = tools.XmlFile(description)
      packages.append(doc.GetAttr('TestPackage', 'appPackageName'))
    # sort the list to give the same sequence based on name
    packages.sort()

    plan = tools.TestPlan(packages)
    plan.Exclude('android\.performance.*')
    self.__WritePlan(plan, 'CTS')
    self.__WritePlan(plan, 'CTS-TF')

    plan = tools.TestPlan(packages)
    plan.Exclude('android\.performance.*')
    plan.Exclude('android\.media\.cts\.StreamingMediaPlayerTest.*')
    # Test plan to not include media streaming tests
    self.__WritePlan(plan, 'CTS-No-Media-Stream')

    plan = tools.TestPlan(packages)
    plan.Exclude('android\.performance.*')
    self.__WritePlan(plan, 'SDK')

    plan.Exclude(r'android\.signature')
    plan.Exclude(r'android\.core.*')
    self.__WritePlan(plan, 'Android')

    plan = tools.TestPlan(packages)
    plan.Include(r'android\.core\.tests.*')
    plan.Exclude(r'android\.core\.tests\.libcore.\package.\harmony*')
    self.__WritePlan(plan, 'Java')

    # TODO: remove this once the tests are fixed and merged into Java plan above.
    plan = tools.TestPlan(packages)
    plan.Include(r'android\.core\.tests\.libcore.\package.\harmony*')
    self.__WritePlan(plan, 'Harmony')

    plan = tools.TestPlan(packages)
    plan.Include(r'android\.core\.vm-tests-tf')
    self.__WritePlan(plan, 'VM-TF')

    plan = tools.TestPlan(packages)
    plan.Include(r'android\.tests\.appsecurity')
    self.__WritePlan(plan, 'AppSecurity')

    # hard-coded white list for PDK plan
    plan.Exclude('.*')
    plan.Include('android\.aadb')
    plan.Include('android\.bluetooth')
    plan.Include('android\.graphics.*')
    plan.Include('android\.hardware')
    plan.Include('android\.media')
    plan.Exclude('android\.mediastress')
    plan.Include('android\.net')
    plan.Include('android\.opengl.*')
    plan.Include('android\.renderscript')
    plan.Include('android\.telephony')
    plan.Include('android\.nativemedia.*')
    plan.Include('com\.android\.cts\..*')#TODO(stuartscott): Should PDK have all these?
    self.__WritePlan(plan, 'PDK')

    flaky_tests = BuildCtsFlakyTestList()

    # CTS Stable plan
    plan = tools.TestPlan(packages)
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-stable')

    # CTS Flaky plan - list of tests known to be flaky in lab environment
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.Include(package+'$')
      plan.IncludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-flaky')

    small_tests = BuildAospSmallSizeTestList()
    medium_tests = BuildAospMediumSizeTestList()
    new_test_packages = BuildCtsVettedNewPackagesList()

    # CTS - sub plan for public, small size tests
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    for package, test_list in small_tests.iteritems():
      plan.Include(package+'$')
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-kitkat-small')

    # CTS - sub plan for public, medium size tests
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    for package, test_list in medium_tests.iteritems():
      plan.Include(package+'$')
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-kitkat-medium')

    # CTS - sub plan for hardware tests which is public, large
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.hardware$')
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-hardware')

    # CTS - sub plan for media tests which is public, large
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.media$')
    plan.Include(r'android\.view$')
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-media')

    # CTS - sub plan for mediastress tests which is public, large
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.mediastress$')
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-mediastress')

    # CTS - sub plan for new tests that is vetted for L launch
    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    for package, test_list in new_test_packages.iteritems():
      plan.Include(package+'$')
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-l-tests')

    #CTS - sub plan for new test packages added for staging
    plan = tools.TestPlan(packages)
    for package, test_list in small_tests.iteritems():
      plan.Exclude(package+'$')
    for package, test_list in medium_tests.iteritems():
      plan.Exclude(package+'$')
    for package, tests_list in new_test_packages.iteritems():
      plan.Exclude(package+'$')
    plan.Exclude(r'android\.hardware$')
    plan.Exclude(r'android\.media$')
    plan.Exclude(r'android\.view$')
    plan.Exclude(r'android\.mediastress$')
    plan.Exclude(r'com\.android\.cts\.browserbench')
    for package, test_list in flaky_tests.iteritems():
      plan.ExcludeTests(package, test_list)
    self.__WritePlan(plan, 'CTS-staging')

    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'com\.drawelements\.')
    self.__WritePlan(plan, 'CTS-DEQP')

    plan = tools.TestPlan(packages)
    plan.Exclude('.*')
    plan.Include(r'android\.webgl')
    self.__WritePlan(plan, 'CTS-webview')


def BuildAospMediumSizeTestList():
  """ Construct a defaultdic that lists package names of medium tests
      already published to aosp. """
  return {
      'android.app' : [],
      'android.core.tests.libcore.package.libcore' : [],
      'android.core.tests.libcore.package.org' : [],
      'android.core.vm-tests-tf' : [],
      'android.dpi' : [],
      'android.host.security' : [],
      'android.net' : [],
      'android.os' : [],
      'android.permission2' : [],
      'android.security' : [],
      'android.telephony' : [],
      'android.webkit' : [],
      'android.widget' : [],
      'com.android.cts.browserbench' : []}

def BuildAospSmallSizeTestList():
  """ Construct a defaultdict that lists packages names of small tests
      already published to aosp. """
  return {
      'android.aadb' : [],
      'android.acceleration' : [],
      'android.accessibility' : [],
      'android.accessibilityservice' : [],
      'android.accounts' : [],
      'android.admin' : [],
      'android.animation' : [],
      'android.bionic' : [],
      'android.bluetooth' : [],
      'android.calendarcommon' : [],
      'android.content' : [],
      'android.core.tests.libcore.package.com' : [],
      'android.core.tests.libcore.package.conscrypt' : [],
      'android.core.tests.libcore.package.dalvik' : [],
      'android.core.tests.libcore.package.sun' : [],
      'android.core.tests.libcore.package.tests' : [],
      'android.database' : [],
      'android.dreams' : [],
      'android.drm' : [],
      'android.effect' : [],
      'android.gesture' : [],
      'android.graphics' : [],
      'android.graphics2' : [],
      'android.jni' : [],
      'android.keystore' : [],
      'android.location' : [],
      'android.nativemedia.sl' : [],
      'android.nativemedia.xa' : [],
      'android.nativeopengl' : [],
      'android.ndef' : [],
      'android.opengl' : [],
      'android.openglperf' : [],
      'android.permission' : [],
      'android.preference' : [],
      'android.preference2' : [],
      'android.provider' : [],
      'android.renderscript' : [],
      'android.rscpp' : [],
      'android.rsg' : [],
      'android.sax' : [],
      'android.signature' : [],
      'android.speech' : [],
      'android.tests.appsecurity' : [],
      'android.text' : [],
      'android.textureview' : [],
      'android.theme' : [],
      'android.usb' : [],
      'android.util' : [],
      'com.android.cts.dram' : [],
      'com.android.cts.filesystemperf' : [],
      'com.android.cts.jank' : [],
      'com.android.cts.opengl' : [],
      'com.android.cts.simplecpu' : [],
      'com.android.cts.ui' : [],
      'com.android.cts.uihost' : [],
      'com.android.cts.videoperf' : [],
      'zzz.android.monkey' : []}

def BuildCtsVettedNewPackagesList():
  """ Construct a defaultdict that maps package names that is vetted for L. """
  return {
      'android.JobScheduler' : [],
      'android.core.tests.libcore.package.harmony_annotation' : [],
      'android.core.tests.libcore.package.harmony_beans' : [],
      'android.core.tests.libcore.package.harmony_java_io' : [],
      'android.core.tests.libcore.package.harmony_java_lang' : [],
      'android.core.tests.libcore.package.harmony_java_math' : [],
      'android.core.tests.libcore.package.harmony_java_net' : [],
      'android.core.tests.libcore.package.harmony_java_nio' : [],
      'android.core.tests.libcore.package.harmony_java_util' : [],
      'android.core.tests.libcore.package.harmony_java_text' : [],
      'android.core.tests.libcore.package.harmony_javax_security' : [],
      'android.core.tests.libcore.package.harmony_logging' : [],
      'android.core.tests.libcore.package.harmony_prefs' : [],
      'android.core.tests.libcore.package.harmony_sql' : [],
      'android.core.tests.libcore.package.jsr166' : [],
      'android.core.tests.libcore.package.okhttp' : [],
      'android.display' : [],
      'android.host.theme' : [],
      'android.jdwp' : [],
      'android.location2' : [],
      'android.print' : [],
      'android.renderscriptlegacy' : [],
      'android.signature' : [],
      'android.tv' : [],
      'android.uiautomation' : [],
      'android.uirendering' : [],
      'android.webgl' : [],
      'com.drawelements.deqp.gles3' : [],
      'com.drawelements.deqp.gles31' : []}

def BuildCtsFlakyTestList():
  """ Construct a defaultdict that maps package name to a list of tests
      that are known to be flaky in the lab or not passing on userdebug builds. """
  return {
      'android.app' : [
          'cts.ActivityManagerTest#testIsRunningInTestHarness',],
      'android.dpi' : [
          'cts.DefaultManifestAttributesSdkTest#testPackageHasExpectedSdkVersion',],
      'android.hardware' : [
          'cts.CameraTest#testVideoSnapshot',
          'cts.CameraGLTest#testCameraToSurfaceTextureMetadata',
          'cts.CameraGLTest#testSetPreviewTextureBothCallbacks',
          'cts.CameraGLTest#testSetPreviewTexturePreviewCallback',],
      'android.media' : [
          'cts.DecoderTest#testCodecResetsH264WithSurface',
          'cts.StreamingMediaPlayerTest#testHLS',],
      'android.net' : [
          'cts.ConnectivityManagerTest#testStartUsingNetworkFeature_enableHipri',
          'cts.DnsTest#testDnsWorks',
          'cts.SSLCertificateSocketFactoryTest#testCreateSocket',
          'cts.SSLCertificateSocketFactoryTest#test_createSocket_bind',
          'cts.SSLCertificateSocketFactoryTest#test_createSocket_simple',
          'cts.SSLCertificateSocketFactoryTest#test_createSocket_wrapping',
          'cts.TrafficStatsTest#testTrafficStatsForLocalhost',
          'wifi.cts.NsdManagerTest#testAndroidTestCaseSetupProperly',],
      'android.os' : [
          'cts.BuildVersionTest#testReleaseVersion',
          'cts.BuildTest#testIsSecureUserBuild',],
      'android.security' : [
          'cts.BannedFilesTest#testNoSu',
          'cts.BannedFilesTest#testNoSuInPath',
          'cts.ListeningPortsTest#testNoRemotelyAccessibleListeningUdp6Ports',
          'cts.ListeningPortsTest#testNoRemotelyAccessibleListeningUdpPorts',
          'cts.PackageSignatureTest#testPackageSignatures',
          'cts.SELinuxDomainTest#testSuDomain',
          'cts.SELinuxHostTest#testAllEnforcing',],
      'android.webkit' : [
          'cts.WebViewClientTest#testOnUnhandledKeyEvent',],
      'com.android.cts.filesystemperf' : [
          'RandomRWTest#testRandomRead',
          'RandomRWTest#testRandomUpdate',],
      '' : []}

def LogGenerateDescription(name):
  print 'Generating test description for package %s' % name

if __name__ == '__main__':
  builder = CtsBuilder(sys.argv)
  result = builder.GenerateTestDescriptions()
  if result != 0:
    sys.exit(result)
  builder.GenerateTestPlans()

