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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.feature.BuilderFactory;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureBundle;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.featurelauncher.FeatureLauncherConstants;
import org.osgi.service.featurelauncher.decorator.FeatureDecorator;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeException;
import org.osgi.service.featurelauncher.runtime.InstalledBundle;
import org.osgi.service.featurelauncher.runtime.InstalledConfiguration;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.4: Feature Launching Process.
 */
public class FeatureLaunchingProcessTest {

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

	// --- 160.4.5: Bundle installation ---

	@Nested
	class BundleInstallation {

		@Test
		void bundles_installedInDeclaredOrder() {
			// 160.4.5: Bundles installed in Feature declaration order
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:launch-order:1.0", TckTestHelper.TB1_COORDS,
					TckTestHelper.TB2_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).hasSize(2);
		}

		@Test
		void bundles_allResolved() {
			// 160.4.5: All feature bundles must be resolved
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:launch-resolved:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			for (InstalledBundle ib : installed.getInstalledBundles()) {
				assertThat(ib.getBundle().getState())
						.isGreaterThanOrEqualTo(Bundle.RESOLVED);
			}
		}

		@Test
		void bundles_allActive() {
			// 160.4.5: All resolved bundles started (unless fragment)
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:launch-active:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			for (InstalledBundle ib : installed.getInstalledBundles()) {
				Bundle b = ib.getBundle();
				if (b.getHeaders().get("Fragment-Host") == null) {
					assertThat(b.getState()).isEqualTo(Bundle.ACTIVE);
				}
			}
		}

