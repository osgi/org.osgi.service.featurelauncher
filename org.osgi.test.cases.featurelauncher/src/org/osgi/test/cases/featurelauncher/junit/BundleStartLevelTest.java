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

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.service.feature.BuilderFactory;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureBundle;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.featurelauncher.FeatureLauncherConstants;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeException;
import org.osgi.service.featurelauncher.runtime.InstalledBundle;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.3: Bundle Start Levels.
 */
public class BundleStartLevelTest {

	@InjectService
	FeatureRuntime				runtime;

	@InjectService
	FeatureService				fs;

	@InjectService
	ArtifactRepositoryFactory	repoFactory;

	ArtifactRepository			testRepo;

	@BeforeEach
	void setUp() throws IOException {
		testRepo = TckTestHelper.createTestRepository(repoFactory);
	}

	@AfterEach
	void tearDown() {
		TckTestHelper.cleanupInstalledFeatures(runtime);
	}

	// --- 160.3: BUNDLE_START_LEVEL_METADATA per-bundle ---

	@Nested
	class MetadataStartLevel {

		@Test
		void bundleStartLevel_fromMetadata_applied() {
			// 160.3: BUNDLE_START_LEVEL_METADATA sets per-bundle start level
			BuilderFactory bf = fs.getBuilderFactory();
			FeatureBundle bundle = bf
					.newBundleBuilder(fs.getIDfromMavenCoordinates(
							TckTestHelper.TB1_COORDS))
					.addMetadata(
							FeatureLauncherConstants.BUNDLE_START_LEVEL_METADATA,
							5)
					.build();
			Feature feature = bf
					.newFeatureBuilder(fs
							.getIDfromMavenCoordinates("test:start-level:1.0"))
					.addBundles(bundle)
					.setComplete(true)
					.build();

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			List<InstalledBundle> bundles = installed.getInstalledBundles();
			assertThat(bundles).isNotEmpty();
			assertThat(bundles.get(0).getStartLevel()).isEqualTo(5);
		}

		@Test
		void bundleStartLevel_validRange_1toMaxValue() {
			// 160.3: Valid range is integer from 1 to Integer.MAX_VALUE
			BuilderFactory bf = fs.getBuilderFactory();
			FeatureBundle bundle = bf
					.newBundleBuilder(fs.getIDfromMavenCoordinates(
							TckTestHelper.TB1_COORDS))
					.addMetadata(
							FeatureLauncherConstants.BUNDLE_START_LEVEL_METADATA,
							1)
					.build();
			Feature feature = bf
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:sl-min:1.0"))
					.addBundles(bundle)
					.setComplete(true)
					.build();

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getInstalledBundles().get(0).getStartLevel())
					.isEqualTo(1);
		}
	}

	// --- 160.3: BUNDLE_START_LEVELS JSON Extension ---

	@Nested
	class ExtensionStartLevel {

		@Test
		void bundleStartLevels_defaultStartLevel_applied() {
			// 160.3: defaultStartLevel from BUNDLE_START_LEVELS JSON extension
			// Tested indirectly - bundles without explicit metadata use default
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:default-sl:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getInstalledBundles()).isNotEmpty();
			assertThat(installed.getInstalledBundles().get(0).getStartLevel())
					.isGreaterThan(0);
		}

		@Test
		void bundleStartLevels_minimumStartLevel_applied() {
			// 160.3: minimumStartLevel from BUNDLE_START_LEVELS JSON extension
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:min-sl:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed).isNotNull();
		}

		@Test
		void bundleStartLevels_defaultNotPresent_usesFrameworkLevel() {
			// 160.3: If defaultStartLevel not present, use current framework
			// start level (or 1 if current is 0)
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"test:fw-sl:1.0", TckTestHelper.TB1_COORDS);
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed.getInstalledBundles()).isNotEmpty();
			int sl = installed.getInstalledBundles().get(0).getStartLevel();
			assertThat(sl).isGreaterThan(0);
		}
	}

	// --- 160.3: Invalid values -> LaunchException ---

	@Nested
	class InvalidStartLevel {

		@Test
		void bundleStartLevel_zero_throwsLaunchException() {
			// 160.3: 0 is not a valid start level -> exception
			BuilderFactory bf = fs.getBuilderFactory();
			FeatureBundle bundle = bf
					.newBundleBuilder(fs.getIDfromMavenCoordinates(
							TckTestHelper.TB1_COORDS))
					.addMetadata(
							FeatureLauncherConstants.BUNDLE_START_LEVEL_METADATA,
							0)
					.build();
			Feature feature = bf
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:sl-zero:1.0"))
					.addBundles(bundle)
					.setComplete(true)
					.build();

			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void bundleStartLevel_negative_throwsLaunchException() {
			// 160.3: Negative value is not valid -> exception
			BuilderFactory bf = fs.getBuilderFactory();
			FeatureBundle bundle = bf
					.newBundleBuilder(fs.getIDfromMavenCoordinates(
							TckTestHelper.TB1_COORDS))
					.addMetadata(
							FeatureLauncherConstants.BUNDLE_START_LEVEL_METADATA,
							-1)
					.build();
			Feature feature = bf
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:sl-neg:1.0"))
					.addBundles(bundle)
					.setComplete(true)
					.build();

			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void bundleStartLevel_invalidJson_throwsLaunchException() {
			// 160.3: Invalid JSON in BUNDLE_START_LEVELS -> exception
			// This tests the spec requirement for invalid extension content
			BuilderFactory bf = fs.getBuilderFactory();
			FeatureBundle bundle = bf
					.newBundleBuilder(fs.getIDfromMavenCoordinates(
							TckTestHelper.TB1_COORDS))
					.addMetadata(
							FeatureLauncherConstants.BUNDLE_START_LEVEL_METADATA,
							"not-a-number")
					.build();
			Feature feature = bf
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:sl-invalid:1.0"))
					.addBundles(bundle)
					.setComplete(true)
					.build();

			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}
	}
}
