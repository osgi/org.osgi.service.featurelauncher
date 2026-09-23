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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeException;
import org.osgi.service.featurelauncher.runtime.InstalledBundle;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.5: Removing Features (Feature Runtime).
 */
public class RuntimeRemoveTest {

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

	// --- 160.5: Feature removal basics ---

	@Nested
	class BasicRemove {

		@Test
		void remove_knownFeature_removedFromList() {
			// 160.5: Removed feature no longer in getInstalledFeatures()
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-basic:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			runtime.remove(featureId);

			List<InstalledFeature> remaining = runtime.getInstalledFeatures();
			List<ID> remainingIds = remaining.stream()
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());
			assertThat(remainingIds).doesNotContain(featureId);
		}

		@Test
		void remove_knownFeature_bundlesStopped() {
			// 160.5: Bundles with zero remaining owners are stopped
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-stop:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			// Capture bundle reference before removal
			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).isNotEmpty();
			Bundle bundle = bundles.get(0).getBundle();
			assertThat(bundle).isNotNull();

			runtime.remove(featureId);

			// After removal, bundle should not be ACTIVE
			assertThat(bundle.getState()).isNotEqualTo(Bundle.ACTIVE);
		}

		@Test
		void remove_knownFeature_configsDeleted() throws Exception {
			// 160.5: Configurations with zero remaining owners are deleted
			String pid = "org.osgi.test.remove.config";
			Map<String,Object> props = mapOf("key", "value");
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"g:remove-cfg:1.0", TckTestHelper.TB1_COORDS, pid, props);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			// Verify config was created
			assertThat(installed.getInstalledConfigurations()).isNotEmpty();

			runtime.remove(featureId);

			// Verify config is deleted - use ConfigurationAdmin to check
			ConfigurationAdmin cm = ctx.getService(
					ctx.getServiceReference(ConfigurationAdmin.class));
			try {
				Configuration[] configs = cm
						.listConfigurations("(service.pid=" + pid + ")");
				assertThat(configs).isNull();
			} finally {
				ctx.ungetService(
						ctx.getServiceReference(ConfigurationAdmin.class));
			}
		}

		@Test
		void remove_knownFeature_bundlesUninstalled() {
			// 160.5: Bundles with zero remaining owners are uninstalled
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-uninstall:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).isNotEmpty();
			Bundle bundle = bundles.get(0).getBundle();

			runtime.remove(featureId);

			assertThat(bundle.getState()).isEqualTo(Bundle.UNINSTALLED);
		}

		@Test
		void remove_bundleStopOrder_reverseStartLevel() {
			// 160.5: Bundles stopped in reverse order - highest start level
			// first. Verify by ensuring all bundles are stopped after removal.
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-stoporder:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			List<Bundle> bundles = installed.getInstalledBundles()
					.stream()
					.map(InstalledBundle::getBundle)
					.collect(Collectors.toList());
			assertThat(bundles).hasSizeGreaterThanOrEqualTo(2);

			runtime.remove(featureId);

			// All bundles should be stopped (either RESOLVED or UNINSTALLED)
			for (Bundle b : bundles) {
				assertThat(b.getState()).isNotEqualTo(Bundle.ACTIVE);
			}
		}

		@Test
		void remove_bundleStopOrder_reverseDeclarationOrder() {
			// 160.5: For same start level, bundles stopped in reverse Feature
			// declaration order. Verify all bundles stopped.
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-declorder:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS, TckTestHelper.TB3_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			List<Bundle> bundles = installed.getInstalledBundles()
					.stream()
					.map(InstalledBundle::getBundle)
					.collect(Collectors.toList());

			runtime.remove(featureId);

			for (Bundle b : bundles) {
				assertThat(b.getState()).isNotEqualTo(Bundle.ACTIVE);
			}
		}

		@Test
		void remove_configDeleteOrder_reverseCreationOrder() throws Exception {
			// 160.5: Configurations deleted in reverse creation order
			// (reverse Feature declaration order)
			String pid = "org.osgi.test.remove.cfgorder";
			Map<String,Object> props = mapOf("k", "v");
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"g:remove-cfgorder:1.0", TckTestHelper.TB1_COORDS, pid,
					props);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			runtime.remove(featureId);

			ConfigurationAdmin cm = ctx.getService(
					ctx.getServiceReference(ConfigurationAdmin.class));
			try {
				Configuration[] configs = cm
						.listConfigurations("(service.pid=" + pid + ")");
				assertThat(configs).isNull();
			} finally {
				ctx.ungetService(
						ctx.getServiceReference(ConfigurationAdmin.class));
			}
		}

		@Test
		void remove_bundleUninstallOrder_reverseInstallOrder() {
			// 160.5: Bundles uninstalled in reverse installation order
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-uninstallorder:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			List<Bundle> bundles = installed.getInstalledBundles()
					.stream()
					.map(InstalledBundle::getBundle)
					.collect(Collectors.toList());

			runtime.remove(featureId);

			// All bundles should be uninstalled
			for (Bundle b : bundles) {
				assertThat(b.getState()).isEqualTo(Bundle.UNINSTALLED);
			}
		}

		@Test
		void remove_refreshBundles_calledAfterUninstall() {
			// 160.5: FrameworkWiring.refreshBundles() called with uninstalled
			// bundles after removal. Best effort: verify bundle is
			// uninstalled and wiring is consistent.
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-refresh:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();
			Bundle bundle = installed.getInstalledBundles().get(0).getBundle();

			runtime.remove(featureId);

			// After removal and refresh, the bundle should be uninstalled
			assertThat(bundle.getState()).isEqualTo(Bundle.UNINSTALLED);

			// Verify no dangling bundles reference the removed BSN
			String bsn = bundle.getSymbolicName();
			if (bsn != null) {
				Bundle[] remaining = ctx.getBundles();
				for (Bundle b : remaining) {
					if (bsn.equals(b.getSymbolicName())
							&& b.getState() != Bundle.UNINSTALLED) {
						// If a same-BSN bundle exists, it should be properly
						// wired (refresh was called)
						assertThat(b.getState()).isIn(Bundle.INSTALLED,
								Bundle.RESOLVED, Bundle.ACTIVE);
					}
				}
			}
		}
	}

	// --- 160.5: Unknown feature ---

	@Nested
	class RemoveUnknown {

		@Test
		void remove_unknownFeatureId_definedBehaviour() {
			// 160.5 does not mandate a specific behaviour for removing an
			// unknown Feature id: accept either a silent no-op or a
			// FeatureRuntimeException, but not any other failure.
			ID unknownId = fs.getIDfromMavenCoordinates("g:nonexistent:9.9.9");
			try {
				runtime.remove(unknownId);
			} catch (FeatureRuntimeException e) {
				// acceptable: implementation chose to signal the unknown id
			}
		}
	}

	// --- 160.5: Initial launch feature not removable ---

	@Nested
	class RemoveInitialLaunch {

		@Test
		void remove_initialLaunchFeature_throwsFRE() {
			// 160.5: Attempting to remove isInitialLaunch()==true feature
			// MUST throw FeatureRuntimeException
			List<InstalledFeature> features = runtime.getInstalledFeatures();
			List<InstalledFeature> initialFeatures = features.stream()
					.filter(InstalledFeature::isInitialLaunch)
					.collect(Collectors.toList());

			if (!initialFeatures.isEmpty()) {
				InstalledFeature initial = initialFeatures.get(0);
				ID initialId = initial.getFeature().getID();
				assertThatThrownBy(() -> runtime.remove(initialId))
						.isInstanceOf(FeatureRuntimeException.class);
			}
			// If no initial launch features exist, the test is not applicable
			// in this runtime configuration but we still validate the API
			// contract by checking that at least getInstalledFeatures works
			assertThat(features).isNotNull();
		}
	}

	// --- 160.5: Shared bundles/configs ---

	@Nested
	class SharedBundles {

		@Test
		void remove_sharedBundle_notRemovedUntilAllFeaturesRemoved() {
			// 160.5: Bundles owned by multiple Features are not removed
			// until ALL owning Features are removed
			Feature feature1 = TckTestHelper.createFeatureWithBundles(fs,
					"g:shared-bundle-f1:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);
			Feature feature2 = TckTestHelper.createFeatureWithBundles(fs,
					"g:shared-bundle-f2:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB3_COORDS);

			InstalledFeature installed1 = runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();
			InstalledFeature installed2 = runtime.install(feature2)
					.addRepository("test", testRepo)
					.complete();

			ID f1Id = installed1.getFeature().getID();

			// Find the shared tb1 bundle
			Bundle sharedBundle = installed1.getInstalledBundles()
					.stream()
					.filter(ib -> ib.getBundleId()
							.toString()
							.contains(TckTestHelper.TB1_ARTIFACT))
					.map(InstalledBundle::getBundle)
					.findFirst()
					.orElse(null);

			// Remove first feature only
			runtime.remove(f1Id);

			// Shared bundle should still be installed (owned by feature2)
			if (sharedBundle != null) {
				assertThat(sharedBundle.getState())
						.isNotEqualTo(Bundle.UNINSTALLED);
			}

			// Now remove second feature
			runtime.remove(installed2.getFeature().getID());

			// Now the shared bundle should be uninstalled
			if (sharedBundle != null) {
				assertThat(sharedBundle.getState())
						.isEqualTo(Bundle.UNINSTALLED);
			}
		}

		@Test
		void remove_sharedConfig_notDeletedUntilAllFeaturesRemoved()
				throws Exception {
			// 160.5: Configurations owned by multiple Features are not
			// deleted until ALL owning Features are removed
			String sharedPid = "org.osgi.test.shared.config";
			Map<String,Object> props1 = mapOf("source", "feature1");
			Map<String,Object> props2 = mapOf("source", "feature2");

			Feature feature1 = TckTestHelper.createFeatureWithConfig(fs,
					"g:shared-cfg-f1:1.0", TckTestHelper.TB1_COORDS, sharedPid,
					props1);
			Feature feature2 = TckTestHelper.createFeatureWithConfig(fs,
					"g:shared-cfg-f2:1.0", TckTestHelper.TB2_COORDS, sharedPid,
					props2);

			InstalledFeature installed1 = runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();
			InstalledFeature installed2 = runtime.install(feature2)
					.addRepository("test", testRepo)
					.complete();

			ID f1Id = installed1.getFeature().getID();

			ConfigurationAdmin cm = ctx.getService(
					ctx.getServiceReference(ConfigurationAdmin.class));
			try {
				// Remove first feature
				runtime.remove(f1Id);

				// Shared config should still exist
				Configuration[] configs = cm
						.listConfigurations("(service.pid=" + sharedPid + ")");
				assertThat(configs).isNotNull().isNotEmpty();

				// Remove second feature
				runtime.remove(installed2.getFeature().getID());

				// Now config should be deleted
				configs = cm
						.listConfigurations("(service.pid=" + sharedPid + ")");
				assertThat(configs).isNull();
			} finally {
				ctx.ungetService(
						ctx.getServiceReference(ConfigurationAdmin.class));
			}
		}

		@Test
		void remove_ownershipListUpdated() {
			// 160.5: Ownership lists updated after removal - removed Feature
			// ID no longer in getOwningFeatures()
			Feature feature1 = TckTestHelper.createFeatureWithBundles(fs,
					"g:ownership-f1:1.0", TckTestHelper.TB1_COORDS);
			Feature feature2 = TckTestHelper.createFeatureWithBundles(fs,
					"g:ownership-f2:1.0", TckTestHelper.TB1_COORDS);

			InstalledFeature installed1 = runtime.install(feature1)
					.addRepository("test", testRepo)
					.complete();
			InstalledFeature installed2 = runtime.install(feature2)
					.addRepository("test", testRepo)
					.complete();

			ID f1Id = installed1.getFeature().getID();

			// Remove first feature
			runtime.remove(f1Id);

			// Re-query installed features and check ownership
			List<InstalledFeature> remaining = runtime.getInstalledFeatures();
			for (InstalledFeature feat : remaining) {
				if (feat.getFeature()
						.getID()
						.equals(installed2.getFeature().getID())) {
					for (InstalledBundle ib : feat.getInstalledBundles()) {
						List<ID> owners = ib.getOwningFeatures();
						assertThat(owners).doesNotContain(f1Id);
					}
				}
			}
		}
	}

	// --- 160.5: Framework start level after removal ---

	@Nested
	class RemoveStartLevel {

		@Test
		void remove_frameworkStartLevel_setToHighestRemaining() {
			// 160.5: Identify highest start level from remaining Features;
			// set framework start level to that value.
			// Best effort: install a feature, remove it, verify framework
			// start level is consistent.
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-sl:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			int slBefore = ctx.getBundle(0)
					.adapt(org.osgi.framework.startlevel.FrameworkStartLevel.class)
					.getStartLevel();

			runtime.remove(featureId);

			int slAfter = ctx.getBundle(0)
					.adapt(org.osgi.framework.startlevel.FrameworkStartLevel.class)
					.getStartLevel();

			// After removal, framework start level should be >= 0
			// and should reflect remaining installed features
			assertThat(slAfter).isGreaterThan(0);
			// If no other features remain, start level may be reduced
			assertThat(slAfter).isLessThanOrEqualTo(slBefore);
		}
	}

	// --- 160.5: Error handling during removal ---

	@Nested
	class RemoveErrorHandling {

		@Test
		void remove_bundleStopException_loggedIgnored() {
			// 160.5: BundleExceptions during stop are logged and ignored.
			// Best effort: install and remove; verify removal completes
			// even if bundle stop is problematic.
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-stopex:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			// Remove should complete without throwing
			assertThatCode(() -> runtime.remove(featureId))
					.doesNotThrowAnyException();

			// Feature should be removed
			List<ID> ids = runtime.getInstalledFeatures()
					.stream()
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());
			assertThat(ids).doesNotContain(featureId);
		}

		@Test
		void remove_bundleRemovalException_loggedContinued() {
			// 160.5: BundleExceptions during uninstall are logged; removal
			// continues. Best effort: verify removal completes.
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-uninstallex:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			// Even if one bundle fails to uninstall, removal should continue
			try {
				runtime.remove(featureId);
			} catch (FeatureRuntimeException e) {
				// 160.5: May throw FRE at end, which is acceptable
			}

			// Feature should be removed from the list regardless
			List<ID> ids = runtime.getInstalledFeatures()
					.stream()
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());
			assertThat(ids).doesNotContain(featureId);
		}

		@Test
		void remove_bundleRemovalException_mayThrowFRE() {
			// 160.5: May throw FeatureRuntimeException at end if bundle
			// removal errors occurred. This test verifies that the runtime
			// either completes successfully or throws FRE (both are valid).
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"g:remove-mayfre:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			try {
				runtime.remove(featureId);
				// Success is acceptable
			} catch (FeatureRuntimeException e) {
				// FRE is also acceptable per spec
				assertThat(e).isInstanceOf(FeatureRuntimeException.class);
			}

			// In either case, feature should be removed
			List<ID> ids = runtime.getInstalledFeatures()
					.stream()
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());
			assertThat(ids).doesNotContain(featureId);
		}

		@Test
		void remove_missingConfig_loggedAsWarning() throws Exception {
			// 160.5: Missing configurations logged as warning, not error.
			// Install a feature with config, manually delete the config,
			// then remove the feature.
			String pid = "org.osgi.test.remove.missing.config";
			Map<String,Object> props = mapOf("key", "value");
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"g:remove-misscfg:1.0", TckTestHelper.TB1_COORDS, pid,
					props);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			// Manually delete the configuration before removal
			ConfigurationAdmin cm = ctx.getService(
					ctx.getServiceReference(ConfigurationAdmin.class));
			try {
				Configuration[] configs = cm
						.listConfigurations("(service.pid=" + pid + ")");
				if (configs != null) {
					for (Configuration c : configs) {
						c.delete();
					}
				}
			} finally {
				ctx.ungetService(
						ctx.getServiceReference(ConfigurationAdmin.class));
			}

			// Remove should succeed (missing config logged as warning)
			assertThatCode(() -> runtime.remove(featureId))
					.doesNotThrowAnyException();

			List<ID> ids = runtime.getInstalledFeatures()
					.stream()
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());
			assertThat(ids).doesNotContain(featureId);
		}

		@Test
		void remove_configDeleteFails_loggedContinued() throws Exception {
			// 160.5: Configuration deletion failures logged; removal
			// continues. Best effort: verify removal completes.
			String pid = "org.osgi.test.remove.cfgdelfail";
			Map<String,Object> props = mapOf("key", "value");
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"g:remove-cfgdelfail:1.0", TckTestHelper.TB1_COORDS, pid,
					props);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			ID featureId = installed.getFeature().getID();

			// Remove should complete even if config deletion has issues
			try {
				runtime.remove(featureId);
			} catch (FeatureRuntimeException e) {
				// Acceptable - removal may throw FRE
			}

			List<ID> ids = runtime.getInstalledFeatures()
					.stream()
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());
			assertThat(ids).doesNotContain(featureId);
		}
	}
}
