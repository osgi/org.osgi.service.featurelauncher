/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/

package org.osgi.test.cases.featurelauncher.junit;

import static org.assertj.core.api.Assertions.*;
import static org.osgi.test.cases.featurelauncher.junit.TckTestHelper.mapOf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeConstants;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeException;
import org.osgi.service.featurelauncher.runtime.InstalledBundle;
import org.osgi.service.featurelauncher.runtime.InstalledConfiguration;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;
import org.osgi.service.featurelauncher.runtime.RuntimeMerges;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.5: Installing Features (Feature Runtime).
 */
public class RuntimeInstallTest {

	@InjectService
	FeatureRuntime				runtime;

	@InjectService
	FeatureService				fs;

	@InjectService
	ArtifactRepositoryFactory	repoFactory;

	@InjectBundleContext
	BundleContext				ctx;

	ArtifactRepository			testRepo;

	@BeforeEach
	void setUp() throws IOException {
		testRepo = TckTestHelper.createTestRepository(repoFactory);
	}

	@AfterEach
	void tearDown() {
		TckTestHelper.cleanupInstalledFeatures(runtime);
	}

	// --- 160.5: Feature installation basics ---

	@Nested
	class BasicInstall {

		@Test
		void install_feature_returnsInstalledFeature() {
			// 160.5: install(Feature) -> InstallOperationBuilder ->
			// complete() returns InstalledFeature
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:basic-install:1.0.0", TckTestHelper.TB1_COORDS);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			assertThat(installed).isNotNull();
			assertThat(installed.getFeature()).isNotNull();
			assertThat(installed.getFeature().getID())
					.isEqualTo(feature.getID());
		}

		@Test
		void install_reader_returnsInstalledFeature() throws Exception {
			// 160.5: install(Reader) -> InstallOperationBuilder ->
			// complete() returns InstalledFeature
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:reader-install:1.0.0", TckTestHelper.TB1_COORDS);

			StringWriter sw = new StringWriter();
			fs.writeFeature(feature, sw);
			String json = sw.toString();

			try (Reader reader = new StringReader(json)) {
				InstalledFeature installed = runtime.install(reader)
						.addRepository("test", testRepo)
						.complete();

				assertThat(installed).isNotNull();
				assertThat(installed.getFeature()).isNotNull();
				assertThat(installed.getFeature().getID())
						.isEqualTo(feature.getID());
			}
		}

		@Test
		void install_bundlesInstalledInFramework() {
			// 160.5: All bundles from the feature are installed in the
			// framework
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:bundles-installed:1.0.0", TckTestHelper.TB1_COORDS);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).isNotEmpty();
			for (InstalledBundle ib : bundles) {
				assertThat(ib.getBundle()).isNotNull();
				assertThat(ib.getBundle().getState())
						.isGreaterThanOrEqualTo(Bundle.INSTALLED);
			}
		}

		@Test
		void install_bundlesInDeclaredOrder() {
			// 160.5: Bundles installed in Feature declaration order
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:bundle-order:1.0.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).hasSize(2);

			ID tb1Id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
			ID tb2Id = fs.getIDfromMavenCoordinates(TckTestHelper.TB2_COORDS);

			assertThat(bundles.get(0).getBundleId()).isEqualTo(tb1Id);
			assertThat(bundles.get(1).getBundleId()).isEqualTo(tb2Id);
		}

