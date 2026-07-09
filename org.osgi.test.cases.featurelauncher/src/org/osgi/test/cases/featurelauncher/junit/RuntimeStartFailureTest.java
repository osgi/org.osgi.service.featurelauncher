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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeException;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.5: a resolved bundle that fails to start.
 * <p>
 * The minimal tb1/tb2/tb3 bundles cannot fail to start, so this uses tb4, whose
 * activator throws from {@code start()}. This exercises the
 * {@code BundleException is thrown during Feature Start} failure scenario, which
 * the existing tests (using a non-existent bundle) only cover at install time.
 */
public class RuntimeStartFailureTest {

	@InjectService
	FeatureRuntime				runtime;

	@InjectService
	FeatureService				fs;

	@InjectService
	ArtifactRepositoryFactory	repoFactory;

	@InjectBundleContext
	BundleContext				ctx;

	private ArtifactRepository	testRepo;

	@BeforeEach
	void setUp() throws IOException {
		testRepo = TckTestHelper.createTestRepository(repoFactory);
	}

	@AfterEach
	void tearDown() {
		TckTestHelper.cleanupInstalledFeatures(runtime);
	}

	@Test
	void bundleFailsToStart_throwsFeatureRuntimeException() {
		Feature feature = TckTestHelper.createFeatureWithBundles(fs,
				"test:start-fail:1.0", TckTestHelper.TB4_COORDS);
		assertThatThrownBy(() -> runtime.install(feature)
				.addRepository("test", testRepo)
				.complete()).isInstanceOf(FeatureRuntimeException.class);
	}

	@Test
	void bundleFailsToStart_systemRolledBack() {
		Feature feature = TckTestHelper.createFeatureWithBundles(fs,
				"test:start-fail-rollback:1.0", TckTestHelper.TB4_COORDS);
		try {
			runtime.install(feature).addRepository("test", testRepo).complete();
		} catch (FeatureRuntimeException expected) {
			// 160.5: a start failure must roll the system back
		}

		boolean featurePresent = runtime.getInstalledFeatures()
				.stream()
				.anyMatch(f -> "test:start-fail-rollback:1.0"
						.equals(f.getFeature().getID().toString()));
		assertThat(featurePresent)
				.as("failed feature must not remain installed")
				.isFalse();

		Bundle leftOver = findBundle("org.osgi.test.cases.featurelauncher.tb4");
		assertThat(leftOver)
				.as("rolled-back tb4 bundle must not be left in the framework")
				.isNull();
	}

	private Bundle findBundle(String symbolicName) {
		for (Bundle b : ctx.getBundles()) {
			if (symbolicName.equals(b.getSymbolicName())) {
				return b;
			}
		}
		return null;
	}
}
