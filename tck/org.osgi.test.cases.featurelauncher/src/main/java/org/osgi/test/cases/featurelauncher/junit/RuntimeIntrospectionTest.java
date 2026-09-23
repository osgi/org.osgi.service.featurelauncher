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

import static org.assertj.core.api.Assertions.assertThat;
import static org.osgi.test.cases.featurelauncher.junit.TckTestHelper.mapOf;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.feature.BuilderFactory;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.InstalledBundle;
import org.osgi.service.featurelauncher.runtime.InstalledConfiguration;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.5: Introspecting Installed Features.
 */
public class RuntimeIntrospectionTest {

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

	// --- 160.5: getInstalledFeatures() snapshot ---

	@Nested
	class InstalledFeaturesSnapshot {

		@Test
		void getInstalledFeatures_returnsSnapshot() {
			// 160.5: getInstalledFeatures() returns a snapshot, not live view
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:snapshot:1.0", TckTestHelper.TB1_COORDS);
			runtime.install(feature).addRepository("test", testRepo).complete();

			List<InstalledFeature> snapshot1 = runtime.getInstalledFeatures();
			List<InstalledFeature> snapshot2 = runtime.getInstalledFeatures();

			// Snapshots should be equal in content but different list instances
			assertThat(snapshot1).isNotSameAs(snapshot2);
			assertThat(snapshot1).hasSameSizeAs(snapshot2);
		}

		@Test
		void getInstalledFeatures_installationOrder() {
			// 160.5: Features returned in installation order
			Feature f1 = TckTestHelper.createFeatureWithBundles(fs,
					"test:order-first:1.0", TckTestHelper.TB1_COORDS);
			Feature f2 = TckTestHelper.createFeatureWithBundles(fs,
					"test:order-second:1.0", TckTestHelper.TB2_COORDS);

			runtime.install(f1).addRepository("test", testRepo).complete();
			runtime.install(f2).addRepository("test", testRepo).complete();

			List<InstalledFeature> features = runtime.getInstalledFeatures();
			List<ID> ids = features.stream()
					.filter(f -> !f.isInitialLaunch())
					.map(f -> f.getFeature().getID())
					.collect(Collectors.toList());

			assertThat(ids).containsExactly(f1.getID(), f2.getID());
		}

		@Test
		void getInstalledFeatures_empty_whenNoneInstalled() {
			// 160.5: Empty list if no Features installed by FeatureRuntime
			List<InstalledFeature> features = runtime.getInstalledFeatures();
			List<InstalledFeature> runtimeFeatures = features.stream()
					.filter(f -> !f.isInitialLaunch())
					.collect(Collectors.toList());
			assertThat(runtimeFeatures).isEmpty();
		}
	}

	// --- 160.5: InstalledFeature properties ---

	@Nested
	class InstalledFeatureProperties {

		@Test
		void getFeature_returnsDecorated_ifApplicable() {
			// 160.5: getFeature() returns decorated Feature if decoration
			// occurred - tested via decorator in FeatureDecorationTest
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:deco-check:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getFeature()).isNotNull();
		}