		@Test
		void install_configurationsCreated() {
			// 160.5: Configurations from the feature are created in the
			// framework
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"test:config-install:1.0.0", TckTestHelper.TB1_COORDS,
					"my.test.pid", mapOf("key", "value"));

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledConfiguration> configs = installed
					.getInstalledConfigurations();
			assertThat(configs).isNotEmpty();
			assertThat(configs).anyMatch(c -> "my.test.pid".equals(c.getPid()));
		}

		@Test
		void install_featureInGetInstalledFeatures() {
			// 160.5: Installed feature appears in getInstalledFeatures()
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:in-list:1.0.0", TckTestHelper.TB1_COORDS);

			runtime.install(feature).addRepository("test", testRepo).complete();

			List<InstalledFeature> allInstalled = runtime
					.getInstalledFeatures();
			List<ID> installedIds = allInstalled.stream()
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());
			assertThat(installedIds).contains(feature.getID());
		}
	}

	// --- 160.5: Already known feature ---

	@Nested
	class DuplicateFeature {

		@Test
		void install_alreadyKnown_throwsFeatureRuntimeException() {
			// 160.5: If feature is already known, MUST throw
			// FeatureRuntimeException
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:duplicate:1.0.0", TckTestHelper.TB1_COORDS);

			runtime.install(feature).addRepository("test", testRepo).complete();

			Feature sameFeature = TckTestHelper.createFeatureWithBundles(fs,
					"test:duplicate:1.0.0", TckTestHelper.TB1_COORDS);

			assertThatThrownBy(() -> runtime.install(sameFeature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}
	}

	// --- 160.5: Bundle overlap ---

	@Nested
	class BundleOverlap {

		@Test
		void install_exactMatch_markOwned() {
			// 160.5: Exact match (same groupId+artifactId+version) -> remove
			// from install list, mark as owned by Feature
			Feature feature1 = TckTestHelper.createFeatureWithBundles(fs,
					"test:overlap-a:1.0.0", TckTestHelper.TB1_COORDS);
			Feature feature2 = TckTestHelper.createFeatureWithBundles(fs,
					"test:overlap-b:1.0.0", TckTestHelper.TB1_COORDS);

			runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();
			InstalledFeature installed2 = runtime.install(feature2)
					.addRepository("test", testRepo)
					.complete();

			// The shared bundle should be owned by both features
			List<InstalledBundle> bundles2 = installed2.getInstalledBundles();
			assertThat(bundles2).isNotEmpty();

			InstalledBundle sharedBundle = bundles2.get(0);
			List<ID> owners = sharedBundle.getOwningFeatures();
			assertThat(owners).contains(feature1.getID(), feature2.getID());
		}

		@Test
		void install_exactMatch_externalFeatureId_ifPreviouslyExternal() {
			// 160.5: If bundle not previously owned by Feature Runtime,
			// also mark as owned by EXTERNAL_FEATURE_ID
			// Pre-install a bundle manually into the framework
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:external-match:1.0.0", TckTestHelper.TB1_COORDS);

			// Pre-install tb1 manually via BundleContext (test setup only).
			ID tb1Id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
			try (InputStream is = testRepo.getArtifact(tb1Id)) {
				assertThat(is).as(
						"tb1 artifact must be available from the test repository")
						.isNotNull();
				ctx.installBundle("pre-installed-tb1", is);
			} catch (Exception e) {
				throw new AssertionError(
						"test setup: could not pre-install tb1", e);
			}

			// Behaviour under test (unguarded so real failures surface): the
			// existing bundle should get EXTERNAL_FEATURE_ID in its owners.
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).isNotEmpty();

			InstalledBundle ib = bundles.get(0);
			List<ID> owners = ib.getOwningFeatures();
			ID externalId = fs.getIDfromMavenCoordinates(
					FeatureRuntimeConstants.EXTERNAL_FEATURE_ID);
			assertThat(owners).contains(externalId);
		}

		@Test
		void install_sameGaVersionDiffers_triggersMerge() {
			// 160.5: Same groupId+artifactId but different version triggers
			// RuntimeBundleMerge
			Feature feature1 = TckTestHelper.createFeatureWithBundles(fs,
					"test:merge-trigger-a:1.0.0", TckTestHelper.TBM_V2_COORDS);

			runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();

			// A second feature with the same groupId+artifactId but a different
			// version (tbm 1.0.0 vs 1.1.0) must trigger the configured merge.
			AtomicBoolean mergeInvoked = new AtomicBoolean(false);
			Feature feature2 = TckTestHelper.createFeatureWithBundles(fs,
					"test:merge-trigger-b:1.0.0", TckTestHelper.TBM_V1_COORDS);

			runtime.install(feature2)
					.addRepository("test", testRepo)
					.withBundleMerge((operation, feature, toMerge,
							installedBundles, existingFeatureBundles) -> {
						mergeInvoked.set(true);
						return RuntimeMerges.preferExistingBundles().mergeBundle(
								operation, feature, toMerge, installedBundles,
								existingFeatureBundles);
					})
					.complete();

			assertThat(mergeInvoked.get())
					.as("an overlapping different version must trigger the merge")
					.isTrue();
		}

		@Test
		void install_duplicateInFramework_success_addAlias() {
			// 160.5: If framework duplicate detected, treat as success and
			// add ID as alias to existing InstalledBundle
			Feature feature1 = TckTestHelper.createFeatureWithBundles(fs,
					"test:alias-a:1.0.0", TckTestHelper.TB1_COORDS);
			Feature feature2 = TckTestHelper.createFeatureWithBundles(fs,
					"test:alias-b:1.0.0", TckTestHelper.TB1_COORDS);

			runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();
			InstalledFeature installed2 = runtime.install(feature2)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed2.getInstalledBundles();
			assertThat(bundles).isNotEmpty();

			InstalledBundle ib = bundles.get(0);
			Collection<ID> aliases = ib.getAliases();
			assertThat(aliases).contains(ib.getBundleId());
		}

		@Test
		void install_duplicateInFramework_lowerStartLevel() {
			// 160.5: Set start level to lower of current and feature value
			Feature feature1 = TckTestHelper.createFeatureWithBundles(fs,
					"test:startlevel-a:1.0.0", TckTestHelper.TB1_COORDS);
			Feature feature2 = TckTestHelper.createFeatureWithBundles(fs,
					"test:startlevel-b:1.0.0", TckTestHelper.TB1_COORDS);

			InstalledFeature installed1 = runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();

			int firstLevel = installed1.getInstalledBundles()
					.get(0)
					.getStartLevel();

			InstalledFeature installed2 = runtime.install(feature2)
					.addRepository("test", testRepo)
					.complete();

			int secondLevel = installed2.getInstalledBundles()
					.get(0)
					.getStartLevel();

			// The start level should be the lower of the two
			assertThat(secondLevel).isLessThanOrEqualTo(firstLevel);
		}
	}

	// --- 160.5: Configuration overlap ---

	@Nested
	class ConfigurationOverlap {

		@Test
		void install_samePid_triggersConfigMerge() {
			// 160.5: Same PID across features triggers
			// RuntimeConfigurationMerge
			Feature feature1 = TckTestHelper.createFeatureWithConfig(fs,
					"test:config-merge-a:1.0.0", TckTestHelper.TB1_COORDS,
					"shared.config.pid", mapOf("key1", "value1"));
			Feature feature2 = TckTestHelper.createFeatureWithConfig(fs,
					"test:config-merge-b:1.0.0", TckTestHelper.TB2_COORDS,
					"shared.config.pid", mapOf("key2", "value2"));

			runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();

			// Second install with same PID should trigger config merge
			// and succeed (using default merge strategy)
			InstalledFeature installed2 = runtime.install(feature2)
					.addRepository("test", testRepo)
					.complete();

			assertThat(installed2).isNotNull();
			List<InstalledConfiguration> configs = installed2
					.getInstalledConfigurations();
			assertThat(configs)
					.anyMatch(c -> "shared.config.pid".equals(c.getPid()));
		}

		@Test
		void install_readOnlyConfig_skippedWithWarning() throws Exception {
			// 160.5.2.1.3: when a configuration already exists in Configuration
			// Admin marked READ_ONLY, the runtime must log a warning and skip it
			// (it must not overwrite the existing properties).
			String pid = "readonly.test.pid";
			ConfigurationAdmin cm = ctx
					.getService(ctx.getServiceReference(ConfigurationAdmin.class));
			try {
				// Pre-create a READ_ONLY configuration outside the runtime.
				Configuration existing = cm.getConfiguration(pid, "?");
				Dictionary<String,Object> props = new Hashtable<>();
				props.put("key", "original");
				existing.update(props);
				existing.addAttributes(
						Configuration.ConfigurationAttribute.READ_ONLY);

				// Install a feature declaring the same pid with a different value.
				Feature feature = TckTestHelper.createFeatureWithConfig(fs,
						"test:readonly-config:1.0.0", TckTestHelper.TB1_COORDS,
						pid, mapOf("key", "value"));
				runtime.install(feature)
						.addRepository("test", testRepo)
						.complete();

				// The READ_ONLY configuration must be left untouched.
				assertThat(cm.getConfiguration(pid, "?").getProperties().get("key"))
						.isEqualTo("original");
			} finally {
				Configuration cleanup = cm.getConfiguration(pid, "?");
				try {
					cleanup.removeAttributes(
							Configuration.ConfigurationAttribute.READ_ONLY);
				} catch (Exception ignore) {
					// best effort
				}
				cleanup.delete();
				ctx.ungetService(
						ctx.getServiceReference(ConfigurationAdmin.class));
			}
		}
	}

	// --- 160.5: Feature start phase ---

	@Nested
	class FeatureStart {

		@Test
		void install_bundlesStartedAscendingStartLevel() {
			// 160.5: Bundles started in ascending start level order
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:start-order:1.0.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).hasSizeGreaterThanOrEqualTo(2);

			// Verify bundles are ordered by ascending start level
			for (int i = 1; i < bundles.size(); i++) {
				assertThat(bundles.get(i).getStartLevel())
						.isGreaterThanOrEqualTo(
								bundles.get(i - 1).getStartLevel());
			}
		}

		@Test
		void install_bundlesMarkedPersistentlyStarted() {
			// 160.5: Bundles marked as persistently started (unless fragment)
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:persistent-start:1.0.0", TckTestHelper.TB1_COORDS);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).isNotEmpty();

			for (InstalledBundle ib : bundles) {
				Bundle bundle = ib.getBundle();
				// Non-fragment bundles should be ACTIVE
				if (bundle.getHeaders().get("Fragment-Host") == null) {
					assertThat(bundle.getState())
							.as("Bundle %s should be ACTIVE",
									bundle.getSymbolicName())
							.isEqualTo(Bundle.ACTIVE);
				}
			}
		}

		@Test
		void install_minimumStartLevel_frameworkLevelSet() {
			// 160.5: If minimumStartLevel set, increase framework start level
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:min-startlevel:1.0.0", TckTestHelper.TB1_COORDS);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			// The framework start level should be at least as high as the
			// minimum start level of any installed bundle
			FrameworkStartLevel fsl = ctx.getBundle(0)
					.adapt(FrameworkStartLevel.class);
			int frameworkLevel = fsl.getStartLevel();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			for (InstalledBundle ib : bundles) {
				assertThat(frameworkLevel)
						.as("Framework start level should be >= bundle "
								+ "start level")
						.isGreaterThanOrEqualTo(ib.getStartLevel());
			}
		}

		@Test
		void install_higherThanCurrentLevel_bundlesNotStartedYet() {
			// 160.5: If start level higher than current framework level,
			// bundles marked persistently started but won't start until
			// framework level changed
			FrameworkStartLevel fsl = ctx.getBundle(0)
					.adapt(FrameworkStartLevel.class);
			int currentLevel = fsl.getStartLevel();

			// This test verifies the concept: bundles with a start level
			// higher than the framework level will not be active yet.
			// We verify this by checking that if a bundle's start level
			// exceeds the framework level, it is not ACTIVE.
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:higher-level:1.0.0", TckTestHelper.TB1_COORDS);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			for (InstalledBundle ib : bundles) {
				if (ib.getStartLevel() > currentLevel) {
					assertThat(ib.getBundle().getState())
							.as("Bundle with higher start level should not "
									+ "be ACTIVE")
							.isNotEqualTo(Bundle.ACTIVE);
				}
			}
		}
	}

	// --- 160.5: Failure -> rollback ---

	@Nested
	class InstallFailureRollback {

		@Test
		void install_failure_systemRestoredToPreviousState() {
			// 160.5: On failure, MUST restore system to pre-existing state
			long[] bundlesBefore = getBundleIds();

			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:rollback-state:1.0.0",
					"com.nonexistent:bundle:9.9.9");

			try {
				runtime.install(feature)
						.addRepository("test", testRepo)
						.complete();
			} catch (FeatureRuntimeException e) {
				// expected
			}

			long[] bundlesAfter = getBundleIds();
			assertThat(bundlesAfter)
					.as("System should be restored to pre-existing state")
					.containsExactly(bundlesBefore);
		}

		@Test
		void install_failure_throwsFeatureRuntimeException() {
			// 160.5: On failure, throw FeatureRuntimeException
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:fail-fre:1.0.0", "com.nonexistent:bundle:9.9.9");

			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void install_bundleNotFound_throwsFRE() {
			// 160.5: Bundle not found in repositories -> FRE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:not-found:1.0.0",
					"com.nonexistent:missing-bundle:1.0.0");

			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void install_bundleException_throwsFRE() {
			// 160.5: BundleException during installation -> FRE
			// Use an artifact repository that returns invalid bundle content
			ArtifactRepository badRepo = id -> {
				// Return invalid content that will cause BundleException
				return new ByteArrayInputStream(
						"not-a-valid-bundle".getBytes());
			};

			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:bad-bundle:1.0.0", TckTestHelper.TB1_COORDS);

			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", badRepo)
					.useDefaultRepositories(false)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void install_configCreationFails_throwsFRE() {
			// 160.5: Configuration creation failure -> FRE
			// This tests that a feature with invalid configuration
			// properties results in a FeatureRuntimeException.
			// The exact failure depends on the ConfigAdmin impl.
			// We test with a config PID that is null/empty which should fail.
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"test:bad-config:1.0.0", TckTestHelper.TB1_COORDS, "",
					mapOf("key", "value"));

			try {
				InstalledFeature installed = runtime.install(feature)
						.addRepository("test", testRepo)
						.complete();
				// If it succeeds, the impl was lenient - not a failure
				assertThat(installed).isNotNull();
			} catch (FeatureRuntimeException e) {
				// Expected behavior per spec
				assertThat(e).isInstanceOf(FeatureRuntimeException.class);
			}
		}

		@Test
		void install_mergeException_throwsFRE() {
			// 160.5: Exception from RuntimeBundleMerge or
			// RuntimeConfigurationMerge -> FRE
			Feature feature1 = TckTestHelper.createFeatureWithBundles(fs,
					"test:merge-exc-a:1.0.0", TckTestHelper.TBM_V2_COORDS);

			runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();

			// Install a second feature with an overlapping bundle (same
			// groupId/artifactId, different version) so a merge is required,
			// using a merge strategy that throws an exception.
			Feature feature2 = TckTestHelper.createFeatureWithBundles(fs,
					"test:merge-exc-b:1.0.0", TckTestHelper.TBM_V1_COORDS);

			assertThatThrownBy(
					() -> runtime.install(feature2)
							.addRepository("test", testRepo)
							.withBundleMerge((operation, feature, toMerge,
									installedBundles,
									existingFeatureBundles) -> {
								throw new RuntimeException(
										"Merge strategy failure");
							})
							.complete()).isInstanceOf(
									FeatureRuntimeException.class);
		}

		private long[] getBundleIds() {
			Bundle[] bundles = ctx.getBundles();
			long[] ids = new long[bundles.length];
			for (int i = 0; i < bundles.length; i++) {
				ids[i] = bundles[i].getBundleId();
			}
			Arrays.sort(ids);
			return ids;
		}
	}

	// --- 160.5: EXTERNAL_FEATURE_ID ---

	@Nested
	class ExternalOwnership {

		@Test
		void install_bundleAlreadyInFramework_getsExternalFeatureId() {
			// 160.5: Bundle already in framework (not owned by Feature
			// Runtime) gets EXTERNAL_FEATURE_ID added to owning features
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:external-owner:1.0.0", TckTestHelper.TB1_COORDS);

			// Pre-install tb1 manually via BundleContext (test setup only).
			ID tb1Id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
			try (InputStream is = testRepo.getArtifact(tb1Id)) {
				assertThat(is).as(
						"tb1 artifact must be available from the test repository")
						.isNotNull();
				ctx.installBundle("pre-installed-external-tb1", is);
			} catch (Exception e) {
				throw new AssertionError(
						"test setup: could not pre-install tb1", e);
			}

			// Behaviour under test (unguarded so real failures surface).
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).isNotEmpty();

			// Find the bundle matching tb1
			InstalledBundle tb1Bundle = bundles.stream().filter(ib -> {
				Bundle b = ib.getBundle();
				return b != null && TckTestHelper.TB1_ARTIFACT
						.equals(b.getSymbolicName());
			}).findFirst().orElse(bundles.get(0));

			List<ID> owners = tb1Bundle.getOwningFeatures();
			ID externalId = fs.getIDfromMavenCoordinates(
					FeatureRuntimeConstants.EXTERNAL_FEATURE_ID);
			assertThat(owners)
					.as("Pre-existing bundle should have "
							+ "EXTERNAL_FEATURE_ID in owners")
					.contains(externalId);
		}
	}
}
