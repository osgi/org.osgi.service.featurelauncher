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

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.RuntimeMerges;
import org.osgi.test.assertj.bundle.BundleAssert;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.5.6: behaviour of a bundle merge strategy.
 * <p>
 * Unlike {@code RuntimeMergeStrategyTest}, which only checks the
 * {@link RuntimeMerges} factory methods return non-null, this drives a real
 * overlapping-version merge through the {@link FeatureRuntime} and asserts the
 * observable outcome in the framework.
 */
public class RuntimeBundleMergeBehaviourTest {

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
	void preferExistingBundles_keepsHigherInstalledVersion() {
		// Feature A installs tbm 1.1.0 (nothing to merge with yet).
		Feature featureA = TckTestHelper.createFeatureWithBundles(fs,
				"test:merge-a:1.0", TckTestHelper.TBM_V2_COORDS);
		runtime.install(featureA)
				.addRepository("test", testRepo)
				.withBundleMerge(RuntimeMerges.preferExistingBundles())
				.complete();

		// Feature B asks for tbm 1.0.0 (same major, lower minor). The strategy
		// must keep the already-installed 1.1.0 rather than downgrade.
		Feature featureB = TckTestHelper.createFeatureWithBundles(fs,
				"test:merge-b:1.0", TckTestHelper.TBM_V1_COORDS);
		runtime.install(featureB)
				.addRepository("test", testRepo)
				.withBundleMerge(RuntimeMerges.preferExistingBundles())
				.complete();

		long tbmCount = countBundles(TckTestHelper.TBM_SYMBOLIC_NAME);
		assertThat(tbmCount)
				.as("the merge must not produce a second tbm bundle")
				.isEqualTo(1L);

		Bundle tbm = findBundle(TckTestHelper.TBM_SYMBOLIC_NAME);
		assertThat(tbm)
				.as("the kept tbm bundle must still be installed")
				.isNotNull();
		assertThat(tbm.getVersion())
				.as("the higher installed version must be the one retained")
				.isEqualTo(Version.parseVersion(TckTestHelper.TBM_V2));
		BundleAssert.assertThat(tbm).isInState(Bundle.ACTIVE);

		// The retained bundle must be owned by BOTH features: prefer-existing
		// transfers ownership of the kept bundle to the merging feature
		// (160.5.6). This distinguishes a correct merge result from an empty
		// one, which would leave the bundle owned only by feature A and would
		// not be caught by the bundle-count assertions above.
		List<ID> owners = runtime.getInstalledFeatures()
				.stream()
				.flatMap(f -> f.getInstalledBundles().stream())
				.filter(ib -> ib.getBundle() != null && TckTestHelper.TBM_SYMBOLIC_NAME
						.equals(ib.getBundle().getSymbolicName()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("kept tbm bundle not found in installed features"))
				.getOwningFeatures();
		assertThat(owners)
				.as("the retained bundle must be owned by both merging features")
				.contains(featureA.getID(), featureB.getID());
	}

	private long countBundles(String symbolicName) {
		long count = 0;
		for (Bundle b : ctx.getBundles()) {
			if (symbolicName.equals(b.getSymbolicName())) {
				count++;
			}
		}
		return count;
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