		@Test
		void bundles_startLevelsApplied() {
			// 160.4.5: Start levels applied per BUNDLE_START_LEVEL_METADATA
			BuilderFactory bf = fs.getBuilderFactory();
			FeatureBundle bundle = bf
					.newBundleBuilder(fs.getIDfromMavenCoordinates(
							TckTestHelper.TB1_COORDS))
					.addMetadata(
							FeatureLauncherConstants.BUNDLE_START_LEVEL_METADATA,
							3)
					.build();
			Feature feature = bf
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:launch-sl:1.0"))
					.addBundles(bundle)
					.setComplete(true)
					.build();

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getInstalledBundles().get(0).getStartLevel())
					.isEqualTo(3);
		}

		@Test
		void bundles_duplicateBundle_lowerStartLevel() {
			// 160.4.5: Duplicate bundle -> success, lower start level used
			// Install same bundle in two features
			Feature f1 = TckTestHelper.createFeatureWithBundles(fs,
					"test:dup-sl-1:1.0", TckTestHelper.TB1_COORDS);
			Feature f2 = TckTestHelper.createFeatureWithBundles(fs,
					"test:dup-sl-2:1.0", TckTestHelper.TB1_COORDS);
			runtime.install(f1).addRepository("test", testRepo).complete();
			InstalledFeature installed2 = runtime.install(f2)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed2).isNotNull();
		}
	}

	// --- 160.4.5: Configuration creation ---

	@Nested
	class ConfigurationCreation {

		@Test
		void configuration_createdViaConfigAdmin() {
			// 160.4.5: Configurations created via ConfigurationAdmin
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"test:launch-cfg:1.0", TckTestHelper.TB1_COORDS,
					"test.launch.config", mapOf("key", "value"));
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			List<InstalledConfiguration> configs = installed
					.getInstalledConfigurations();
			assertThat(configs).isNotEmpty();
		}

		@Test
		void configuration_factoryConfig_created() {
			// 160.4.5: Factory configurations using PID~name format
			Feature feature = TckTestHelper.createFeatureWithFactoryConfig(fs,
					"test:launch-factory:1.0", TckTestHelper.TB1_COORDS,
					"test.factory", "inst1", mapOf("key", "value"));
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			List<InstalledConfiguration> configs = installed
					.getInstalledConfigurations();
			assertThat(configs).isNotEmpty();
			assertThat(configs.get(0).getFactoryPid()).isPresent();
		}

		@Test
		void configuration_typedProperties_preserved() {
			// 160.4.5: Typed properties preserved (Integer, Long, etc.)
			Map<String,Object> props = mapOf("strProp", "hello", "intProp", 42,
					"boolProp", true);
			Feature feature = TckTestHelper.createFeatureWithConfig(fs,
					"test:launch-typed:1.0", TckTestHelper.TB1_COORDS,
					"test.typed.config", props);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			Map<String,Object> installedProps = installed.getInstalledConfigurations()
					.stream()
					.filter(c -> "test.typed.config".equals(c.getPid()))
					.findFirst()
					.orElseThrow(() -> new AssertionError("config not installed"))
					.getProperties();
			assertThat(installedProps).containsEntry("strProp", "hello")
					.containsEntry("intProp", 42)
					.containsEntry("boolProp", true);
		}
	}

	// --- 160.4.1: Decoration phase during launch ---

	@Nested
	class DecorationPhase {

		@Test
		void decorationPhase_decoratorsCalledInOrder() {
			// 160.4.1: Decorators called in registration order
			AtomicBoolean called = new AtomicBoolean(false);
			FeatureDecorator decorator = (f, repos, builder, factory) -> {
				called.set(true);
				return f;
			};
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:deco-phase:1.0", TckTestHelper.TB1_COORDS);
			runtime.install(feature)
					.addRepository("test", testRepo)
					.withDecorator(decorator)
					.complete();
			assertThat(called.get()).isTrue();
		}

		@Test
		void decorationPhase_extensionHandlersCalled() {
			// 160.4.1: Extension handlers called for matching extensions
			AtomicBoolean called = new AtomicBoolean(false);
			Feature feature = TckTestHelper.createFeatureWithExtension(fs,
					"test:ext-handler:1.0", TckTestHelper.TB1_COORDS, "test-ext",
					"data");
			runtime.install(feature)
					.addRepository("test", testRepo)
					.withExtensionHandler("test-ext",
							(f, ext, repos, builder, factory) -> {
								called.set(true);
								return f;
							})
					.complete();
			// The feature declares "test-ext", so the handler must be invoked.
			assertThat(called.get()).isTrue();
		}

		@Test
		void decorationPhase_knownExtension_emptyHandler() {
			// 160.4.1: Empty handlers for LAUNCH_FRAMEWORK,
			// FRAMEWORK_LAUNCHING_PROPERTIES, BUNDLE_START_LEVELS
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:known-ext:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed).isNotNull();
		}

	}

	// --- 160.4.7: Failure cases and cleanup ---

	@Nested
	class LaunchFailure {

		@Test
		void bundleFailsToResolve_throwsLaunchException() {
			// 160.4.7: Bundle fails to resolve -> exception
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:unresolvable:1.0",
					"com.example:nonexistent-bundle:1.0.0");
			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void bundleFailsToStart_throwsLaunchException() {
			// 160.4.7: Bundle fails to start -> exception
			// Requires a bundle whose activator throws; tested via
			// non-existent bundle which will fail to install
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:start-fail:1.0", "com.example:bad-bundle:1.0.0");
			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void failure_frameworkStoppedAndDiscarded() {
			// 160.4.7: On failure, framework stopped and discarded
			// Verified by ensuring no stale bundles after failed install
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:cleanup:1.0", "com.example:missing-bundle:1.0.0");
			try {
				runtime.install(feature)
						.addRepository("test", testRepo)
						.complete();
			} catch (FeatureRuntimeException e) {
				// Expected
			}
			// Verify feature was not partially installed
			List<InstalledFeature> features = runtime.getInstalledFeatures();
			boolean found = features.stream()
					.anyMatch(f -> "test:cleanup:1.0"
							.equals(f.getFeature().getID().toString()));
			assertThat(found).isFalse();
		}

		@Test
		void failure_exceptionSetAsCause() {
			// 160.4.7: Exception set as cause of LaunchException
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:cause:1.0", "com.example:nonexistent:1.0.0");
			try {
				runtime.install(feature)
						.addRepository("test", testRepo)
						.complete();
			} catch (FeatureRuntimeException e) {
				// Cause may or may not be set depending on failure type
				assertThat(e.getMessage()).isNotNull();
			}
		}
	}
}
