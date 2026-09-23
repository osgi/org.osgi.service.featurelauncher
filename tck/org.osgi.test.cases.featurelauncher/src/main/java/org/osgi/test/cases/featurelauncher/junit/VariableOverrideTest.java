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
import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeException;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.3: Feature Variables Override.
 */
public class VariableOverrideTest {

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

	// --- 160.3: Variable Override at deployment ---

	@Nested
	class VariableOverrideApplied {

		@Test
		void variableWithDefault_overrideApplied() {
			// 160.3: Variable override replaces default value
			Feature feature = TckTestHelper.createFeatureWithVariables(fs,
					"test:var-override:1.0", mapOf("myVar", "defaultVal"));
			// Add a bundle so it can install
			feature = fs.getBuilderFactory()
					.newFeatureBuilder(feature.getID())
					.addBundles(fs.getBuilderFactory()
							.newBundleBuilder(fs.getIDfromMavenCoordinates(
									TckTestHelper.TB1_COORDS))
							.build())
					.addVariable("myVar", "defaultVal")
					.build();

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.withVariables(mapOf("myVar", "overrideVal"))
					.complete();
			assertThat(installed).isNotNull();
			// Variable override applied during installation
			Map<String,Object> vars = installed.getFeature().getVariables();
			assertThat(vars).containsEntry("myVar", "overrideVal");
		}

		@Test
		void variableWithDefault_noOverride_usesDefault() {
			// 160.3: Variable with default and no override uses default
			Feature feature = fs.getBuilderFactory()
					.newFeatureBuilder(fs
							.getIDfromMavenCoordinates("test:var-default:1.0"))
					.addBundles(fs.getBuilderFactory()
							.newBundleBuilder(fs.getIDfromMavenCoordinates(
									TckTestHelper.TB1_COORDS))
							.build())
					.addVariable("myVar", "defaultVal")
					.build();

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.complete();
			assertThat(installed).isNotNull();
			Map<String,Object> vars = installed.getFeature().getVariables();
			assertThat(vars).containsEntry("myVar", "defaultVal");
		}
	}

	// --- 160.3: Supported variable types ---

	@Nested
	class VariableTypes {

		@Test
		void variableType_string_accepted() {
			// 160.3: String variable type accepted
			Feature feature = fs.getBuilderFactory()
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:var-str:1.0"))
					.addBundles(fs.getBuilderFactory()
							.newBundleBuilder(fs.getIDfromMavenCoordinates(
									TckTestHelper.TB1_COORDS))
							.build())
					.addVariable("strVar", "hello")
					.build();
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.withVariables(mapOf("strVar", "world"))
					.complete();
			assertThat(installed.getFeature().getVariables())
					.containsEntry("strVar", "world");
		}

		@Test
		void variableType_boolean_accepted() {
			// 160.3: Boolean variable type accepted
			Feature feature = fs.getBuilderFactory()
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:var-bool:1.0"))
					.addBundles(fs.getBuilderFactory()
							.newBundleBuilder(fs.getIDfromMavenCoordinates(
									TckTestHelper.TB1_COORDS))
							.build())
					.addVariable("boolVar", Boolean.TRUE)
					.build();
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.withVariables(mapOf("boolVar", Boolean.FALSE))
					.complete();
			assertThat(installed.getFeature().getVariables())
					.containsEntry("boolVar", Boolean.FALSE);
		}

		@Test
		void variableType_bigDecimal_accepted() {
			// 160.3: BigDecimal variable type accepted for numbers
			Feature feature = fs.getBuilderFactory()
					.newFeatureBuilder(
							fs.getIDfromMavenCoordinates("test:var-num:1.0"))
					.addBundles(fs.getBuilderFactory()
							.newBundleBuilder(fs.getIDfromMavenCoordinates(
									TckTestHelper.TB1_COORDS))
							.build())
					.addVariable("numVar", BigDecimal.ONE)
					.build();
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", testRepo)
					.withVariables(mapOf("numVar", BigDecimal.TEN))
					.complete();
			assertThat(installed.getFeature().getVariables())
					.containsEntry("numVar", BigDecimal.TEN);
		}
	}

	// --- 160.3: Variables without default MUST be provided ---

	@Nested
	class MissingRequiredVariables {

		@Test
		void variableNoDefault_noOverride_throwsLaunchException() {
			// 160.3: Variables with no default value MUST be provided;
			// MUST throw exception if not provided
			Feature feature = fs.getBuilderFactory()
					.newFeatureBuilder(fs
							.getIDfromMavenCoordinates("test:var-missing:1.0"))
					.addBundles(fs.getBuilderFactory()
							.newBundleBuilder(fs.getIDfromMavenCoordinates(
									TckTestHelper.TB1_COORDS))
							.build())
					.addVariable("requiredVar", null)
					.build();

			assertThatThrownBy(() -> runtime.install(feature)
					.addRepository("test", testRepo)
					.complete()).isInstanceOf(FeatureRuntimeException.class);
		}
	}
}