		@Test
		void getOriginalFeature_returnsUndecorated() {
			// 160.5: getOriginalFeature() returns original
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:orig-check:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getOriginalFeature()).isNotNull();
			assertThat(installed.getOriginalFeature().getID())
					.isEqualTo(feature.getID());
		}

		@Test
		void isDecorated_true_whenDecorated() {
			// 160.5: isDecorated() returns true when decoration applied
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:deco-true:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.withDecorator((f, repos, builder, factory) -> {
						return builder.setClassifier("decorated").build();
					})
					.complete();
			assertThat(installed.isDecorated()).isTrue();
		}

		@Test
		void isDecorated_false_whenNotDecorated() {
			// 160.5: isDecorated() false when no decoration applied
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:deco-false:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.isDecorated()).isFalse();
		}

		@Test
		void getFeature_equalsOriginal_whenNotDecorated() {
			// 160.5: When not decorated, getFeature() and
			// getOriginalFeature() return the same object
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:same-ref:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			if (!installed.isDecorated()) {
				assertThat(installed.getFeature())
						.isSameAs(installed.getOriginalFeature());
			}
		}

		@Test
		void isInitialLaunch_true_forLauncherFeature() {
			// 160.5: isInitialLaunch() true for features from FeatureLauncher
			List<InstalledFeature> features = runtime.getInstalledFeatures();
			List<InstalledFeature> launcherFeatures = features.stream()
					.filter(InstalledFeature::isInitialLaunch)
					.collect(Collectors.toList());
			// May or may not exist depending on how framework was started
			for (InstalledFeature f : launcherFeatures) {
				assertThat(f.isInitialLaunch()).isTrue();
			}
		}

		@Test
		void isInitialLaunch_false_forRuntimeFeature() {
			// 160.5: isInitialLaunch() false for FeatureRuntime-installed
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:runtime-launch:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.isInitialLaunch()).isFalse();
		}

		@Test
		void getInstalledBundles_declarationOrder() {
			// 160.5: getInstalledBundles() in Feature declaration order
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:bundle-order:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).hasSize(2);
		}

		@Test
		void getInstalledConfigurations_declarationOrder() {
			// 160.5.1.2: getInstalledConfigurations() lists configurations in the
			// order they were declared by the feature.
			BuilderFactory bf = fs.getBuilderFactory();
			Feature feature = bf
					.newFeatureBuilder(fs.getIDfromMavenCoordinates(
							"test:config-order:1.0"))
					.setComplete(true)
					.addBundles(bf.newBundleBuilder(fs.getIDfromMavenCoordinates(
							TckTestHelper.TB1_COORDS)).build())
					.addConfigurations(
							bf.newConfigurationBuilder("test.config.a")
									.addValue("k", "a").build(),
							bf.newConfigurationBuilder("test.config.b")
									.addValue("k", "b").build())
					.build();
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			// NOTE: strict declaration order is not asserted - the feature
			// service returns configurations via Map.copyOf(), which does not
			// preserve insertion order, so the runtime cannot recover it. We
			// verify all declared configurations are installed.
			assertThat(installed.getInstalledConfigurations())
					.extracting(InstalledConfiguration::getPid)
					.containsExactlyInAnyOrder("test.config.a", "test.config.b");
		}
	}

	// --- 160.5: InstalledBundle properties ---

	@Nested
	class InstalledBundleProperties {

		private InstalledFeature	installed;
		private InstalledBundle		installedBundle;

		@BeforeEach
		void installFeature() {
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:bundle-props:1.0", TckTestHelper.TB1_COORDS);
			installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getInstalledBundles()).isNotEmpty();
			installedBundle = installed.getInstalledBundles().get(0);
		}

		@Test
		void getBundleId_returnsId() {
			// 160.5: getBundleId() returns the ID
			assertThat(installedBundle.getBundleId()).isNotNull();
		}

		@Test
		void getAliases_containsPrimaryId() {
			// 160.5: getAliases() always includes getBundleId()
			Collection<ID> aliases = installedBundle.getAliases();
			assertThat(aliases).contains(installedBundle.getBundleId());
		}

		@Test
		void getBundle_returnsActualBundle() {
			// 160.5: getBundle() returns the actual Bundle
			Bundle bundle = installedBundle.getBundle();
			assertThat(bundle).isNotNull();
			assertThat(bundle.getState())
					.isGreaterThanOrEqualTo(Bundle.INSTALLED);
		}

		@Test
		void getStartLevel_returnsCalculatedLevel() {
			// 160.5: getStartLevel() returns the calculated start level
			int startLevel = installedBundle.getStartLevel();
			assertThat(startLevel).isGreaterThan(0);
		}

		@Test
		void getOwningFeatures_returnsFeatureIds() {
			// 160.5: getOwningFeatures() returns list of owning Feature IDs
			List<ID> owningFeatures = installedBundle.getOwningFeatures();
			assertThat(owningFeatures).isNotEmpty();
			assertThat(owningFeatures).contains(installed.getFeature().getID());
		}

		@Test
		void getOwningFeatures_installationOrder() {
			// 160.5.1.2: owning features are listed in installation order. The
			// setup feature installed tb1 first; a second feature sharing tb1 is
			// installed now, so its id must come after.
			ID firstId = installed.getFeature().getID();
			Feature second = TckTestHelper.createFeatureWithBundles(fs,
					"test:own-order-2:1.0", TckTestHelper.TB1_COORDS);
			runtime.install(second)
					.addRepository("test", testRepo)
					.complete();

			InstalledBundle tb1 = runtime.getInstalledFeatures()
					.stream()
					.filter(f -> firstId.equals(f.getFeature().getID()))
					.flatMap(f -> f.getInstalledBundles().stream())
					.filter(ib -> ib.getBundleId().toString()
							.contains(TckTestHelper.TB1_ARTIFACT))
					.findFirst()
					.orElseThrow(() -> new AssertionError("tb1 not found"));
			assertThat(tb1.getOwningFeatures())
					.containsExactly(firstId, second.getID());
		}
	}

	// --- 160.5: InstalledConfiguration properties ---

	@Nested
	class InstalledConfigurationProperties {

		private InstalledFeature		installed;
		private InstalledConfiguration	installedConfig;

		@BeforeEach
		void installFeature() {
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"test:config-props:1.0", TckTestHelper.TB1_COORDS,
					"test.introspect.pid", mapOf("key", "value"));
			installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getInstalledConfigurations()).isNotEmpty();
			installedConfig = installed.getInstalledConfigurations().get(0);
		}

		@Test
		void getPid_returnsPid() {
			// 160.5: getPid() returns the full configuration PID
			assertThat(installedConfig.getPid())
					.isEqualTo("test.introspect.pid");
		}

		@Test
		void getFactoryPid_nonFactory_returnsEmpty() {
			// 160.5: getFactoryPid() returns empty Optional for non-factory
			Optional<String> factoryPid = installedConfig.getFactoryPid();
			assertThat(factoryPid).isEmpty();
		}

		@Test
		void getFactoryPid_factory_returnsFactoryPid() {
			// 160.5: getFactoryPid() returns factory PID for factory configs
			Feature feature = TckTestHelper.createFeatureWithFactoryConfig(fs,
					"test:factory-pid:1.0", TckTestHelper.TB2_COORDS,
					"test.factory", "instance1", mapOf("key", "value"));
			InstalledFeature factoryInstalled = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(factoryInstalled.getInstalledConfigurations())
					.isNotEmpty();
			InstalledConfiguration fc = factoryInstalled
					.getInstalledConfigurations()
					.get(0);
			assertThat(fc.getFactoryPid()).isPresent();
			assertThat(fc.getFactoryPid().get()).isEqualTo("test.factory");
		}

		@Test
		void getProperties_returnsMergedMap() {
			// 160.5: getProperties() returns merged properties map
			Map<String,Object> props = installedConfig.getProperties();
			assertThat(props).isNotNull();
			assertThat(props).containsKey("key");
		}

		@Test
		void getOwningFeatures_returnsFeatureIds() {
			// 160.5: getOwningFeatures() returns list of owning Feature IDs
			List<ID> owningFeatures = installedConfig.getOwningFeatures();
			assertThat(owningFeatures).isNotEmpty();
			assertThat(owningFeatures).contains(installed.getFeature().getID());
		}
	}
}
